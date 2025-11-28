package com.app.busiscoming.presentation.screens.busstoparrival

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 정류장 도착 알림 화면 ViewModel
 */
@HiltViewModel
class BusStopArrivalViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(BusStopArrivalUiState())
    val uiState: StateFlow<BusStopArrivalUiState> = _uiState.asStateFlow()
    
    fun initialize(busNumber: String?) {
        _uiState.update { it.copy(busNumber = busNumber) }
    }
    
    fun sendBoardingNotification() {
        // 실제 서버 전송 로직은 없고 UI만 구현
        _uiState.update { it.copy(notificationSent = true) }
    }
}

/**
 * 정류장 도착 알림 화면 UI 상태
 */
data class BusStopArrivalUiState(
    val busNumber: String? = null,
    val notificationSent: Boolean = false
)

