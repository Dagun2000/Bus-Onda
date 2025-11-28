package com.app.busiscoming.domain.repository

import com.app.busiscoming.domain.model.PlaceInfo
import com.app.busiscoming.domain.model.RouteInfo

/**
 * 경로 검색 Repository 인터페이스
 */
interface RouteRepository {
    suspend fun searchRoutes(
        start: PlaceInfo,
        end: PlaceInfo
    ): Result<List<RouteInfo>>
}




