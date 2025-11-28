package com.app.busiscoming.data.model

import com.google.gson.annotations.SerializedName

/**
 * TMAP Transit API 응답 모델
 */
data class TransitRouteResponse(
    @SerializedName("metaData")
    val metaData: MetaData
)

data class MetaData(
    @SerializedName("requestParameters")
    val requestParameters: RequestParameters?,
    
    @SerializedName("plan")
    val plan: Plan
)

data class RequestParameters(
    @SerializedName("busCount")
    val busCount: Int?,
    
    @SerializedName("locale")
    val locale: String?,
    
    @SerializedName("endY")
    val endY: String?,
    
    @SerializedName("endX")
    val endX: String?,
    
    @SerializedName("startY")
    val startY: String?,
    
    @SerializedName("startX")
    val startX: String?,
    
    @SerializedName("reqDttm")
    val reqDttm: String?
)

data class Plan(
    @SerializedName("itineraries")
    val itineraries: List<Itinerary>
)

data class Itinerary(
    @SerializedName("fare")
    val fare: Fare,
    
    @SerializedName("totalTime")
    val totalTime: Int,
    
    @SerializedName("legs")
    val legs: List<Leg>,
    
    @SerializedName("totalWalkTime")
    val totalWalkTime: Int,
    
    @SerializedName("transferCount")
    val transferCount: Int,
    
    @SerializedName("totalDistance")
    val totalDistance: Int,
    
    @SerializedName("pathType")
    val pathType: Int,
    
    @SerializedName("totalWalkDistance")
    val totalWalkDistance: Int
)

data class Fare(
    @SerializedName("regular")
    val regular: Regular
)

data class Regular(
    @SerializedName("totalFare")
    val totalFare: Int,
    
    @SerializedName("currency")
    val currency: Currency
)

data class Currency(
    @SerializedName("symbol")
    val symbol: String,
    
    @SerializedName("currency")
    val currency: String,
    
    @SerializedName("currencyCode")
    val currencyCode: String
)

data class Leg(
    @SerializedName("mode")
    val mode: String,
    
    @SerializedName("sectionTime")
    val sectionTime: Int,
    
    @SerializedName("distance")
    val distance: Int,
    
    @SerializedName("start")
    val start: Location,
    
    @SerializedName("end")
    val end: Location,
    
    @SerializedName("steps")
    val steps: List<Step>?,
    
    @SerializedName("route")
    val route: String?,
    
    @SerializedName("routeColor")
    val routeColor: String?,
    
    @SerializedName("passStopList")
    val passStopList: PassStopList?,
    
    @SerializedName("type")
    val type: Int?,
    
    @SerializedName("passShape")
    val passShape: PassShape?
)

data class Location(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("lon")
    val lon: Double,
    
    @SerializedName("lat")
    val lat: Double
)

data class Step(
    @SerializedName("streetName")
    val streetName: String,
    
    @SerializedName("distance")
    val distance: Int,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("linestring")
    val linestring: String
)

data class PassStopList(
    @SerializedName("stationList")
    val stationList: List<Station>
)

data class Station(
    @SerializedName("index")
    val index: Int,
    
    @SerializedName("stationName")
    val stationName: String,
    
    @SerializedName("lon")
    val lon: String,
    
    @SerializedName("lat")
    val lat: String,
    
    @SerializedName("stationID")
    val stationID: String
)

data class PassShape(
    @SerializedName("linestring")
    val linestring: String
)




