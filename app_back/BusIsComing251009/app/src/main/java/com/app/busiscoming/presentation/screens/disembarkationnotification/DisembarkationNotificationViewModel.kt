package com.app.busiscoming.presentation.screens.disembarkationnotification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.busiscoming.data.datastore.AppPreferences
import com.app.busiscoming.data.remote.BusApiServiceFactory
import com.app.busiscoming.data.remote.dto.AlightRequestDto
import com.app.busiscoming.data.remote.dto.Position
import com.app.busiscoming.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 하차 알림 화면 ViewModel
 */
@HiltViewModel
class DisembarkationNotificationViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val busApiServiceFactory: BusApiServiceFactory,
    private val locationHelper: LocationHelper
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DisembarkationNotificationUiState())
    val uiState: StateFlow<DisembarkationNotificationUiState> = _uiState.asStateFlow()
    
    fun initialize(busNumber: String?) {
        _uiState.update { it.copy(busNumber = busNumber) }
    }
    
    suspend fun getBusInfo(): Triple<String, String, String>? {
        return appPreferences.getBusInfo()
    }
    
    suspend fun requestAlight(
        plateNumber: String,
        lineName: String,
        stopNo: String
    ): Result<Unit> {
        return try {
            val deviceId = appPreferences.getDeviceId()
            val serverIp = appPreferences.serverIp.first()
            val port = appPreferences.serverPort.first()
            
            android.util.Log.d("Disembarkation", "하차 요청 시작: plateNumber=$plateNumber, lineName=$lineName, stopNo=$stopNo")
            
            if (serverIp.isEmpty() || port.isEmpty()) {
                android.util.Log.e("Disembarkation", "서버 설정이 없습니다: IP=$serverIp, Port=$port")
                return Result.failure(Exception("서버 IP와 포트를 설정해주세요"))
            }
            
            val currentLocation = locationHelper.getCurrentLocation()
            if (currentLocation == null) {
                android.util.Log.e("Disembarkation", "위치 정보를 가져올 수 없습니다")
                return Result.failure(Exception("위치 정보를 가져올 수 없습니다"))
            }
            
            val baseUrl = "http://$serverIp:$port"
            val apiService = busApiServiceFactory.create(baseUrl)
            
            val request = AlightRequestDto(
                deviceId = deviceId,
                plateNumber = plateNumber,
                lineName = lineName,
                stopNo = stopNo,
                position = Position(
                    lat = currentLocation.latitude,
                    lon = currentLocation.longitude
                )
            )
            
            android.util.Log.d("Disembarkation", "하차 요청 전송: deviceId=$deviceId, lat=${currentLocation.latitude}, lon=${currentLocation.longitude}")
            val response = apiService.alight(request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                android.util.Log.i("Disembarkation", "하차 요청 성공")
                _uiState.update { it.copy(alightRequested = true) }
                Result.success(Unit)
            } else {
                android.util.Log.e("Disembarkation", "하차 요청 실패: code=${response.code()}, body=${response.body()}")
                Result.failure(Exception("하차 요청 실패: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("Disembarkation", "하차 요청 예외 발생: ${e.message}", e)
            Result.failure(e)
        }
    }
}

/**
 * 하차 알림 화면 UI 상태
 */
data class DisembarkationNotificationUiState(
    val busNumber: String? = null,
    val alightRequested: Boolean = false
)





