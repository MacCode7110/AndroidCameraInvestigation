package com.example.androidcamerainvestigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
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
import kotlinx.coroutines.launch

@Composable
fun CameraView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mode by rememberSaveable{ mutableStateOf(CameraMode.NONE) }
    var numFaces by rememberSaveable{ mutableIntStateOf(0) }
    val processor = remember { ImageProcessor() }
    val scope = rememberCoroutineScope()

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases (
                CameraController.IMAGE_CAPTURE
            )

            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
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
                Image (
                    bitmap = capturedImage.value!!.asImageBitmap(),
                    contentDescription = "Captured Photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(2.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                CameraPreview(
                    controller = controller,
                    modifier = Modifier
                        .fillMaxSize()
                        .height(500.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if(capturedImage.value == null) {
                    takePhoto(
                        controller = controller,
                        onPhotoTaken = { bitmap ->
                            scope.launch {
                                val result = processor.process(bitmap, mode)
                                capturedImage.value = result.bitmap
                                numFaces = result.faceCount
                            }
                        },
                        context = context
                    )
                } else {
                    capturedImage.value = null
                }
            },
            shape = RoundedCornerShape(1.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Blue,
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
                Log.e("Camera", "couldn't take photo", exception)
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
                    onClick = { onModeSelect(mode) }
                )

                Text(
                    text = modeText
                )

                Spacer(modifier = Modifier.weight(1f))

                if(mode == CameraMode.FACE_DETECTION && selectedMode == mode) {
                    Text(
                        text = if (numFaces == 1) "$numFaces face detected" else "$numFaces faces detected",
                    )
                }
            }
        }
    }
}