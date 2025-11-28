package com.app.busiscoming.domain.repository

import com.app.busiscoming.domain.model.PlaceInfo

/**
 * Geocoding Repository 인터페이스
 */
interface GeocodingRepository {
    
    /**
     * 위도/경도를 주소로 변환
     */
    suspend fun getAddressFromLocation(latitude: Double, longitude: Double): Result<PlaceInfo>
    
    /**
     * 주소/검색어를 위도/경도로 변환
     */
    suspend fun getLocationFromAddress(address: String): Result<PlaceInfo>
}




