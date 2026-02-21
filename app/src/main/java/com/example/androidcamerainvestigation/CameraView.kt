package com.example.androidcamerainvestigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.androidcamerainvestigation.ui.theme.Purple40
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CameraView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mode by rememberSaveable{ mutableStateOf(CameraMode.NONE) }
    var numFaces by rememberSaveable{ mutableIntStateOf(0) }
    val boundingRectangle = remember { BoundingRectangle(context, null) }
    val contourView = remember { ContourView(context, null) }
    val processor = remember { ImageProcessor(boundingRectangle, contourView) }
    val scope = rememberCoroutineScope()
    val isProcessing = remember { AtomicBoolean(false) }
    var isAnalysisEnabled by remember { mutableStateOf(true) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(
                CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS
            )

            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                ImageAnalysis.Analyzer { imageProxy ->
                    if (!isAnalysisEnabled) {
                        imageProxy.close()
                        return@Analyzer
                    }
                    if (mode == CameraMode.FACE_DETECTION) {
                        contourView.clear()
                        if (isProcessing.compareAndSet(false, true)) {
                            scope.launch {
                                try {
                                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                    val bitmap = imageProxy.toBitmap()
                                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    processor.process(rotatedBitmap, mode)
                                } finally {
                                    imageProxy.close()
                                    isProcessing.set(false)
                                }
                            }
                        } else {
                            imageProxy.close()
                        }
                    } else if (mode == CameraMode.CONTOUR_DETECTION) {
                        boundingRectangle.clear()
                        if (isProcessing.compareAndSet(false, true)) {
                            scope.launch {
                                try {
                                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                    val bitmap = imageProxy.toBitmap()
                                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    processor.process(rotatedBitmap, mode)
                                } finally {
                                    imageProxy.close()
                                    isProcessing.set(false)
                                }
                            }
                        } else {
                            imageProxy.close()
                        }
                    } else {
                        boundingRectangle.clear()
                        contourView.clear()
                        imageProxy.close()
                    }
                }
            )
        }
    }

    val capturedImage = remember {
        mutableStateOf<Bitmap?>(null)
    }

    Column(
        modifier = modifier.padding(PaddingValues()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp),
            contentAlignment = Alignment.Center
        ) {
            if (capturedImage.value != null) {
                Image(
                    bitmap = capturedImage.value!!.asImageBitmap(),
                    contentDescription = "Captured Photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(2.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                isAnalysisEnabled = true
                CameraPreview(
                    controller = controller,
                    modifier = Modifier
                        .fillMaxSize()
                        .height(500.dp)
                )
            }
            AndroidView({ boundingRectangle })
            AndroidView({ contourView })
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (capturedImage.value == null) {
                    scope.launch {
                        isAnalysisEnabled = false
                        withContext(Dispatchers.IO) {
                            while (isProcessing.get()) {
                                delay(10)
                            }
                        }
                        boundingRectangle.clear()
                        contourView.clear()
                        numFaces = 0
                        takePhoto(
                            controller = controller,
                            onPhotoTaken = { bitmap ->
                                scope.launch {
                                    val result = processor.process(bitmap, mode)
                                    capturedImage.value = result.bitmap
                                    numFaces = result.faceCount
                                }
                            },
                            onPhotoCaptureFailed = { isProcessing.set(false) },
                            context = context
                        )
                    }
                } else {
                    capturedImage.value = null
                    boundingRectangle.clear()
                    contourView.clear()
                    numFaces = 0
                }
            },
            shape = RoundedCornerShape(1.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Purple40,
                contentColor = Color.White
            )
        ) {
            Text("Take a picture", fontSize = 16.sp)
        }



        Spacer(modifier = Modifier.height(10.dp))

        ModeSelection(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 30.dp, bottom = 50.dp),
            selectedMode = mode,
            onModeSelect = { modeSelected ->
                mode = modeSelected
            },
            numFaces = numFaces
        )
    }
}

private fun takePhoto(
    controller: LifecycleCameraController,
    onPhotoTaken: (Bitmap) -> Unit,
    onPhotoCaptureFailed: () -> Unit,
    context: Context
) {
    controller.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                super.onCaptureSuccess(image)

                val rotationDegrees = image.imageInfo.rotationDegrees

                val originalBitmap = image.toBitmap()

                val matrix = Matrix().apply {
                    postRotate(rotationDegrees.toFloat())
                }

                val rotatedBitmap = Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true
                )

                image.close()
                onPhotoTaken(rotatedBitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                Log.e("Camera", "couldn\'t take photo", exception)
                onPhotoCaptureFailed()
            }

        }
    )
}

@Composable
fun CameraPreview(
    controller: LifecycleCameraController,
    modifier: Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = {
            PreviewView(it).apply {
                this.controller = controller
                controller.bindToLifecycle(lifecycleOwner)
            }
        },
        modifier = modifier
    )
}

@Composable
fun ModeSelection(
    modifier: Modifier = Modifier,
    selectedMode: CameraMode,
    onModeSelect: (CameraMode) -> Unit,
    numFaces: Int
) {
    Column(
        modifier = modifier
    ) {
        for (mode in CameraMode.entries) {
            val modeText = when(mode) {
                CameraMode.NONE -> "None"
                CameraMode.FACE_DETECTION -> "Face Detection"
                CameraMode.CONTOUR_DETECTION -> "Contour Detection"
                CameraMode.MESH_DETECTION -> "Mesh Detection"
                CameraMode.SELFIE_SEGMENTATION -> "Selfie Segmentation"
            }

            Row (
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onModeSelect(mode)
                    }
            ) {
                RadioButton(
                    selected = mode == selectedMode,
                    onClick = { onModeSelect(mode) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color.Black,
                        unselectedColor = Color.LightGray
                    )
                )

                Text(
                    text = modeText
                )

                Spacer(modifier = Modifier.weight(1f))

                if(mode == CameraMode.FACE_DETECTION && selectedMode == mode) {
                    Text(
                        text = "$numFaces face detected"
                    )
                }
            }
        }
    }
}
