package com.app.busiscoming.data.remote.dto

data class PingResponse(
    val success: Boolean,
    val serverTime: String? = null,
    val rtt: Long? = null // Round Trip Time in milliseconds
)









