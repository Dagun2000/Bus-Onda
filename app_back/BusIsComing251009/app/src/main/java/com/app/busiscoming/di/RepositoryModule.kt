package com.app.busiscoming.di

import com.app.busiscoming.data.remote.TmapApiService
import com.app.busiscoming.data.repository.RouteRepositoryImpl
import com.app.busiscoming.domain.repository.RouteRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository 의존성 주입 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideRouteRepository(
        apiService: TmapApiService,
        @javax.inject.Named("TmapApiKey") apiKey: String
    ): RouteRepository {
        return RouteRepositoryImpl(apiService, apiKey)
    }
    
    @Provides
    @Singleton
    fun provideGeocodingRepository(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): com.app.busiscoming.domain.repository.GeocodingRepository {
        return com.app.busiscoming.data.repository.GeocodingRepositoryImpl(context)
    }
}

