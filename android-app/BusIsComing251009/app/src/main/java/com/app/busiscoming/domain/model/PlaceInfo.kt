package com.app.busiscoming.domain.model

/**
 * 장소 정보 모델
 */
data class PlaceInfo(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)




