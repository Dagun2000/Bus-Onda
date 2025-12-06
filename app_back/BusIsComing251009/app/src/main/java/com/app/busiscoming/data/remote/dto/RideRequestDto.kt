package com.app.busiscoming.data.remote.dto

data class RideRequestDto(
    val deviceId: String,
    val plateNumber: String,
    val lineName: String,
    val stopNo: String,
    val direction: String,
    val userLocation: UserLocation
)

data class UserLocation(
    val lat: Double,
    val lon: Double
)









