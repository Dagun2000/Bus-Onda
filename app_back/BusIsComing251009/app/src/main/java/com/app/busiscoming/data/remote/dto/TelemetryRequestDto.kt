package com.app.busiscoming.data.remote.dto

data class TelemetryRequestDto(
    val deviceId: String,
    val plateNumber: String,
    val lineName: String,
    val stopNo: String,
    val direction: String,
    val position: Position
)

data class Position(
    val lat: Double,
    val lon: Double
)









