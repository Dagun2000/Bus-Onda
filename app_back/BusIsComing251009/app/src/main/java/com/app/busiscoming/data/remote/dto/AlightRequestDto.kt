package com.app.busiscoming.data.remote.dto

data class AlightRequestDto(
    val deviceId: String,
    val plateNumber: String,
    val lineName: String,
    val stopNo: String,
    val position: Position
)









