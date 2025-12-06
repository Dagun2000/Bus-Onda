package com.app.busiscoming.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.busiscoming.data.datastore.AppPreferences
import com.app.busiscoming.data.remote.BusApiServiceFactory
import com.app.busiscoming.data.remote.dto.PingRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 설정 화면 ViewModel
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val busApiServiceFactory: BusApiServiceFactory
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            appPreferences.deviceId.collect { deviceId ->
                _uiState.value = _uiState.value.copy(deviceId = deviceId)
            }
        }
        viewModelScope.launch {
            appPreferences.serverIp.collect { serverIp ->
                _uiState.value = _uiState.value.copy(serverIp = serverIp)
            }
        }
        viewModelScope.launch {
            appPreferences.serverPort.collect { port ->
                _uiState.value = _uiState.value.copy(port = port)
            }
        }
    }
    
    fun updateServerIp(ip: String) {
        viewModelScope.launch {
            appPreferences.setServerIp(ip)
        }
    }
    
    fun updateServerPort(port: String) {
        viewModelScope.launch {
            appPreferences.setServerPort(port)
        }
    }
    
    fun pingTest() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPinging = true, pingResult = null, pingError = null)
            
            try {
                val deviceId = appPreferences.getDeviceId()
                val serverIp = appPreferences.serverIp.first()
                val port = appPreferences.serverPort.first()
                
                if (serverIp.isEmpty() || port.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isPinging = false,
                        pingError = "서버 IP와 포트를 입력해주세요"
                    )
                    return@launch
                }
                
                val baseUrl = "http://$serverIp:$port"
                val apiService = busApiServiceFactory.create(baseUrl)
                
                val startTime = System.currentTimeMillis()
                val request = PingRequest(deviceId)
                val response = apiService.ping(request)
                val endTime = System.currentTimeMillis()
                val rtt = endTime - startTime
                
                if (response.isSuccessful && response.body() != null) {
                    val pingResponse = response.body()!!
                    _uiState.value = _uiState.value.copy(
                        isPinging = false,
                        pingResult = PingResult(
                            success = true,
                            serverTime = pingResponse.serverTime,
                            rtt = rtt
                        )
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isPinging = false,
                        pingError = "서버 연결 실패: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPinging = false,
                    pingError = "연결 오류: ${e.message}"
                )
            }
        }
    }
    
    fun clearPingResult() {
        _uiState.value = _uiState.value.copy(pingResult = null, pingError = null)
    }
}

data class SettingsUiState(
    val deviceId: String = "",
    val serverIp: String = "",
    val port: String = "3000",
    val isPinging: Boolean = false,
    val pingResult: PingResult? = null,
    val pingError: String? = null
)

data class PingResult(
    val success: Boolean,
    val serverTime: String? = null,
    val rtt: Long? = null
)


