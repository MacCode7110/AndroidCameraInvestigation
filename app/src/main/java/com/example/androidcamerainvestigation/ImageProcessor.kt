package com.example.androidcamerainvestigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ImageProcessor {
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

    fun detectFaces(bitmap: Bitmap): ProcessingResult {
        return ProcessingResult(bitmap = bitmap, faceCount = 0)
    }

    suspend fun detectContour(bitmap: Bitmap): ProcessingResult =
        suspendCancellableCoroutine { cont ->

            val image = InputImage.fromBitmap(bitmap, 0)
            val detector = FaceDetection.getClient(contourOptions)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    val mutableBitmap =
                        bitmap.copy(Bitmap.Config.ARGB_8888, true)

                    val canvas = Canvas(mutableBitmap)

                    val paint = Paint().apply {
                        color = Color.GREEN
                        style = Paint.Style.FILL
                        strokeWidth = 4f
                    }

                    faces.forEach { face ->
                        face.allContours.forEach { contour ->
                            contour.points.forEach { point ->
                                canvas.drawCircle(
                                    point.x,
                                    point.y,
                                    3f,
                                    paint
                                )
                            }
                        }
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