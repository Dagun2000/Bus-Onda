package com.app.busiscoming.walknavi

import com.google.gson.JsonElement // ★ 이 import가 꼭 필요합니다!
import com.google.gson.annotations.SerializedName

data class TmapPedestrianResponse(
    @SerializedName("features") val features: List<Feature>
)

data class Feature(
    @SerializedName("type") val type: String,
    @SerializedName("geometry") val geometry: Geometry,
    @SerializedName("properties") val properties: Properties
)

data class Geometry(
    @SerializedName("type") val type: String,
    // ★ [수정] Any -> JsonElement 로 변경
    @SerializedName("coordinates") val coordinates: JsonElement
)

data class Properties(
    @SerializedName("index") val index: Int,
    @SerializedName("description") val description: String,
    @SerializedName("turnType") val turnType: Int?,
    @SerializedName("facilityType") val facilityType: String?,
    @SerializedName("totalDistance") val totalDistance: Int?,
    @SerializedName("totalTime") val totalTime: Int?
)
data class TmapPoiResponse(
    @SerializedName("searchPoiInfo") val searchPoiInfo: SearchPoiInfo
)

data class SearchPoiInfo(
    @SerializedName("pois") val pois: Pois
)

data class Pois(
    @SerializedName("poi") val poi: List<Poi>
)

data class Poi(
    @SerializedName("name") val name: String,
    @SerializedName("frontLat") val frontLat: String?, // 입구 위도 (없을 수 있음)
    @SerializedName("frontLon") val frontLon: String?, // 입구 경도
    @SerializedName("noorLat") val noorLat: String?,   // ★ 중심 위도 (안전빵)
    @SerializedName("noorLon") val noorLon: String?    // ★ 중심 경도
)