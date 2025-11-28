package com.app.busiscoming.presentation.screens.walkingguide

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 도보 안내 시작 화면 ViewModel
 */
@HiltViewModel
class WalkingGuideViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(WalkingGuideUiState())
    val uiState: StateFlow<WalkingGuideUiState> = _uiState.asStateFlow()
    
    fun initialize(busNumber: String?) {
        _uiState.update { it.copy(busNumber = busNumber) }
    }
}

/**
 * 도보 안내 시작 화면 UI 상태
 */
data class WalkingGuideUiState(
    val busNumber: String? = null
)

