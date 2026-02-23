package com.example.androidcamerainvestigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import androidx.core.graphics.set
import androidx.core.graphics.get

class ImageProcessor(
    private val boundingRectangle: BoundingRectangle,
    private val contourView: ContourView,
    private val meshView: MeshView
) {

    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()
    private val contourOptions = FaceDetectorOptions.Builder()
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()
    private val meshOptions = FaceMeshDetectorOptions.Builder()
        .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
        .build()

    private val selfieOptions = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        .build()

    private val segmenter by lazy {
        Segmentation.getClient(selfieOptions)
    }

    private val meshDetector: com.google.mlkit.vision.facemesh.FaceMeshDetector? by lazy {
        try {
            Log.d("ImageProcessor", "Attempting to create face mesh detector...")
            val detector = FaceMeshDetection.getClient(meshOptions)
            Log.d("ImageProcessor", "Face mesh detector created successfully")
            detector
        } catch (e: Exception) {
            Log.e("ImageProcessor", "Failed to create face mesh detector", e)
            null
        } catch (e: Error) {
            Log.e("ImageProcessor", "Fatal error creating face mesh detector", e)
            null
        }
    }

    suspend fun process(bitmap: Bitmap, mode: CameraMode, drawOnBitmap: Boolean = false): ProcessingResult {
        return when(mode) {
            CameraMode.NONE -> ProcessingResult(bitmap = bitmap, faceCount = 0)
            CameraMode.FACE_DETECTION -> detectFaces(bitmap)
            CameraMode.CONTOUR_DETECTION -> detectContour(bitmap)
            CameraMode.MESH_DETECTION -> detectMesh(bitmap, drawOnBitmap)
            CameraMode.SELFIE_SEGMENTATION -> segmentSelfie(bitmap)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    suspend fun detectFaces(bitmap: Bitmap): ProcessingResult =
        suspendCancellableCoroutine { cont ->

            val image = InputImage.fromBitmap(bitmap, 0)
            val detector = FaceDetection.getClient(faceDetectorOptions)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    val mutableBitmap =
                        bitmap.copy(Bitmap.Config.ARGB_8888, true)

                    faces.forEach { face ->
                        val faceBounds = face.boundingBox
                        val height = faceBounds.height()
                        val offset = (height * 0.1f).toInt()
                        faceBounds.offset(0, offset)

                        boundingRectangle.setBoundingRect(faceBounds, image.width, image.height)

                    }

                    cont.resume(
                        ProcessingResult(
                            bitmap = mutableBitmap,
                            faceCount = faces.size
                        )
                    )
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }

        }

    suspend fun detectContour(bitmap: Bitmap): ProcessingResult =
        suspendCancellableCoroutine { cont ->

            val image = InputImage.fromBitmap(bitmap, 0)
            val detector = FaceDetection.getClient(contourOptions)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    val mutableBitmap =
                        bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    contourView.setContours(faces, image.width, image.height)

                    cont.resume(
                        ProcessingResult(
                            bitmap = mutableBitmap,
                            faceCount = faces.size
                        )
                    )
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
            }

    suspend fun detectMesh(bitmap: Bitmap, drawOnBitmap: Boolean = false): ProcessingResult =
        suspendCancellableCoroutine { cont ->

            val detector = meshDetector

            if (detector == null) {
                Log.e("ImageProcessor", "Face mesh detector is not available")
                cont.resume(
                    ProcessingResult(
                        bitmap = bitmap,
                        faceCount = 0
                    )
                )
                return@suspendCancellableCoroutine
            }

            val processedBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }

            val scaledBitmap = if (processedBitmap.width > 600 || processedBitmap.height > 800) {
                val scale = if (processedBitmap.width > processedBitmap.height) {
                    480f / processedBitmap.width
                } else {
                    640f / processedBitmap.height
                }
                val newWidth = (processedBitmap.width * scale).toInt()
                val newHeight = (processedBitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(processedBitmap, newWidth, newHeight, true)
            } else {
                processedBitmap
            }

            val image = InputImage.fromBitmap(scaledBitmap, 0)


            detector.process(image)
                .addOnSuccessListener { meshes ->
                    val scaleFactorForDraw = processedBitmap.width.toFloat() / scaledBitmap.width.toFloat()

                    val displayBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

                    meshView.setMeshes(meshes, image.width, image.height)

                    if (drawOnBitmap && meshes.isNotEmpty()) {
                        val canvas = Canvas(displayBitmap)

                        val pointPaint = Paint().apply {
                            color = Color.CYAN
                            style = Paint.Style.FILL
                            strokeWidth = 4f
                        }

                        val linePaint = Paint().apply {
                            color = Color.CYAN
                            style = Paint.Style.STROKE
                            strokeWidth = 2f
                            alpha = 180
                        }

                        for (mesh in meshes) {
                            mesh.allTriangles.forEach { triangle ->
                                val points = triangle.allPoints
                                if (points.size == 3) {
                                    val p1 = points[0].position
                                    val p2 = points[1].position
                                    val p3 = points[2].position

                                    canvas.drawLine(p1.x * scaleFactorForDraw, p1.y * scaleFactorForDraw,
                                                   p2.x * scaleFactorForDraw, p2.y * scaleFactorForDraw, linePaint)
                                    canvas.drawLine(p2.x * scaleFactorForDraw, p2.y * scaleFactorForDraw,
                                                   p3.x * scaleFactorForDraw, p3.y * scaleFactorForDraw, linePaint)
                                    canvas.drawLine(p3.x * scaleFactorForDraw, p3.y * scaleFactorForDraw,
                                                   p1.x * scaleFactorForDraw, p1.y * scaleFactorForDraw, linePaint)
                                }
                            }

                            mesh.allPoints.forEach { point ->
                                val pos = point.position
                                canvas.drawCircle(pos.x * scaleFactorForDraw, pos.y * scaleFactorForDraw, 3f, pointPaint)
                            }
                        }
                    }

                    cont.resume(
                        ProcessingResult(
                            bitmap = displayBitmap,
                            faceCount = meshes.size
                        )
                    )
                }
                .addOnFailureListener { e ->
                    Log.e("ImageProcessor", "Face mesh detection failed", e)
                    cont.resume(
                        ProcessingResult(
                            bitmap = bitmap,
                            faceCount = 0
                        )
                    )
                }
        }

    suspend fun segmentSelfie(bitmap: Bitmap): ProcessingResult =
        suspendCancellableCoroutine { cont ->

            val image = InputImage.fromBitmap(bitmap, 0)

            segmenter.process(image)
                .addOnSuccessListener { mask ->

                    val width = mask.width
                    val height = mask.height
                    val maskBuffer = mask.buffer
                    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

                    maskBuffer.rewind()

                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            val confidence = maskBuffer.float
                            if (confidence > 0.5f) {
                                val originalPixel = mutableBitmap[x, y]

                                val highlightColor = 0x55E015E3
                                val blended = blendColors(originalPixel, highlightColor)

                                mutableBitmap[x, y] = blended
                            }
                        }
                    }

                    cont.resume(ProcessingResult(mutableBitmap, 0))
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }
    private fun blendColors(baseColor: Int, overlayColor: Int): Int {
        val alpha = ((overlayColor shr 24) and 0xFF) / 255f

        val r1 = (baseColor shr 16) and 0xFF
        val g1 = (baseColor shr 8) and 0xFF
        val b1 = baseColor and 0xFF

        val r2 = (overlayColor shr 16) and 0xFF
        val g2 = (overlayColor shr 8) and 0xFF
        val b2 = overlayColor and 0xFF

        val r = (r1 * (1 - alpha) + r2 * alpha).toInt()
        val g = (g1 * (1 - alpha) + g2 * alpha).toInt()
        val b = (b1 * (1 - alpha) + b2 * alpha).toInt()

        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

data class ProcessingResult(
    val bitmap: Bitmap,
    val faceCount: Int = 0
)

enum class CameraMode {
    NONE,
    FACE_DETECTION,
    CONTOUR_DETECTION,
    MESH_DETECTION,
    SELFIE_SEGMENTATION
}
