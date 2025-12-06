package com.app.busiscoming.data.remote.dto

data class RideCancelRequestDto(
    val deviceId: String,
    val requestId: String,
    val reason: String = "user_cancel"
)









