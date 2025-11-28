package com.app.busiscoming.presentation.screens.busrecognitionresult

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 버스 인식 결과 화면 ViewModel
 */
@HiltViewModel
class BusRecognitionResultViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(BusRecognitionResultUiState())
    val uiState: StateFlow<BusRecognitionResultUiState> = _uiState.asStateFlow()
    
    fun initialize(busNumber: String?) {
        _uiState.update { it.copy(busNumber = busNumber) }
    }
}

/**
 * 버스 인식 결과 화면 UI 상태
 */
data class BusRecognitionResultUiState(
    val busNumber: String? = null
)



