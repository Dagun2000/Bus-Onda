package com.app.busiscoming.presentation.screens.disembarkationnotification

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 하차 알림 화면 ViewModel
 */
@HiltViewModel
class DisembarkationNotificationViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(DisembarkationNotificationUiState())
    val uiState: StateFlow<DisembarkationNotificationUiState> = _uiState.asStateFlow()
    
    fun initialize(busNumber: String?) {
        _uiState.update { it.copy(busNumber = busNumber) }
    }
}

/**
 * 하차 알림 화면 UI 상태
 */
data class DisembarkationNotificationUiState(
    val busNumber: String? = null
)



