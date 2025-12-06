package com.app.busiscoming.presentation.screens.busstoparrival

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.busiscoming.data.datastore.AppPreferences
import com.app.busiscoming.data.remote.BusApiServiceFactory
import com.app.busiscoming.data.remote.dto.RideCancelRequestDto
import com.app.busiscoming.data.remote.dto.RideRequestDto
import com.app.busiscoming.data.remote.dto.RideResponseDto
import com.app.busiscoming.data.remote.dto.UserLocation
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
 * 정류장 도착 알림 화면 ViewModel
 */
@HiltViewModel
class BusStopArrivalViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val busApiServiceFactory: BusApiServiceFactory,
    private val locationHelper: LocationHelper
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(BusStopArrivalUiState())
    val uiState: StateFlow<BusStopArrivalUiState> = _uiState.asStateFlow()
    
    fun initialize(busNumber: String?) {
        _uiState.update { it.copy(busNumber = busNumber) }
    }
    
    /**
     * 정류장 도착 후 더블탭 시 승차 요청을 보냅니다.
     */
    suspend fun sendBoardingNotification(): Result<String> {
        return try {
            val deviceId = appPreferences.getDeviceId()
            val serverIp = appPreferences.serverIp.first()
            val port = appPreferences.serverPort.first()
            
            if (serverIp.isEmpty() || port.isEmpty()) {
                Result.failure(Exception("서버 IP와 포트를 설정해주세요"))
            } else {
                // 저장된 버스 정보 가져오기
                val busInfo = appPreferences.getBusInfo()
                if (busInfo == null) {
                    Result.failure(Exception("버스 정보를 찾을 수 없습니다"))
                } else {
                    val (plateNumber, lineName, stopNo) = busInfo
                    val direction = "up" // 기본값
                    
                    val currentLocation = locationHelper.getCurrentLocation()
                    if (currentLocation == null) {
                        Result.failure(Exception("위치 정보를 가져올 수 없습니다"))
                    } else {
                        val baseUrl = "http://$serverIp:$port"
                        val apiService = busApiServiceFactory.create(baseUrl)
                        
                        val request = RideRequestDto(
                            deviceId = deviceId,
                            plateNumber = plateNumber,
                            lineName = lineName,
                            stopNo = stopNo,
                            direction = direction,
                            userLocation = UserLocation(
                                lat = currentLocation.latitude,
                                lon = currentLocation.longitude
                            )
                        )
                        
                        val response = apiService.requestRide(request)
                        
                        when {
                            response.isSuccessful && response.body() != null -> {
                                val rideResponse = response.body()!!
                                when {
                                    rideResponse.success && rideResponse.data?.requestId != null -> {
                                        // requestId 저장
                                        appPreferences.setRequestId(rideResponse.data.requestId)
                                        _uiState.update { 
                                            it.copy(
                                                notificationSent = true,
                                                requestId = rideResponse.data.requestId
                                            ) 
                                        }
                                        android.util.Log.i("BusStopArrival", "승차 요청 성공: requestId=${rideResponse.data.requestId}")
                                        Result.success(rideResponse.data.requestId)
                                    }
                                    rideResponse.error == "BUS_NOT_FOUND" -> {
                                        Result.failure(Exception("BUS_NOT_FOUND"))
                                    }
                                    else -> {
                                        Result.failure(Exception(rideResponse.error ?: "승차 요청 실패"))
                                    }
                                }
                            }
                            else -> {
                                Result.failure(Exception("서버 오류: ${response.code()}"))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BusStopArrival", "승차 요청 예외 발생: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun getRequestId(): String? {
        return appPreferences.getRequestId()
    }
    
    suspend fun cancelRide(requestId: String?): Result<Unit> {
        if (requestId == null) {
            return Result.failure(Exception("requestId가 없습니다"))
        }
        
        return try {
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
                _uiState.update { it.copy(rideCancelled = true) }
                // requestId 삭제
                appPreferences.setRequestId(null)
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
 * 정류장 도착 알림 화면 UI 상태
 */
data class BusStopArrivalUiState(
    val busNumber: String? = null,
    val notificationSent: Boolean = false,
    val rideCancelled: Boolean = false,
    val requestId: String? = null
)

