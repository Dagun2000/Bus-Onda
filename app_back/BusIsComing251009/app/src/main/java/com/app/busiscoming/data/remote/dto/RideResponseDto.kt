package com.app.busiscoming.data.remote.dto

/**
 * 승차 요청 응답 DTO
 * 서버 응답 구조: { success: Boolean, data: { requestId: String, status: String }, serverTime: String, error?: String }
 */
data class RideResponseDto(
    val success: Boolean,
    val data: RideResponseData? = null,
    val error: String? = null,
    val serverTime: String? = null
)

data class RideResponseData(
    val requestId: String,
    val status: String
)







