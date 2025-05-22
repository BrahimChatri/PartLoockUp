package com.example.partlookup.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun BarcodeScanner(
    onBarcodeDetected: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isScanning by remember { mutableStateOf(false) }
    var lastScannedCode by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) {
                onError("Camera permission is required for barcode scanning")
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        } else {
            delay(2000)
            isScanning = true
            Log.d("BarcodeScanner", "Scanner started after delay")
        }
    }

    if (hasCameraPermission) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val executor = Executors.newSingleThreadExecutor()
                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        if (isScanning) {
                            processImageProxy(
                                imageProxy = imageProxy,
                                previewView = previewView,
                                onBarcodeDetected = { barcode ->
                                    if (barcode != lastScannedCode) {
                                        Log.d("BarcodeScanner", "New barcode detected: $barcode")
                                        lastScannedCode = barcode
                                        isScanning = false
                                        onBarcodeDetected(barcode)
                                    }
                                },
                                onError = onError
                            )
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        Log.d("BarcodeScanner", "Camera bound successfully")
                    } catch (e: Exception) {
                        Log.e("BarcodeScanner", "Use case binding failed", e)
                        onError("Failed to start camera: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )
    }
}

private fun processImageProxy(
    imageProxy: ImageProxy,
    previewView: PreviewView,
    onBarcodeDetected: (String) -> Unit,
    onError: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()

        // Calculate the center region of the preview
        val previewWidth = previewView.width
        val previewHeight = previewView.height
        val centerRegion = Rect(
            previewWidth / 4,
            previewHeight / 4,
            (previewWidth * 3) / 4,
            (previewHeight * 3) / 4
        )

        Log.d("BarcodeScanner", "Processing image: ${image.width}x${image.height}")
        Log.d("BarcodeScanner", "Center region: $centerRegion")

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                Log.d("BarcodeScanner", "Found ${barcodes.size} barcodes")
                barcodes.forEach { barcode ->
                    Log.d("BarcodeScanner", "Barcode format: ${barcode.format}, value: ${barcode.rawValue}")
                }
                
                barcodes.firstOrNull { barcode ->
                    barcode.rawValue != null && 
                    barcode.format == Barcode.FORMAT_ALL_FORMATS &&
                    isBarcodeInCenterRegion(barcode, centerRegion, image.width, image.height)
                }?.rawValue?.let { value ->
                    Log.d("BarcodeScanner", "Valid barcode detected in center region: $value")
                    onBarcodeDetected(value)
                }
            }
            .addOnFailureListener { e ->
                Log.e("BarcodeScanner", "Failed to scan barcode", e)
                onError("Failed to scan barcode: ${e.message}")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        Log.w("BarcodeScanner", "Media image is null")
        imageProxy.close()
    }
}

private fun isBarcodeInCenterRegion(
    barcode: Barcode,
    centerRegion: Rect,
    imageWidth: Int,
    imageHeight: Int
): Boolean {
    val boundingBox = barcode.boundingBox ?: return false
    
    // Convert barcode coordinates to preview coordinates
    val scaleX = centerRegion.width().toFloat() / imageWidth
    val scaleY = centerRegion.height().toFloat() / imageHeight
    
    val barcodeCenterX = boundingBox.centerX() * scaleX
    val barcodeCenterY = boundingBox.centerY() * scaleY
    
    val isInRegion = centerRegion.contains(barcodeCenterX.toInt(), barcodeCenterY.toInt())
    Log.d("BarcodeScanner", "Barcode center: ($barcodeCenterX, $barcodeCenterY), in region: $isInRegion")
    
    return isInRegion
} 