package com.app.busiscoming.presentation.screens.busrecognition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.busiscoming.data.datastore.AppPreferences
import com.app.busiscoming.data.remote.BusApiServiceFactory
import com.app.busiscoming.data.remote.dto.RideCancelRequestDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 버스 인식 기능 화면 ViewModel
 */
@HiltViewModel
class BusRecognitionViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val busApiServiceFactory: BusApiServiceFactory
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(BusRecognitionUiState())
    val uiState: StateFlow<BusRecognitionUiState> = _uiState.asStateFlow()
    
    fun initialize(busNumber: String?) {
        _uiState.update { it.copy(busNumber = busNumber) }
    }
    
    fun selectFindBusOnly() {
        _uiState.update { it.copy(selectedFunction = BusFunction.FIND_BUS_ONLY) }
    }
    
    fun selectFindBusAndSeats() {
        _uiState.update { it.copy(selectedFunction = BusFunction.FIND_BUS_AND_SEATS) }
    }
    
    suspend fun cancelBoardingNotification(): Result<Unit> {
        return try {
            val requestId = appPreferences.getRequestId()
            if (requestId == null) {
                return Result.failure(Exception("requestId가 없습니다"))
            }
            
            val deviceId = appPreferences.getDeviceId()
            val serverIp = appPreferences.serverIp.first()
            val port = appPreferences.serverPort.first()
            
            if (serverIp.isEmpty() || port.isEmpty()) {
                return Result.failure(Exception("서버 IP와 포트를 설정해주세요"))
            }
            
            val baseUrl = "http://$serverIp:$port"
            val apiService = busApiServiceFactory.create(baseUrl)
            
            val request = RideCancelRequestDto(
                deviceId = deviceId,
                requestId = requestId,
                reason = "user_cancel"
            )
            
            val response = apiService.cancelRide(request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                // requestId 삭제
                appPreferences.setRequestId(null)
                _uiState.update { it.copy(selectedFunction = BusFunction.CANCEL_NOTIFICATION) }
                Result.success(Unit)
            } else {
                Result.failure(Exception("승차 취소 실패"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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

