package com.example.partlookup.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.partlookup.data.PartDatabase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PartLookupViewModel = viewModel(
        factory = PartLookupViewModel.Factory(
            PartDatabase.getDatabase(LocalContext.current).partDao()
        )
    )
) {
    var showScanner by remember { mutableStateOf(false) }
    var manualPartNumber by remember { mutableStateOf("") }
    var uploadedFileName by remember { mutableStateOf<String?>(null) }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            // Get the actual file name using content resolver
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val fileName = cursor?.use {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                it.moveToFirst()
                it.getString(nameIndex)
            } ?: "Unknown file"
            uploadedFileName = fileName
            
            // Get content type from content resolver
            val mimeType = context.contentResolver.getType(uri)
            Log.d("FileUpload", "MIME type: $mimeType")
            
            Log.d("FileUpload", "File name: $fileName")
            
            // Get the file extension, handling potential null cases
            val fileExtension = if (fileName.contains(".")) {
                fileName.substringAfterLast('.').lowercase()
            } else {
                ""
            }
            
            Log.d("FileUpload", "Detected extension: $fileExtension")
            
            // Check both MIME type and file extension
            when {
                fileExtension == "csv" || mimeType == "text/csv" || mimeType == "text/comma-separated-values" -> {
                    Log.d("FileUpload", "Processing CSV file")
                    viewModel.importCsv(uri, context.contentResolver)
                }
                fileExtension == "xlsx" || mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> {
                    Log.d("FileUpload", "Processing XLSX file")
                    viewModel.importXlsx(uri, context.contentResolver)
                }
                else -> {
                    Log.d("FileUpload", "Unsupported file type: $fileExtension, MIME: $mimeType")
                    viewModel.searchPart("") // This will trigger the error state with proper message
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title with better styling
        Text(
            text = "Part Lookup",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Show Scanner at the top if active
        if (showScanner) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .padding(bottom = 16.dp)
            ) {
                BarcodeScanner(
                    onBarcodeDetected = { barcode ->
                        viewModel.searchPart(barcode)
                        showScanner = false
                    },
                    onError = { error ->
                        // Handle error
                        showScanner = false
                    }
                )
                
                // Scanner Frame Overlay
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val width = size.width
                    val height = size.height
                    val frameWidth = width * 0.7f
                    val frameHeight = height * 0.35f
                    val frameX = (width - frameWidth) / 2
                    val frameY = (height - frameHeight) / 2
                    
                    // Semi-transparent background
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(0f, 0f),
                        size = size
                    )
                    
                    // Clear scanning area
                    drawRect(
                        color = Color.Transparent,
                        topLeft = Offset(frameX, frameY),
                        size = androidx.compose.ui.geometry.Size(frameWidth, frameHeight)
                    )
                    
                    // Corner markers
                    val markerLength = frameHeight * 0.2f
                    val strokeWidth = 4f
                    
                    // Top-left corner
                    drawLine(
                        color = Color.White,
                        start = Offset(frameX, frameY),
                        end = Offset(frameX + markerLength, frameY),
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(frameX, frameY),
                        end = Offset(frameX, frameY + markerLength),
                        strokeWidth = strokeWidth
                    )
                    
                    // Top-right corner
                    drawLine(
                        color = Color.White,
                        start = Offset(frameX + frameWidth, frameY),
                        end = Offset(frameX + frameWidth - markerLength, frameY),
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(frameX + frameWidth, frameY),
                        end = Offset(frameX + frameWidth, frameY + markerLength),
                        strokeWidth = strokeWidth
                    )
                    
                    // Bottom-left corner
                    drawLine(
                        color = Color.White,
                        start = Offset(frameX, frameY + frameHeight),
                        end = Offset(frameX + markerLength, frameY + frameHeight),
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(frameX, frameY + frameHeight),
                        end = Offset(frameX, frameY + frameHeight - markerLength),
                        strokeWidth = strokeWidth
                    )
                    
                    // Bottom-right corner
                    drawLine(
                        color = Color.White,
                        start = Offset(frameX + frameWidth, frameY + frameHeight),
                        end = Offset(frameX + frameWidth - markerLength, frameY + frameHeight),
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(frameX + frameWidth, frameY + frameHeight),
                        end = Offset(frameX + frameWidth, frameY + frameHeight - markerLength),
                        strokeWidth = strokeWidth
                    )
                }
            }
        }

        // CSV Upload Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "Upload File"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload CSV/XLSX")
                }

                uploadedFileName?.let { fileName ->
                    Text(
                        text = "File: $fileName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Manual Search Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                OutlinedTextField(
                    value = manualPartNumber,
                    onValueChange = { 
                        manualPartNumber = it
                        if (showScanner) {
                            showScanner = false
                        }
                    },
                    label = { Text("Enter Part Number") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = { 
                        viewModel.searchPart(manualPartNumber)
                        if (showScanner) {
                            showScanner = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Search Part")
                }
            }
        }

        // Barcode Scanner Button
        Button(
            onClick = { showScanner = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Text("Scan Barcode")
        }

        // Show Results
        if (!showScanner) {
            when (uiState) {
                is PartLookupUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is PartLookupUiState.Success -> {
                    val part = (uiState as PartLookupUiState.Success).part
                    if (part != null) {
                        PartDetailsCard(part = part)
                    }
                }
                is PartLookupUiState.Error -> {
                    ErrorCard(message = (uiState as PartLookupUiState.Error).message)
                }
                else -> {}
            }
        }
    }
}

@Composable
fun PartDetailsCard(part: com.example.partlookup.data.Part) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Split the description into lines and display each line
            part.description.split("\n").forEach { line ->
                if (line.isNotEmpty()) {
                    if (line.startsWith("Part details")) {
                        // Title style
                        Text(
                            text = line,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        // Split label and value
                        val parts = line.split(":", limit = 2)
                        if (parts.size == 2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                // Label style
                                Text(
                                    text = "${parts[0]}:",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                // Value style - slightly different
                                Text(
                                    text = parts[1].trim(),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(2f)
                                )
                            }
                        } else {
                            // Fallback for any line that doesn't match the pattern
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(2f)
        )
    }
}

@Composable
fun ErrorCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
} 