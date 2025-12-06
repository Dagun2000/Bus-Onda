package com.app.busiscoming.domain.model

/**
 * Domain 경로 정보 모델
 */
data class RouteInfo(
    val totalTime: Int,
    val totalFare: Int,
    val transferCount: Int,
    val totalDistance: Int,
    val totalWalkTime: Int,
    val totalWalkDistance: Int,
    val legs: List<RouteLeg>,
    val firstStopName: String,
    val firstStopLat: Double,
    val firstStopLon: Double
)

data class RouteLeg(
    val mode: TransitMode,
    val sectionTime: Int,
    val distance: Int,
    val startName: String,
    val endName: String,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val routeName: String? = null,
    val routeColor: String? = null,
    val steps: List<WalkStep>? = null,
    val passStations: List<StationInfo>? = null
)

data class WalkStep(
    val description: String,
    val distance: Int,
    val streetName: String
)

data class StationInfo(
    val index: Int,
    val name: String,
    val lat: Double,
    val lon: Double
)

enum class TransitMode {
    WALK,
    BUS,
    SUBWAY,
    UNKNOWN
}


