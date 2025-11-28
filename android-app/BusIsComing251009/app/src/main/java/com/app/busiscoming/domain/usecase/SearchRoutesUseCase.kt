package com.app.busiscoming.domain.usecase

import com.app.busiscoming.domain.model.PlaceInfo
import com.app.busiscoming.domain.model.RouteInfo
import com.app.busiscoming.domain.repository.RouteRepository
import javax.inject.Inject

/**
 * 경로 검색 UseCase
 */
class SearchRoutesUseCase @Inject constructor(
    private val repository: RouteRepository
) {
    suspend operator fun invoke(
        start: PlaceInfo,
        end: PlaceInfo
    ): Result<List<RouteInfo>> {
        return repository.searchRoutes(start, end)
    }
}




