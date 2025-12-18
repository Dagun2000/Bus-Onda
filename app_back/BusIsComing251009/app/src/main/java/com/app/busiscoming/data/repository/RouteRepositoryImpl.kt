package com.app.busiscoming.data.repository

import com.app.busiscoming.data.mapper.TransitRouteMapper
import com.app.busiscoming.data.model.TransitRouteRequest
import com.app.busiscoming.data.remote.TmapApiService
import com.app.busiscoming.domain.model.PlaceInfo
import com.app.busiscoming.domain.model.RouteInfo
import com.app.busiscoming.domain.repository.RouteRepository
import javax.inject.Inject

/**
 * 경로 검색 Repository 구현체
 */
class RouteRepositoryImpl @Inject constructor(
    private val apiService: TmapApiService,
    private val apiKey: String
) : RouteRepository {

    // RouteRepositoryImpl.kt

    override suspend fun searchRoutes(
        start: PlaceInfo,
        end: PlaceInfo
    ): Result<List<RouteInfo>> {
        return try {
            val request = TransitRouteRequest(
                startX = start.longitude.toString(),
                startY = start.latitude.toString(),
                endX = end.longitude.toString(),
                endY = end.latitude.toString(),
                count = 5 // 지하철을 뺄 것이므로 검색 개수를 좀 더 늘려주는 게 좋습니다.
            )

            val response = apiService.getTransitRoutes(apiKey, request)

            // 1. 지하철(SUBWAY)이 포함된 경로는 아예 리스트에서 제외합니다.
            val busOnlyItineraries = response.metaData.plan.itineraries.filter { itinerary ->
                itinerary.legs.none { leg ->
                    leg.mode.uppercase() == "SUBWAY"
                }
            }

            // 2. 필터링된 결과만 RouteInfo로 변환합니다.
            val routes = busOnlyItineraries.map {
                TransitRouteMapper.toRouteInfo(it)
            }

            Result.success(routes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}




