package com.app.busiscoming.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BusApiService를 생성하는 Factory
 */
@Singleton
class BusApiServiceFactory @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    fun create(baseUrl: String): BusApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(BusApiService::class.java)
    }
}









