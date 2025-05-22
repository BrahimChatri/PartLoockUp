package com.example.partlookup.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.partlookup.data.Part
import com.example.partlookup.data.PartDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.Row

/**
 * Processes a part number according to the specified rules:
 * - If starts with 'PP', remove first 'P'
 * - If starts with 'P' and second char is '4', remove 'P'
 * - If starts with 'P' and second char is '0', keep as is
 * - Otherwise, return original value
 */
private fun processPartNumber(value: String): String {
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

class PartLookupViewModel(private val partDao: PartDao) : ViewModel() {
    private val _uiState = MutableStateFlow<PartLookupUiState>(PartLookupUiState.Initial)
    val uiState: StateFlow<PartLookupUiState> = _uiState.asStateFlow()

    fun searchPart(partNumber: String) {
        viewModelScope.launch {
            Log.e("DEBUG_PART", "Starting search for part: $partNumber")
            _uiState.value = PartLookupUiState.Loading
            try {
                // Handle empty string case (used for unsupported file types)
                if (partNumber.isEmpty()) {
                    Log.e("DEBUG_PART", "Empty part number received")
                    _uiState.value = PartLookupUiState.Error("Unsupported file type. Please upload a CSV or XLSX file.")
                    return@launch
                }

                // Process the part number according to rules
                val processedPartNumber = processPartNumber(partNumber)
                Log.e("DEBUG_PART", "Processed part number: $processedPartNumber")
                
                // Try to find the part with the processed number
                var part = partDao.getPartByNumber(processedPartNumber)
                Log.e("DEBUG_PART", "First search result for $processedPartNumber: ${part?.partNumber}")
                
                // If not found and the number starts with 4, try with the original number
                if (part == null && processedPartNumber.startsWith("4")) {
                    Log.e("DEBUG_PART", "Trying original number: $partNumber")
                    part = partDao.getPartByNumber(partNumber)
                    Log.e("DEBUG_PART", "Second search result for $partNumber: ${part?.partNumber}")
                }
                
                if (part != null) {
                    Log.e("DEBUG_PART", "Found part: ${part.partNumber}, New Reference: ${part.newReference}, Location: ${part.location}")
                    
                    // Format response based on part number prefix
                    val response = if (processedPartNumber.startsWith("4")) {
                        // For parts starting with 4, include new reference
                        "Part details\n" +
                        "scanned part number: $processedPartNumber\n" +
                        "New reference: ${part.newReference ?: "N/A"}\n" +
                        "EMP location: ${part.location}"
                    } else {
                        // For other parts (starting with P), show basic info
                        "Part details\n" +
                        "scanned part number: $processedPartNumber\n" +
                        "EMP location: ${part.location}"
                    }
                    
                    _uiState.value = PartLookupUiState.Success(part.copy(description = response))
                } else {
                    Log.e("DEBUG_PART", "Part not found. Original: $partNumber, Processed: $processedPartNumber")
                    _uiState.value = PartLookupUiState.Error(
                        "Part not found.\nNumber scanned: $processedPartNumber"
                    )
                }
            } catch (e: Exception) {
                Log.e("DEBUG_PART", "Error in searchPart: ${e.message}", e)
                _uiState.value = PartLookupUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun importCsv(uri: Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            _uiState.value = PartLookupUiState.Loading
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val parts = mutableListOf<Part>()
                    
                    // Read header to verify format
                    val header = reader.readLine()
                    if (!header.equals("PartNumber,EMP_Location", ignoreCase = true)) {
                        _uiState.value = PartLookupUiState.Error("Invalid CSV format. Expected header: PartNumber,EMP_Location")
                        return@launch
                    }
                    
                    reader.forEachLine { line ->
                        val values = line.split(",")
                        if (values.size >= 2) {
                            parts.add(
                                Part(
                                    partNumber = values[0].trim(),
                                    location = values[1].trim(),
                                    description = "", // Not used in your format
                                    quantity = 0 // Not used in your format
                                )
                            )
                        }
                    }
                    
                    partDao.deleteAllParts()
                    partDao.insertParts(parts)
                    _uiState.value = PartLookupUiState.Success(null)
                }
            } catch (e: Exception) {
                Log.e("PartLookup", "Failed to import CSV: ${e.message}", e)
                _uiState.value = PartLookupUiState.Error("Failed to import CSV: ${e.message}")
            }
        }
    }

    fun importXlsx(uri: Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            Log.e("DEBUG_XLSX", "Starting XLSX import")
            _uiState.value = PartLookupUiState.Loading
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val workbook = XSSFWorkbook(inputStream)
                    val sheet = workbook.getSheetAt(0)
                    val parts = mutableListOf<Part>()
                    
                    Log.e("DEBUG_XLSX", "Total rows in sheet: ${sheet.lastRowNum}")
                    
                    // Skip header row
                    for (rowIndex in 1..sheet.lastRowNum) {
                        val row: Row? = sheet.getRow(rowIndex)
                        if (row != null) {
                            // Helper function to safely get cell value as string
                            fun getCellValueAsString(cell: org.apache.poi.ss.usermodel.Cell?): String? {
                                if (cell == null) return null
                                return when (cell.cellType) {
                                    org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
                                    org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                                        // Use DecimalFormat to avoid scientific notation
                                        val formatter = java.text.DecimalFormat("0")
                                        formatter.isGroupingUsed = false
                                        formatter.format(cell.numericCellValue)
                                    }
                                    else -> null
                                }
                            }

                            // Get part number (required)
                            val partNumber = getCellValueAsString(row.getCell(0))?.trim()
                            if (partNumber.isNullOrEmpty()) {
                                Log.e("DEBUG_XLSX", "Row $rowIndex - Skipping: Empty part number")
                                continue
                            }

                            // Get harmonizer (optional)
                            val harmonizer = getCellValueAsString(row.getCell(1))?.trim()
                            
                            // Get location (required)
                            val location = getCellValueAsString(row.getCell(2))?.trim()
                            if (location.isNullOrEmpty()) {
                                Log.e("DEBUG_XLSX", "Row $rowIndex - Skipping: Empty location")
                                continue
                            }
                            
                            Log.e("DEBUG_XLSX", "Row $rowIndex - Part: $partNumber, Harmonizer: $harmonizer, Location: $location")
                            
                            parts.add(
                                Part(
                                    partNumber = partNumber,
                                    location = location,
                                    description = "", // Not used in your format
                                    quantity = 0, // Not used in your format
                                    newReference = harmonizer // Store harmonized reference if present
                                )
                            )
                        }
                    }
                    
                    Log.e("DEBUG_XLSX", "Total parts to import: ${parts.size}")
                    partDao.deleteAllParts()
                    partDao.insertParts(parts)
                    Log.e("DEBUG_XLSX", "Import completed successfully")
                    _uiState.value = PartLookupUiState.Success(null)
                }
            } catch (e: Exception) {
                Log.e("DEBUG_XLSX", "Failed to import XLSX: ${e.message}", e)
                _uiState.value = PartLookupUiState.Error("Failed to import XLSX: ${e.message}")
            }
        }
    }

    class Factory(private val partDao: PartDao) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PartLookupViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PartLookupViewModel(partDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

sealed class PartLookupUiState {
    object Initial : PartLookupUiState()
    object Loading : PartLookupUiState()
    data class Success(val part: Part?) : PartLookupUiState()
    data class Error(val message: String) : PartLookupUiState()
} 