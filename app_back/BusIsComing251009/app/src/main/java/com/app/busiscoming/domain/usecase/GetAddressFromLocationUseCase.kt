package com.app.busiscoming.domain.usecase

import com.app.busiscoming.domain.model.PlaceInfo
import com.app.busiscoming.domain.repository.GeocodingRepository
import javax.inject.Inject

/**
 * 위도/경도를 주소로 변환하는 UseCase
 */
class GetAddressFromLocationUseCase @Inject constructor(
    private val repository: GeocodingRepository
) {
    suspend operator fun invoke(latitude: Double, longitude: Double): Result<PlaceInfo> {
        return repository.getAddressFromLocation(latitude, longitude)
    }
}




