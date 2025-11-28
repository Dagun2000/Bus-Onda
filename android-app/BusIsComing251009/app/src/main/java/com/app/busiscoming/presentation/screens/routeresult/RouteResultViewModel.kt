package com.app.busiscoming.presentation.screens.routeresult

import androidx.lifecycle.ViewModel
import com.app.busiscoming.domain.model.RouteInfo
import com.app.busiscoming.domain.model.RouteLeg
import com.app.busiscoming.domain.model.TransitMode
import com.app.busiscoming.util.TtsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 경로 결과 화면 ViewModel
 */
@HiltViewModel
class RouteResultViewModel @Inject constructor(
    private val ttsHelper: TtsHelper
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(RouteResultUiState())
    val uiState: StateFlow<RouteResultUiState> = _uiState.asStateFlow()
    
    fun setRoutes(routes: List<RouteInfo>) {
        val limited = routes.sortedBy { it.transferCount }.take(5)
        _uiState.update { it.copy(routes = limited, selectedRoute = limited.firstOrNull()) }
    }
    
    fun selectRoute(route: RouteInfo) {
        _uiState.update { it.copy(selectedRoute = route) }
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
    val selectedRoute: RouteInfo? = null
)


