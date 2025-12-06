package com.app.busiscoming.domain.model

/**
 * 버스 정보 데이터 클래스
 */
data class BusInfo(
    val plateNumber: String,
    val lineName: String,
    val stopNo: String,
    val direction: String // "up" or "down"
)









