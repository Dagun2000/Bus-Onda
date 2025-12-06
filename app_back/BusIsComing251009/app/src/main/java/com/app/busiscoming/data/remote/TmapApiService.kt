package com.app.busiscoming.data.remote

import com.app.busiscoming.data.model.TransitRouteRequest
import com.app.busiscoming.data.model.TransitRouteResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * TMAP API 서비스 인터페이스
 */
interface TmapApiService {
    
    @POST("transit/routes")
    @Headers("accept: application/json", "content-type: application/json")
    suspend fun getTransitRoutes(
        @Header("appKey") appKey: String,
        @Body request: TransitRouteRequest
    ): TransitRouteResponse
}




