package com.app.busiscoming.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.busiscoming.domain.model.PlaceInfo
import com.app.busiscoming.domain.model.RouteInfo
import com.app.busiscoming.domain.usecase.SearchRoutesUseCase
import com.app.busiscoming.util.LocationHelper
import com.app.busiscoming.util.TtsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 홈 화면 ViewModel
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchRoutesUseCase: SearchRoutesUseCase,
    private val locationHelper: LocationHelper,
    private val getAddressFromLocationUseCase: com.app.busiscoming.domain.usecase.GetAddressFromLocationUseCase,
    private val getLocationFromAddressUseCase: com.app.busiscoming.domain.usecase.GetLocationFromAddressUseCase,
    private val ttsHelper: TtsHelper
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        // 앱 시작 시 자동으로 현재 위치를 출발지로 설정
        setCurrentLocationAsStart()
    }
    
    fun onDestinationTextChanged(text: String) {
        _uiState.update { it.copy(destinationText = text) }
    }
    
    fun setCurrentLocationAsStart() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLocation = true) }
            
            val location = locationHelper.getCurrentLocation()
            if (location != null) {
                getAddressFromLocationUseCase(location.latitude, location.longitude)
                    .onSuccess { place ->
                        _uiState.update { 
                            it.copy(
                                startPlace = place,
                                isLoadingLocation = false,
                                isLocationReady = true
                            )
                        }
                    }
                    .onFailure { error ->
                        // Geocoding 실패 시 기본 정보로 설정
                        val place = PlaceInfo(
                            name = "현재 위치",
                            latitude = location.latitude,
                            longitude = location.longitude,
                            address = null
                        )
                        _uiState.update { 
                            it.copy(
                                startPlace = place,
                                isLoadingLocation = false,
                                isLocationReady = true,
                                error = "주소 변환 실패: ${error.message}"
                            )
                        }
                    }
            } else {
                _uiState.update { 
                    it.copy(
                        isLoadingLocation = false,
                        isLocationReady = false,
                        error = "위치를 가져올 수 없습니다. 위치 권한을 확인해주세요."
                    )
                }
            }
        }
    }
    
    fun setDestinationFromText() {
        val text = _uiState.value.destinationText.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(error = "목적지를 입력해주세요.") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingDestination = true) }
            
            // Google Geocoding API로 검색
            getLocationFromAddressUseCase(text)
                .onSuccess { place ->
                    _uiState.update { 
                        it.copy(
                            endPlace = place,
                            isSearchingDestination = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isSearchingDestination = false,
                            error = error.message ?: "목적지를 찾을 수 없습니다."
                        )
                    }
                }
        }
    }
    
    fun searchRoutes() {
        val start = _uiState.value.startPlace
        val end = _uiState.value.endPlace
        
        if (start == null || end == null) {
            _uiState.update { 
                it.copy(error = "출발지와 목적지를 모두 입력해주세요.")
            }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            searchRoutesUseCase(start, end)
                .onSuccess { routes ->
                    _uiState.update { 
                        it.copy(
                            routes = routes,
                            isLoading = false,
                            searchCompleted = true
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "경로 검색에 실패했습니다: ${throwable.message}"
                        )
                    }
                }
        }
    }
    
    fun onDestinationRecognized(text: String) {
        _uiState.update { it.copy(destinationText = text) }
        viewModelScope.launch {
            ttsHelper.speakDestinationConfirmed(text)
        }
    }
    
    fun onStartClicked() {
        val state = _uiState.value
        if (!state.isLocationReady) {
            ttsHelper.speakLocationLoading()
            return
        }

        // 목적지가 설정되지 않았을 때 처리
        if (state.endPlace == null) {
            val text = state.destinationText.trim()
            if (text.isEmpty()) {
                ttsHelper.speak("화면을 두 번 빠르게 터치하고 목적지를 말해주세요")
                return
            }
            // 텍스트는 있으나 좌표 미설정이면 지오코딩 후 검색 진행
            viewModelScope.launch {
                _uiState.update { it.copy(isSearchingDestination = true) }
                getLocationFromAddressUseCase(text)
                    .onSuccess { place ->
                        _uiState.update {
                            it.copy(endPlace = place, isSearchingDestination = false)
                        }
                        searchRoutes()
                    }
                    .onFailure {
                        _uiState.update { it.copy(isSearchingDestination = false) }
                        ttsHelper.speak("목적지를 찾을 수 없습니다. 다시 말씀해주세요")
                    }
            }
            return
        }

        // 출발지/목적지 모두 준비된 경우 검색 실행
        searchRoutes()
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun resetSearchCompleted() {
        _uiState.update { it.copy(searchCompleted = false) }
    }

    fun onVoiceRecognitionFailed() {
        ttsHelper.speak("인식하지 못했습니다. 화면을 더블 탭하여 다시 말씀해주세요")
    }
}

/**
 * 홈 화면 UI 상태
 */
data class HomeUiState(
    val startPlace: PlaceInfo? = null,
    val endPlace: PlaceInfo? = null,
    val destinationText: String = "",
    val routes: List<RouteInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingLocation: Boolean = false,
    val isSearchingDestination: Boolean = false,
    val error: String? = null,
    val searchCompleted: Boolean = false,
    val isLocationReady: Boolean = false
)

