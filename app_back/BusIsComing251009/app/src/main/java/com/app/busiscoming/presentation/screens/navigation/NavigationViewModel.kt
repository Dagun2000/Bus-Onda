package com.app.busiscoming.presentation.screens.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.busiscoming.domain.model.RouteInfo
import com.app.busiscoming.domain.model.RouteLeg
import com.app.busiscoming.domain.model.TransitMode
import com.app.busiscoming.util.LocationHelper
import com.app.busiscoming.util.SensorHelper
import com.app.busiscoming.util.TtsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 네비게이션 화면 ViewModel
 */
@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val locationHelper: LocationHelper,
    private val sensorHelper: SensorHelper,
    private val ttsHelper: TtsHelper
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()
    
    private var locationUpdateJob: Job? = null
    private var lastAnnouncedDistance: Int = -1
    private var hasAnnouncedInitialRoute = false
    
    fun startNavigation(destinationLat: Double, destinationLon: Double, destinationName: String, routeInfo: RouteInfo? = null) {
        _uiState.update { 
            it.copy(
                destinationLat = destinationLat,
                destinationLon = destinationLon,
                destinationName = destinationName,
                routeInfo = routeInfo
            )
        }
        
        announceInitialRoute(destinationName, routeInfo)
        startLocationUpdates()
    }

    private fun announceInitialRoute(destinationName: String, routeInfo: RouteInfo?) {
        if (hasAnnouncedInitialRoute) return
        hasAnnouncedInitialRoute = true
        
        viewModelScope.launch {
            // 기본 안내
            ttsHelper.speak("${destinationName}까지 안내를 시작합니다.")
            delay(2000) // 2초 대기
            
            // 경로 정보가 있으면 상세 안내
            routeInfo?.let { route ->
                announceDetailedRoute(route)
            }
        }
    }
    
    /**
     * 상세 경로 안내 (WalkStep 정보 활용)
     */
    private suspend fun announceDetailedRoute(route: RouteInfo) {
        // 첫 번째 도보 구간 찾기 (현재 위치에서 첫 번째 정류장까지)
        val firstWalkLeg = route.legs.firstOrNull { it.mode == TransitMode.WALK }
        
        if (firstWalkLeg?.steps != null) {
            ttsHelper.speak("첫 번째 정류장까지의 경로를 안내해드리겠습니다.")
            delay(2000)
            
            // 각 단계별 안내
            firstWalkLeg.steps.forEachIndexed { index, step ->
                val stepDescription = buildStepDescription(step, index + 1)
                ttsHelper.speak(stepDescription)
                delay(3000) // 각 단계마다 3초 대기
            }
            
            // 첫 번째 정류장 정보 안내
            val firstTransitLeg = route.legs.firstOrNull { 
                it.mode == TransitMode.BUS || it.mode == TransitMode.SUBWAY 
            }
            
            firstTransitLeg?.let { transitLeg ->
                val transitInfo = when (transitLeg.mode) {
                    TransitMode.BUS -> "${transitLeg.routeName} 버스"
                    TransitMode.SUBWAY -> "${transitLeg.routeName} 지하철"
                    else -> "대중교통"
                }
                ttsHelper.speak("${route.firstStopName}에서 ${transitInfo}를 이용하세요.")
                delay(2000)
            }
        } else {
            // WalkStep 정보가 없는 경우 기본 안내
            ttsHelper.speak("${route.firstStopName}까지 직진하세요.")
        }
    }
    
    /**
     * 단계별 안내 메시지 생성
     */
    private fun buildStepDescription(step: com.app.busiscoming.domain.model.WalkStep, stepNumber: Int): String {
        val distance = if (step.distance >= 1000) {
            "${step.distance / 1000}킬로미터"
        } else {
            "${step.distance}미터"
        }
        
        val streetInfo = if (step.streetName.isNotEmpty()) {
            " ${step.streetName}을 따라"
        } else {
            ""
        }
        
        return "${stepNumber}단계: ${step.description}${streetInfo} ${distance} 이동하세요."
    }
    
    private fun startLocationUpdates() {
        locationUpdateJob?.cancel()
        locationUpdateJob = viewModelScope.launch {
            // 자이로센서와 위치 정보를 결합하여 나침판 기능 구현
            combine(
                sensorHelper.getDeviceOrientation(),
                kotlinx.coroutines.flow.flow {
                    while (isActive) {
                        val location = locationHelper.getCurrentLocation()
                        emit(location)
                        delay(2000) // 2초마다 위치 업데이트
                    }
                }
            ) { deviceOrientation, location ->
                updateNavigationData(location, deviceOrientation)
            }.collect { }
        }
    }
    
    private suspend fun updateNavigationData(location: android.location.Location?, deviceOrientation: Float) {
        val destLat = _uiState.value.destinationLat
        val destLon = _uiState.value.destinationLon
        
        if (location != null && destLat != 0.0 && destLon != 0.0) {
            val currentLat = location.latitude
            val currentLon = location.longitude
            
            // 거리 계산
            val distance = locationHelper.calculateDistance(
                currentLat, currentLon, destLat, destLon
            ).roundToInt()
            
            // 목적지까지의 절대 방향 계산
            val targetBearing = locationHelper.calculateBearing(
                currentLat, currentLon, destLat, destLon
            )
            
            val relativeDirection = sensorHelper.calculateRelativeDirection(
                deviceOrientation, targetBearing
            )
            
            // 화살표는 상대적 방향을 표시 (0도 = 직진, 양수 = 오른쪽, 음수 = 왼쪽)
            val arrowBearing = relativeDirection
            
            val direction = when {
                relativeDirection > -22.5 && relativeDirection <= 22.5 -> "직진"
                relativeDirection > 22.5 && relativeDirection <= 67.5 -> "우측"
                relativeDirection > 67.5 && relativeDirection <= 112.5 -> "우회전"
                relativeDirection > 112.5 && relativeDirection <= 157.5 -> "우측 후진"
                relativeDirection > 157.5 || relativeDirection <= -157.5 -> "후진"
                relativeDirection > -157.5 && relativeDirection <= -112.5 -> "좌측 후진"
                relativeDirection > -112.5 && relativeDirection <= -67.5 -> "좌회전"
                relativeDirection > -67.5 && relativeDirection <= -22.5 -> "좌측"
                else -> "직진"
            }
            
            _uiState.update { 
                it.copy(
                    currentLat = currentLat,
                    currentLon = currentLon,
                    distance = distance,
                    bearing = arrowBearing,
                    deviceOrientation = deviceOrientation,
                    targetBearing = targetBearing,
                    direction = direction,
                    isNearDestination = distance < 50 // 50m 이내
                )
            }
            
            // 거리 안내 (100m, 50m, 20m)
            announceDistance(distance)
        }
    }
    
    private fun announceDistance(distance: Int) {
        val shouldAnnounce = when {
            distance <= 20 && lastAnnouncedDistance > 20 -> {
                "목적지까지 20미터 남았습니다."
            }
            distance <= 50 && lastAnnouncedDistance > 50 -> {
                "목적지까지 50미터 남았습니다."
            }
            distance <= 100 && lastAnnouncedDistance > 100 -> {
                "목적지까지 100미터 남았습니다."
            }
            else -> null
        }
        
        shouldAnnounce?.let { message ->
            ttsHelper.speak(message)
            lastAnnouncedDistance = distance
        }
    }
    
    fun confirmArrival() {
        ttsHelper.speak("정류장에 도착했습니다.")
        _uiState.update { it.copy(arrived = true) }
    }
    
    override fun onCleared() {
        super.onCleared()
        locationUpdateJob?.cancel()
        ttsHelper.stop()
    }
}

/**
 * 네비게이션 화면 UI 상태
 */
data class NavigationUiState(
    val currentLat: Double = 0.0,
    val currentLon: Double = 0.0,
    val destinationLat: Double = 0.0,
    val destinationLon: Double = 0.0,
    val destinationName: String = "",
    val routeInfo: RouteInfo? = null, // 전체 경로 정보
    val distance: Int = 0,
    val bearing: Double = 0.0, // 상대적 방향 (-180 ~ 180도)
    val deviceOrientation: Float = 0f, // 폰의 방향 (0-360도)
    val targetBearing: Double = 0.0, // 목적지까지의 절대 방향 (0-360도)
    val direction: String = "북",
    val isNearDestination: Boolean = false,
    val arrived: Boolean = false
)




