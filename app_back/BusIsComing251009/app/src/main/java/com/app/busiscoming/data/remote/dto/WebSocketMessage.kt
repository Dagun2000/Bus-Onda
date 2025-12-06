package com.app.busiscoming.data.remote.dto

data class WebSocketMessage(
    val type: String, // "bus_nearby" or "bus_arrived"
    val distance_m: Double? = null
)









