package com.app.busiscoming.di

import com.app.busiscoming.BuildConfig
import com.app.busiscoming.data.remote.BusApiService
import com.app.busiscoming.data.remote.TmapApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 네트워크 관련 의존성 주입 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    private const val BASE_URL = "https://apis.openapi.sk.com/"
    private const val TIMEOUT = 30L
    
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
            .build()
    }
    
    // TMAP용 Retrofit
    @Provides
    @Singleton
    @javax.inject.Named("TmapRetrofit")
    fun provideTmapRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideTmapApiService(
        @javax.inject.Named("TmapRetrofit") retrofit: Retrofit
    ): TmapApiService {
        return retrofit.create(TmapApiService::class.java)
    }
    
    // API Keys
    @Provides
    @Singleton
    @javax.inject.Named("TmapApiKey")
    fun provideTmapApiKey(): String {
        return BuildConfig.TMAP_API_KEY
    }
    
}

