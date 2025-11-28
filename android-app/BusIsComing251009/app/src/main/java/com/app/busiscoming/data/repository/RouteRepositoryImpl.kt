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
                count = 3
            )
            
            val response = apiService.getTransitRoutes(apiKey, request)
            val routes = response.metaData.plan.itineraries.map { 
                TransitRouteMapper.toRouteInfo(it)
            }
            
            Result.success(routes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}




