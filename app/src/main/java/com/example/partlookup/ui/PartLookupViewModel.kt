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
            _uiState.value = PartLookupUiState.Loading
            try {
                // Process the part number according to rules
                val processedPartNumber = processPartNumber(partNumber)
                
                // Log the processed part number for debugging
                Log.d("PartLookup", "Original part number: $partNumber, Processed: $processedPartNumber")
                
                val part = partDao.getPartByNumber(processedPartNumber)
                if (part != null) {
                    _uiState.value = PartLookupUiState.Success(part)
                } else {
                    // Log the failed lookup
                    Log.w("PartLookup", "Part not found. Processed number: $processedPartNumber")
                    _uiState.value = PartLookupUiState.Error(
                        "Part not found.\nNumber scanned: $processedPartNumber"
                    )
                }
            } catch (e: Exception) {
                // Log any errors that occur during lookup
                Log.e("PartLookup", "Error looking up part: ${e.message}", e)
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