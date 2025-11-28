package com.app.busiscoming.presentation.screens.busrecognition

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 버스 인식 기능 화면 ViewModel
 */
@HiltViewModel
class BusRecognitionViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(BusRecognitionUiState())
    val uiState: StateFlow<BusRecognitionUiState> = _uiState.asStateFlow()
    
    fun initialize(busNumber: String?) {
        _uiState.update { it.copy(busNumber = busNumber) }
    }
    
    fun selectFindBusOnly() {
        // 버스만 찾기 기능 (UI만 구현)
        _uiState.update { it.copy(selectedFunction = BusFunction.FIND_BUS_ONLY) }
    }
    
    fun selectFindBusAndSeats() {
        // 버스 및 빈 좌석 찾기 기능 (UI만 구현)
        _uiState.update { it.copy(selectedFunction = BusFunction.FIND_BUS_AND_SEATS) }
    }
    
    fun cancelBoardingNotification() {
        // 승차 알림 취소 기능 (UI만 구현)
        _uiState.update { it.copy(selectedFunction = BusFunction.CANCEL_NOTIFICATION) }
    }
}

/**
 * 버스 인식 기능 화면 UI 상태
 */
data class BusRecognitionUiState(
    val busNumber: String? = null,
    val selectedFunction: BusFunction? = null
)

/**
 * 버스 인식 기능 타입
 */
enum class BusFunction {
    FIND_BUS_ONLY,
    FIND_BUS_AND_SEATS,
    CANCEL_NOTIFICATION
}

