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
                            if (confidence < 0.8f) {
                                mutableBitmap[x, y] = 0xFF00FF00.toInt()
                            }
                        }
                    }

                    cont.resume(ProcessingResult(mutableBitmap, 0))
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
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
