package com.app.busiscoming.data.mapper

import com.app.busiscoming.data.model.Itinerary
import com.app.busiscoming.data.model.Leg
import com.app.busiscoming.domain.model.RouteInfo
import com.app.busiscoming.domain.model.RouteLeg
import com.app.busiscoming.domain.model.StationInfo
import com.app.busiscoming.domain.model.TransitMode
import com.app.busiscoming.domain.model.WalkStep

/**
 * API 응답을 Domain 모델로 변환하는 Mapper
 */
object TransitRouteMapper {
    
    fun toRouteInfo(itinerary: Itinerary): RouteInfo {
        // 최초 정류장 찾기: 첫 번째 대중교통의 시작 지점
        val firstTransitLeg = itinerary.legs.firstOrNull { 
            it.mode.uppercase() == "BUS" || it.mode.uppercase() == "SUBWAY" 
        }
        
        // 최초 정류장 정보 (없으면 첫 번째 leg의 end 사용)
        val firstStopName = firstTransitLeg?.start?.name ?: itinerary.legs.first().end.name
        val firstStopLat = firstTransitLeg?.start?.lat ?: itinerary.legs.first().end.lat
        val firstStopLon = firstTransitLeg?.start?.lon ?: itinerary.legs.first().end.lon
        
        return RouteInfo(
            totalTime = itinerary.totalTime,
            totalFare = itinerary.fare.regular.totalFare,
            transferCount = itinerary.transferCount,
            totalDistance = itinerary.totalDistance,
            totalWalkTime = itinerary.totalWalkTime,
            totalWalkDistance = itinerary.totalWalkDistance,
            legs = itinerary.legs.map { toRouteLeg(it) },
            firstStopName = firstStopName,
            firstStopLat = firstStopLat,
            firstStopLon = firstStopLon
        )
    }
    
    private fun toRouteLeg(leg: Leg): RouteLeg {
        return RouteLeg(
            mode = when (leg.mode.uppercase()) {
                "WALK" -> TransitMode.WALK
                "BUS" -> TransitMode.BUS
                "SUBWAY" -> TransitMode.SUBWAY
                else -> TransitMode.UNKNOWN
            },
            sectionTime = leg.sectionTime,
            distance = leg.distance,
            startName = leg.start.name,
            endName = leg.end.name,
            startLat = leg.start.lat,
            startLon = leg.start.lon,
            endLat = leg.end.lat,
            endLon = leg.end.lon,
            routeName = leg.route,
            routeColor = leg.routeColor,
            steps = leg.steps?.map { 
                WalkStep(
                    description = it.description,
                    distance = it.distance,
                    streetName = it.streetName
                )
            },
            passStations = leg.passStopList?.stationList?.map {
                StationInfo(
                    index = it.index,
                    name = it.stationName,
                    lat = it.lat.toDoubleOrNull() ?: 0.0,
                    lon = it.lon.toDoubleOrNull() ?: 0.0
                )
            }
        )
    }
}


