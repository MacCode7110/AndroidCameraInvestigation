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


    private fun getMeshDetector(): com.google.mlkit.vision.facemesh.FaceMeshDetector? {
        return try {
            FaceMeshDetection.getClient(meshOptions)
        } catch (e: Exception) {
            Log.e("ImageProcessor", "Failed to create face mesh detector", e)
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

            val detector = getMeshDetector()

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

    fun segmentSelfie(bitmap: Bitmap): ProcessingResult {
        return ProcessingResult(bitmap = bitmap, faceCount = 0)
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
