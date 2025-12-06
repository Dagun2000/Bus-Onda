package com.app.busiscoming.presentation.screens.routeresult

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.busiscoming.data.datastore.AppPreferences
import com.app.busiscoming.data.remote.BusApiService
import com.app.busiscoming.data.remote.BusApiServiceFactory
import com.app.busiscoming.data.remote.WebSocketManager
import com.app.busiscoming.data.remote.dto.*
import com.app.busiscoming.domain.model.RouteInfo
import com.app.busiscoming.domain.model.RouteLeg
import com.app.busiscoming.domain.model.TransitMode
import com.app.busiscoming.util.LocationHelper
import com.app.busiscoming.util.TtsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 경로 결과 화면 ViewModel
 */
@HiltViewModel
class RouteResultViewModel @Inject constructor(
    private val ttsHelper: TtsHelper,
    private val appPreferences: AppPreferences,
    private val busApiServiceFactory: BusApiServiceFactory,
    private val webSocketManager: WebSocketManager,
    private val locationHelper: LocationHelper
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(RouteResultUiState())
    val uiState: StateFlow<RouteResultUiState> = _uiState.asStateFlow()
    
    private var telemetryJob: Job? = null
    private var webSocketJob: Job? = null
    private var requestId: String? = null
    private var busInfo: BusInfo? = null
    private var consecutiveFailures = 0
    private var webSocketStarted = false // 웹소켓 연결 여부 추적
    
    fun setRoutes(routes: List<RouteInfo>) {
        val limited = routes.sortedBy { it.transferCount }.take(5)
        _uiState.update { it.copy(routes = limited, selectedRoute = limited.firstOrNull()) }
    }
    
    fun selectRoute(route: RouteInfo) {
        _uiState.update { it.copy(selectedRoute = route) }
    }
    
    /**
     * 경로 선택 시 버스 정보를 추출하고 텔레메트리 전송을 시작합니다.
     * 승차 요청은 보내지 않고 위치 정보만 전송합니다.
     */
    fun startTelemetryForRoute(route: RouteInfo) {
        // 버스 정보 추출
        val busLeg = route.legs.firstOrNull { it.mode == TransitMode.BUS }
        if (busLeg == null) {
            android.util.Log.e("RouteResult", "버스 정보를 찾을 수 없습니다")
            return
        }
        
        val plateNumber = extractPlateNumber(busLeg.routeName ?: "")
        val lineName = busLeg.routeName ?: ""
        val stopNo = route.firstStopName
        val direction = "up" // 기본값
        
        // 버스 정보 저장
        busInfo = BusInfo(plateNumber, lineName, stopNo, direction)
        viewModelScope.launch {
            appPreferences.setBusInfo(plateNumber, lineName, stopNo)
        }
        
        android.util.Log.i("RouteResult", "경로 선택됨. 텔레메트리 시작: busInfo=$busInfo")
        
        // 텔레메트리 전송 시작 (성공 시 웹소켓 연결)
        webSocketStarted = false
        startTelemetry()
    }
    
    suspend fun requestRide(route: RouteInfo): Result<String> {
        return try {
            val deviceId = appPreferences.getDeviceId()
            val serverIp = appPreferences.serverIp.first()
            val port = appPreferences.serverPort.first()
            
            if (serverIp.isEmpty() || port.isEmpty()) {
                Result.failure(Exception("서버 IP와 포트를 설정해주세요"))
            } else {
                // 버스 정보 추출
                val busLeg = route.legs.firstOrNull { it.mode == TransitMode.BUS }
                if (busLeg == null) {
                    Result.failure(Exception("버스 정보를 찾을 수 없습니다"))
                } else {
                    val plateNumber = extractPlateNumber(busLeg.routeName ?: "")
                    val lineName = busLeg.routeName ?: ""
                    val stopNo = route.firstStopName // 정류장 번호는 이름으로 대체
                    val direction = "up" // 기본값, 실제로는 경로 정보에서 추출해야 함
                    
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
                                        requestId = rideResponse.data.requestId
                                        busInfo = BusInfo(plateNumber, lineName, stopNo, direction)
                                        _uiState.update { it.copy(requestId = rideResponse.data.requestId) }
                                        
                                        // requestId와 버스 정보를 DataStore에 저장
                                        appPreferences.setRequestId(rideResponse.data.requestId)
                                        appPreferences.setBusInfo(plateNumber, lineName, stopNo)
                                        
                                        // 텔레메트리 전송 시작
                                        android.util.Log.i("RouteResult", "승차 요청 성공. 텔레메트리 시작: requestId=$requestId, busInfo=$busInfo")
                                        startTelemetry()
                                        
                                        // WebSocket 연결 시작
                                        startWebSocket()
                                        
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
            Result.failure(e)
        }
    }
    
    private fun extractPlateNumber(routeName: String): String {
        // 버스 번호 추출 로직 (예: "동작 01" -> "01")
        // 실제로는 더 정교한 파싱이 필요할 수 있음
        return routeName.replace(" ", "").takeLast(4)
    }
    
    private fun startTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch {
            consecutiveFailures = 0
            
            while (true) {
                try {
                    val deviceId = appPreferences.getDeviceId()
                    val serverIp = appPreferences.serverIp.first()
                    val port = appPreferences.serverPort.first()
                    val currentBusInfo = busInfo
                    
                    if (serverIp.isEmpty() || port.isEmpty()) {
                        android.util.Log.e("Telemetry", "서버 설정이 없습니다: IP=$serverIp, Port=$port")
                        break
                    }
                    
                    if (currentBusInfo == null) {
                        android.util.Log.e("Telemetry", "버스 정보가 없습니다. requestRide()가 성공했는지 확인하세요.")
                        break
                    }
                    
                    val currentLocation = locationHelper.getCurrentLocation()
                    if (currentLocation == null) {
                        android.util.Log.w("Telemetry", "위치 정보를 가져올 수 없습니다. 1초 후 재시도...")
                        delay(1000)
                        continue
                    }
                    
                    val baseUrl = "http://$serverIp:$port"
                    val apiService = busApiServiceFactory.create(baseUrl)
                    
                    val request = TelemetryRequestDto(
                        deviceId = deviceId,
                        plateNumber = currentBusInfo.plateNumber,
                        lineName = currentBusInfo.lineName,
                        stopNo = currentBusInfo.stopNo,
                        direction = currentBusInfo.direction,
                        position = Position(
                            lat = currentLocation.latitude,
                            lon = currentLocation.longitude
                        )
                    )
                    
                    android.util.Log.d("Telemetry", "텔레메트리 전송: plateNumber=${currentBusInfo.plateNumber}, lat=${currentLocation.latitude}, lon=${currentLocation.longitude}")
                    val response = apiService.sendTelemetry(request)
                    
                    if (response.isSuccessful && response.body() != null) {
                        val telemetryResponse = response.body()!!
                        
                        if (telemetryResponse.send == false) {
                            android.util.Log.i("Telemetry", "서버에서 전송 중단 요청")
                            break
                        }
                        
                        consecutiveFailures = 0
                        _uiState.update { it.copy(telemetryError = null) }
                        android.util.Log.d("Telemetry", "텔레메트리 전송 성공")
                        
                        // 첫 번째 성공 시 웹소켓 연결 시작
                        if (!webSocketStarted) {
                            android.util.Log.i("Telemetry", "텔레메트리 전송 성공. 웹소켓 연결 시작")
                            webSocketStarted = true
                            startWebSocket()
                        }
                    } else {
                        consecutiveFailures++
                        android.util.Log.w("Telemetry", "텔레메트리 전송 실패: ${response.code()}, 실패 횟수: $consecutiveFailures")
                        if (consecutiveFailures >= 30) {
                            _uiState.update { 
                                it.copy(telemetryError = "서버와 통신 불가") 
                            }
                        }
                    }
                } catch (e: Exception) {
                    consecutiveFailures++
                    android.util.Log.e("Telemetry", "텔레메트리 전송 예외 발생: ${e.message}", e)
                    if (consecutiveFailures >= 30) {
                        _uiState.update { 
                            it.copy(telemetryError = "서버와 통신 불가") 
                        }
                    }
                }
                
                delay(1000) // 1초 주기
            }
        }
    }
    
    private fun startWebSocket() {
        webSocketJob?.cancel()
        webSocketJob = viewModelScope.launch {
            try {
                val serverIp = appPreferences.serverIp.first()
                val port = appPreferences.serverPort.first()
                val deviceId = appPreferences.getDeviceId()
                
                if (serverIp.isEmpty() || port.isEmpty()) {
                    android.util.Log.e("RouteResult", "서버 설정이 없습니다: IP=$serverIp, Port=$port")
                    return@launch
                }
                
                val wsUrl = "ws://$serverIp:$port/mobile-ws"
                android.util.Log.i("RouteResult", "WebSocket 연결 시작: $wsUrl, deviceId=$deviceId")
                
                webSocketManager.observeMessages(wsUrl, deviceId).collect { message ->
                    android.util.Log.d("RouteResult", "WebSocket 메시지 수신: type=${message.type}, distance=${message.distance_m}")
                    when (message.type) {
                        "bus_nearby" -> {
                            _uiState.update { 
                                it.copy(
                                    busNearby = true,
                                    busDistance = message.distance_m
                                ) 
                            }
                        }
                        "bus_arrived" -> {
                            _uiState.update { 
                                it.copy(
                                    busArrived = true,
                                    busDistance = message.distance_m
                                ) 
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RouteResult", "WebSocket 연결 실패: ${e.message}", e)
            }
        }
    }
    
    fun stopTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = null
        webSocketJob?.cancel()
        webSocketJob = null
        webSocketStarted = false
        webSocketManager.disconnect()
        viewModelScope.launch {
            appPreferences.setRequestId(null)
        }
    }
    
    fun clearBusNearby() {
        _uiState.update { it.copy(busNearby = false) }
    }
    
    fun clearBusArrived() {
        _uiState.update { it.copy(busArrived = false) }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopTelemetry()
    }

    private fun buildRouteAnnouncement(route: RouteInfo): String {
        val hours = route.totalTime / 3600
        val minutes = (route.totalTime % 3600) / 60
        
        val timeText = when {
            hours > 0 -> "${hours}시간 ${minutes}분"
            else -> "${minutes}분"
        }
        
        val fareText = "${route.totalFare}원"
        val transferText = when (route.transferCount) {
            0 -> "환승 없음"
            else -> "환승 ${route.transferCount}회"
        }
        
        // 주요 교통수단 설명
        val transitInfo = route.legs
            .filter { it.mode != TransitMode.WALK }
            .joinToString(", ") { leg ->
                when (leg.mode) {
                    TransitMode.BUS -> "${leg.routeName} 버스"
                    TransitMode.SUBWAY -> "${leg.routeName} 지하철"
                    else -> leg.routeName ?: "대중교통"
                }
            }
        
        return "소요 시간 $timeText, 요금 $fareText, $transferText. 이용 노선: $transitInfo. 최초 정류장: ${route.firstStopName}"
    }

}

/**
 * 경로 결과 화면 UI 상태
 */
data class RouteResultUiState(
    val routes: List<RouteInfo> = emptyList(),
    val selectedRoute: RouteInfo? = null,
    val requestId: String? = null,
    val telemetryError: String? = null,
    val busNearby: Boolean = false,
    val busArrived: Boolean = false,
    val busDistance: Double? = null
)

/**
 * 버스 정보 (내부 사용)
 */
private data class BusInfo(
    val plateNumber: String,
    val lineName: String,
    val stopNo: String,
    val direction: String
)


