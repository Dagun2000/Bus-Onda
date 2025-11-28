package com.app.busiscoming.domain.usecase

import com.app.busiscoming.domain.model.PlaceInfo
import com.app.busiscoming.domain.repository.GeocodingRepository
import javax.inject.Inject

/**
 * 주소/검색어를 위도/경도로 변환하는 UseCase
 */
class GetLocationFromAddressUseCase @Inject constructor(
    private val repository: GeocodingRepository
) {
    suspend operator fun invoke(address: String): Result<PlaceInfo> {
        return repository.getLocationFromAddress(address)
    }
}




