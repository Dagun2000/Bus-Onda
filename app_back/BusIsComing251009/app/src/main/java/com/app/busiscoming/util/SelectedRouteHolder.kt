package com.app.busiscoming.util

import android.util.Log
import com.app.busiscoming.domain.model.RouteInfo
import com.app.busiscoming.domain.model.RouteLeg

/**
 * 선택된 전체 경로와 현재 진행 중인 인덱스를 관리하는 글로벌 저장소
 */
object SelectedRouteHolder {
    // 전체 경로 정보
    var selectedRoute: RouteInfo? = null

    // 현재 진행 중인 구간 인덱스 (0부터 시작)
    var currentLegIndex: Int = 0

    /**
     * 새로운 경로를 설정하고 인덱스를 0으로 초기화
     */
    fun setRoute(route: RouteInfo) {
        selectedRoute = route
        currentLegIndex = 0
        logRouteDetails(route)
    }

    /**
     * 현재 인덱스에 해당하는 구간(Leg) 정보를 가져옴
     */
    fun getCurrentLeg(): RouteLeg? {
        return selectedRoute?.legs?.getOrNull(currentLegIndex)
        Log.d("ROUTE_DEBUG", " - selectedRoute?.legs?")
    }

    /**
     * 인덱스를 1 증가시킴 (다음 구간으로 이동)
     * @return 다음 구간이 존재하면 true, 마지막 구간이면 false 반환
     */
    fun incrementIndex(): Boolean {
        val route = selectedRoute ?: return false
        if (currentLegIndex < route.legs.size - 1) {
            currentLegIndex++
            return true
        }
        return false
    }
    private fun logRouteDetails(route: RouteInfo) {
        Log.d("ROUTE_DEBUG", "========== [데이터 확인 시작] ==========")
        Log.d("ROUTE_DEBUG", "최초 정류장: ${route.firstStopName}") // 이미지 필드 확인됨
        Log.d("ROUTE_DEBUG", "총 소요시간: ${route.totalTime}초")

        route.legs.forEachIndexed { index, leg ->
            Log.d("ROUTE_DEBUG", "구간 [$index] 모드: ${leg.mode}")
            Log.d("ROUTE_DEBUG", " - 노선명: ${leg.routeName}")
            Log.d("ROUTE_DEBUG", " - 시작지: ${leg.startName}")
            Log.d("ROUTE_DEBUG", " - 종료지: ${leg.endName}")
        }

        val lastLeg = route.legs.lastOrNull()
        Log.d("ROUTE_DEBUG", "최종 목적지 원본 이름: ${lastLeg?.endName}")
        Log.d("ROUTE_DEBUG", "========== [데이터 확인 종료] ==========")
    }
    /**
     * 데이터 초기화
     */
    fun clear() {
        selectedRoute = null
        currentLegIndex = 0
    }
}