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

class ImageProcessor(private val boundingRectangle: BoundingRectangle, private val contourView: ContourView) {

    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()
    private val contourOptions = FaceDetectorOptions.Builder()
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()

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
