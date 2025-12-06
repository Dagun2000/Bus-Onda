package com.app.busiscoming.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 위치 관련 유틸리티
 */
@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val _locationReady = MutableStateFlow(false)
    val locationReady: StateFlow<Boolean> = _locationReady
    
    /**
     * 위치 권한 확인
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 현재 위치 가져오기
     */
    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) {
            _locationReady.value = false
            return null
        }
        
        return try {
            val cancellationTokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await()
                .also { loc ->
                    _locationReady.value = loc != null
                }
        } catch (e: Exception) {
            _locationReady.value = false
            null
        }
    }
    
    /**
     * 두 지점 간의 거리 계산 (미터)
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // 미터
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }

    fun calculateBearing(
        currentLat: Double,
        currentLon: Double,
        destLat: Double,
        destLon: Double
    ): Double {
        val dLon = Math.toRadians(destLon - currentLon)
        val currentLatRad = Math.toRadians(currentLat)
        val destLatRad = Math.toRadians(destLat)
        
        val y = sin(dLon) * cos(destLatRad)
        val x = cos(currentLatRad) * sin(destLatRad) -
                sin(currentLatRad) * cos(destLatRad) * cos(dLon)
        
        var bearing = Math.toDegrees(atan2(y, x))
        bearing = (bearing + 360) % 360
        
        return bearing
    }
}




