package com.app.busiscoming.data.model

import com.google.gson.annotations.SerializedName

/**
 * TMAP Transit API 요청 모델
 */
data class TransitRouteRequest(
    @SerializedName("startX")
    val startX: String,
    
    @SerializedName("startY")
    val startY: String,
    
    @SerializedName("endX")
    val endX: String,
    
    @SerializedName("endY")
    val endY: String,
    
    @SerializedName("count")
    val count: Int = 3,
    
    @SerializedName("lang")
    val lang: Int = 0,
    
    @SerializedName("format")
    val format: String = "json"
)




