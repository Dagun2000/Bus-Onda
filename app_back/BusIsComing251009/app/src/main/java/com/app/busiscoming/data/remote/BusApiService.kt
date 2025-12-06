package com.app.busiscoming.data.remote

import com.app.busiscoming.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 버스 서버 API 인터페이스
 */
interface BusApiService {
    
    @POST("/api/v1/ping")
    suspend fun ping(@Body request: PingRequest): Response<PingResponse>
    
    @POST("/api/v1/ride/request")
    suspend fun requestRide(@Body request: RideRequestDto): Response<RideResponseDto>
    
    @POST("/api/v1/telemetry")
    suspend fun sendTelemetry(@Body request: TelemetryRequestDto): Response<TelemetryResponseDto>
    
    @POST("/api/v1/ride/cancel")
    suspend fun cancelRide(@Body request: RideCancelRequestDto): Response<RideResponseDto>
    
    @POST("/api/v1/ride/alight")
    suspend fun alight(@Body request: AlightRequestDto): Response<RideResponseDto>
}









