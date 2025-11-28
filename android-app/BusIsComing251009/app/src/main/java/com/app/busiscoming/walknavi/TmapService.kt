package com.app.busiscoming.walknavi

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface TmapApi {
    // 1. 보행자 경로 안내
    @FormUrlEncoded
    @POST("tmap/routes/pedestrian?version=1")
    fun getRoute(
        @Header("appKey") appKey: String,
        @Field("startX") startX: Double,
        @Field("startY") startY: Double,
        @Field("endX") endX: Double, // ★ 이제 입력받은 값을 넣을 예정
        @Field("endY") endY: Double,
        @Field("startName") startName: String = "Start",
        @Field("endName") endName: String = "Goal",
        @Field("searchOption") searchOption: Int = 30
    ): Call<TmapPedestrianResponse>

    // 2. ★ [추가] 장소 검색 (명칭 -> 좌표)
    @GET("tmap/pois?version=1")
    fun searchPoi(
        @Header("appKey") appKey: String,
        @Query("searchKeyword") keyword: String,
        @Query("count") count: Int = 1 // 1개만 찾기
    ): Call<TmapPoiResponse>
}

class TmapService {
    private val api: TmapApi

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://apis.openapi.sk.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(TmapApi::class.java)
    }

    // 장소 검색 함수
    fun searchLocation(
        appKey: String,
        keyword: String,
        onResult: (String, Double, Double) -> Unit,
        onError: () -> Unit
    ) {
        api.searchPoi(appKey, keyword).enqueue(object : Callback<TmapPoiResponse> {
            override fun onResponse(call: Call<TmapPoiResponse>, response: Response<TmapPoiResponse>) {
                val poi = response.body()?.searchPoiInfo?.pois?.poi?.firstOrNull()

                if (poi != null) {
                    // ★ [핵심 수정] 좌표 구하기 로직 (입구 -> 중심 순서로 시도)
                    val lat = poi.frontLat?.toDoubleOrNull() ?: poi.noorLat?.toDoubleOrNull() ?: 0.0
                    val lon = poi.frontLon?.toDoubleOrNull() ?: poi.noorLon?.toDoubleOrNull() ?: 0.0

                    if (lat != 0.0 && lon != 0.0) {
                        onResult(poi.name, lat, lon)
                    } else {
                        // 좌표가 둘 다 없으면 에러 처리
                        onError()
                    }
                } else {
                    onError()
                }
            }
            override fun onFailure(call: Call<TmapPoiResponse>, t: Throwable) {
                onError()
            }
        })
    }

    // ★ [수정] 도착지 좌표(endLat, endLon)를 밖에서 받도록 변경
    fun getRoute(
        appKey: String,
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double, // 여기가 추가됨
        onSuccess: (List<Feature>) -> Unit,
        onError: () -> Unit
    ) {
        // startX=경도, startY=위도 순서 주의!
        val call = api.getRoute(appKey, startLon, startLat, endLon, endLat)

        call.enqueue(object : Callback<TmapPedestrianResponse> {
            override fun onResponse(call: Call<TmapPedestrianResponse>, response: Response<TmapPedestrianResponse>) {
                if (response.isSuccessful) {
                    val features = response.body()?.features ?: emptyList()
                    onSuccess(features)
                } else {
                    onError()
                }
            }
            override fun onFailure(call: Call<TmapPedestrianResponse>, t: Throwable) {
                onError()
            }
        })
    }
}