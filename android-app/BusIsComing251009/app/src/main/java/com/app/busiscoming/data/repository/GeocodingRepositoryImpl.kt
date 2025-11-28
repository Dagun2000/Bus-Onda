package com.app.busiscoming.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.app.busiscoming.domain.model.PlaceInfo
import com.app.busiscoming.domain.repository.GeocodingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Geocoding Repository 구현체 (Android Geocoder 사용)
 */
class GeocodingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : GeocodingRepository {
    
    private val geocoder = Geocoder(context, Locale.KOREAN)
    
    override suspend fun getAddressFromLocation(
        latitude: Double,
        longitude: Double
    ): Result<PlaceInfo> = withContext(Dispatchers.IO) {
        try {
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13 이상
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        continuation.resume(addresses)
                    }
                }
            } else {
                // Android 12 이하
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1) ?: emptyList()
            }
            
            if (addresses.isNotEmpty()) {
                val address = addresses.first()
                val name = extractPlaceName(address)
                
                Result.success(
                    PlaceInfo(
                        name = name,
                        latitude = latitude,
                        longitude = longitude,
                        address = address.getAddressLine(0)
                    )
                )
            } else {
                Result.failure(Exception("주소를 찾을 수 없습니다."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getLocationFromAddress(address: String): Result<PlaceInfo> = withContext(Dispatchers.IO) {
        try {
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(address, 1) { addresses ->
                        continuation.resume(addresses)
                    }
                }
            } else {
                // Android 12 이하
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(address, 1) ?: emptyList()
            }
            
            if (addresses.isNotEmpty()) {
                val addr = addresses.first()
                val name = extractPlaceName(addr)
                
                Result.success(
                    PlaceInfo(
                        name = name,
                        latitude = addr.latitude,
                        longitude = addr.longitude,
                        address = addr.getAddressLine(0)
                    )
                )
            } else {
                Result.failure(Exception("'$address'에 대한 검색 결과가 없습니다."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractPlaceName(address: Address): String {
        address.featureName?.takeIf {
            it.isNotBlank() && !it.matches(Regex("\\d+")) 
        }?.let {
            return it
        }
        
        address.thoroughfare?.let {
            return it
        }
        
        address.subLocality?.let {
            return it
        }
        
        address.locality?.let {
            return it
        }
        
        address.subAdminArea?.let {
            return it
        }
        
        return address.getAddressLine(0)?.split(" ")?.take(3)?.joinToString(" ") ?: "위치"
    }
}

