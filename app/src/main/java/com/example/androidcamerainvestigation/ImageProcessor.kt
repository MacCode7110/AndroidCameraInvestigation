package com.example.androidcamerainvestigation

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import androidx.core.graphics.set
import androidx.core.graphics.get

class ImageProcessor(private val boundingRectangle: BoundingRectangle, private val contourView: ContourView) {

    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()
    private val contourOptions = FaceDetectorOptions.Builder()
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()

    private val selfieOptions = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        .build()

    private val segmenter = Segmentation.getClient(selfieOptions)

    suspend fun process(bitmap: Bitmap, mode: CameraMode): ProcessingResult {
        return when(mode) {
            CameraMode.NONE -> ProcessingResult(bitmap = bitmap, faceCount = 0)
            CameraMode.FACE_DETECTION -> detectFaces(bitmap)
            CameraMode.CONTOUR_DETECTION -> detectContour(bitmap)
            CameraMode.MESH_DETECTION -> detectMesh(bitmap)
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

    fun detectMesh(bitmap: Bitmap): ProcessingResult {
        return ProcessingResult(bitmap = bitmap, faceCount = 0)
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
