package com.example.partlookup.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.ToneGenerator
import android.media.AudioManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Processes a scanned barcode value according to the specified rules:
 * - If starts with 'PP', remove first 'P'
 * - If starts with 'P' and second char is '4', remove 'P'
 * - If starts with 'P' and second char is '0', keep as is
 * - Otherwise, return original value
 */
private fun processScannedValue(value: String): String {
    return when {
        // Handle PP prefix
        value.startsWith("PP") -> value.substring(1)
        
        // Handle P prefix
        value.startsWith("P") && value.length > 1 -> {
            when (value[1]) {
                '4' -> value.substring(1) // Remove P for P4 prefix
                '0' -> value // Keep as is for P0 prefix
                else -> value // Keep as is for other P prefixes
            }
        }
        
        // Return original value for all other cases
        else -> value
    }
}

@Composable
fun BarcodeScanner(
    onBarcodeDetected: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isScanning by remember { mutableStateOf(false) }
    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }
    var lastScanTime by remember { mutableStateOf(0L) }
    var isInitialDelay by remember { mutableStateOf(true) }
    var isFlashOn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) {
                onError("Camera permission is required")
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
        delay(2000)
        isInitialDelay = false
    }

    if (!hasCameraPermission) {
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { previewView ->
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(
                Executors.newSingleThreadExecutor()
            ) { imageProxy ->
                if (!isInitialDelay && !isScanning) {
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )

                        val scanner = BarcodeScanning.getClient()
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                if (barcodes.isNotEmpty() && !isScanning) {
                                    val barcode = barcodes[0]
                                    val rawValue = barcode.rawValue
                                    
                                    if (rawValue != null && rawValue != lastScannedBarcode) {
                                        // Check if barcode is within the frame
                                        val frameWidth = previewView.width * 0.7f
                                        val frameHeight = previewView.height * 0.35f
                                        val frameX = (previewView.width - frameWidth) / 2
                                        val frameY = (previewView.height - frameHeight) / 2
                                        
                                        val barcodeBox = barcode.boundingBox
                                        if (barcodeBox != null) {
                                            // Convert barcode coordinates to preview coordinates
                                            val scaleX = previewView.width.toFloat() / image.width
                                            val scaleY = previewView.height.toFloat() / image.height
                                            
                                            val scaledLeft = barcodeBox.left * scaleX
                                            val scaledTop = barcodeBox.top * scaleY
                                            val scaledRight = barcodeBox.right * scaleX
                                            val scaledBottom = barcodeBox.bottom * scaleY
                                            
                                            // Check if barcode is within the frame boundaries
                                            if (scaledLeft >= frameX &&
                                                scaledRight <= frameX + frameWidth &&
                                                scaledTop >= frameY &&
                                                scaledBottom <= frameY + frameHeight
                                            ) {
                                                isScanning = true
                                                lastScannedBarcode = rawValue
                                                lastScanTime = System.currentTimeMillis()
                                                
                                                // Process the scanned value according to the rules
                                                val processedValue = processScannedValue(rawValue)
                                                
                                                // Play a louder beep sound
                                                val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                                                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                                                
                                                // Return the processed value
                                                onBarcodeDetected(processedValue)
                                            }
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("BarcodeScanner", "Barcode scanning failed", e)
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                } else {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                
                // Add flash control
                camera.cameraControl.enableTorch(isFlashOn)
                
                preview.setSurfaceProvider(previewView.surfaceProvider)
            } catch (e: Exception) {
                Log.e("BarcodeScanner", "Camera binding failed", e)
                onError("Failed to start camera: ${e.message}")
            }
        }

        // Top bar with flash and exit buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Flash toggle button (moved to left)
            IconButton(
                onClick = { isFlashOn = !isFlashOn },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.medium
                    )
            ) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = if (isFlashOn) "Turn off flash" else "Turn on flash",
                    tint = if (isFlashOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            // Exit button (added to right)
            IconButton(
                onClick = { onError("Camera closed") },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.medium
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close camera",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (isInitialDelay) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
        }
    }
} 