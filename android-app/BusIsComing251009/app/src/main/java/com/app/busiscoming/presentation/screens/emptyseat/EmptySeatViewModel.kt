package com.app.busiscoming.presentation.screens.emptyseat

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 빈 좌석 찾기 화면 ViewModel
 */
@HiltViewModel
class EmptySeatViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(EmptySeatUiState())
    val uiState: StateFlow<EmptySeatUiState> = _uiState.asStateFlow()
    
    fun initialize(busNumber: String?) {
        _uiState.update { it.copy(busNumber = busNumber) }
    }
}

/**
 * 빈 좌석 찾기 화면 UI 상태
 */
data class EmptySeatUiState(
    val busNumber: String? = null
)



