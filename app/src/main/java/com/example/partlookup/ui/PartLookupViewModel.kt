package com.example.partlookup.ui

import android.net.Uri
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

class PartLookupViewModel(private val partDao: PartDao) : ViewModel() {
    private val _uiState = MutableStateFlow<PartLookupUiState>(PartLookupUiState.Initial)
    val uiState: StateFlow<PartLookupUiState> = _uiState.asStateFlow()

    fun searchPart(partNumber: String) {
        viewModelScope.launch {
            _uiState.value = PartLookupUiState.Loading
            try {
                val part = partDao.getPartByNumber(partNumber)
                if (part != null) {
                    _uiState.value = PartLookupUiState.Success(part)
                } else {
                    _uiState.value = PartLookupUiState.Error("Part not found")
                }
            } catch (e: Exception) {
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