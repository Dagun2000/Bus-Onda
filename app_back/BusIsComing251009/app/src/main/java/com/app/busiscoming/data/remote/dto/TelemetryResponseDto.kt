package com.app.busiscoming.data.remote.dto

data class TelemetryResponseDto(
    val success: Boolean,
    val send: Boolean? = null, // false면 전송 중단
    val error: String? = null
)









