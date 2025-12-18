package com.app.busiscoming.walknavi

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val tmapService = TmapService()
    private val ttsManager = TtsManager(application.applicationContext)
    private val locationHelper = LocationHelper(application.applicationContext)
    private val compassHelper = CompassHelper(application.applicationContext)

    private val APP_KEY = "Gc1ggMVc4K3p76YmOBSuY6APLbLBDDHQa0ege4VP"

    private var waypoints = listOf<Feature>()
    private var currentTargetIndex = 0
    private var lastSpokenTime = 0L
    private var lastPassedLocation: Location? = null

    private var destName = ""
    private var destLat = 0.0
    private var destLon = 0.0
    private var isFinalDestination = false

    private var lastKnownLat: Double? = null
    private var lastKnownLon: Double? = null
    var currentHeading by mutableStateOf(0f)
        private set

    private var navigationJob: Job? = null

    // 목적지 설정
    fun setDestination(name: String, lat: Double, lon: Double, isFinal: Boolean = false) {
        this.destName = name
        this.destLat = lat
        this.destLon = lon
        this.isFinalDestination = isFinal
        Log.d("NAVI_DEBUG", "목적지 설정: $destName, 최종여부: $isFinal")
    }

    // 내비게이션 시작
    fun startNavigation() {
        ttsManager.speak("목적지 ${destName}까지 안내를 시작합니다.")
        compassHelper.startListening { azimuth -> currentHeading = azimuth }
        locationHelper.startListening { lat, lon ->
            lastKnownLat = lat
            lastKnownLon = lon
            if (waypoints.isEmpty()) requestRouteFromApi(lat, lon)
        }
    }

    private fun requestRouteFromApi(lat: Double, lon: Double) {
        tmapService.getRoute(APP_KEY, lat, lon, destLat, destLon,
            onSuccess = { features ->
                waypoints = features.filter { it.geometry.type == "Point" }
                currentTargetIndex = 1
                lastSpokenTime = 0L

                var directionGuide = "앞으로"
                var distToNext = 0
                if (waypoints.size > 1) {
                    val startLoc = Location("start").apply { latitude = lat; longitude = lon }
                    val nextCoords = waypoints[1].geometry.coordinates.asJsonArray
                    val nextLoc = Location("next").apply { latitude = nextCoords[1].asDouble; longitude = nextCoords[0].asDouble }
                    distToNext = startLoc.distanceTo(nextLoc).toInt()

                    val bearingToNext = startLoc.bearingTo(nextLoc).let { if (it < 0) it + 360 else it }
                    var diff = bearingToNext - currentHeading
                    if (diff > 180) diff -= 360
                    if (diff < -180) diff += 360

                    directionGuide = when {
                        diff in -45.0..45.0 -> "정면으로"
                        diff in 45.0..135.0 -> "오른쪽으로"
                        diff in -135.0..-45.0 -> "왼쪽으로"
                        else -> "뒤로 돌아서"
                    }
                }
                ttsManager.speak("경로 탐색을 완료했습니다. 목적지 ${destName}까지 안내합니다. $directionGuide 약 ${distToNext}미터 이동하세요.")
                startNavigationLoop()
            },
            onError = { ttsManager.speak("경로를 찾을 수 없습니다.") }
        )
    }

    private fun startNavigationLoop() {
        navigationJob?.cancel()
        navigationJob = viewModelScope.launch {
            while (isActive) {
                if (lastKnownLat != null && lastKnownLon != null && waypoints.isNotEmpty()) {
                    trackCurrentTarget(lastKnownLat!!, lastKnownLon!!)
                }
                delay(1000)
            }
        }
    }

    // 🌟 복구된 방향 보정 가이드 함수
    private fun getCorrectionGuide(targetLoc: Location, myLoc: Location): String {
        val bearingToTarget = myLoc.bearingTo(targetLoc).let { if (it < 0) it + 360 else it }
        var diff = bearingToTarget - currentHeading
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360

        return when {
            diff in 60.0..120.0 -> "오른쪽으로 도세요."
            diff in 120.0..180.0 || diff in -180.0..-120.0 -> "뒤로 도세요."
            diff in -120.0..-60.0 -> "왼쪽으로 도세요."
            else -> ""
        }
    }

    private fun trackCurrentTarget(currentLat: Double, currentLon: Double) {
        if (currentTargetIndex >= waypoints.size) {
            finishNavigation()
            return
        }

        val myLoc = Location("me").apply { latitude = currentLat; longitude = currentLon }
        val targetFeature = waypoints[currentTargetIndex]
        val coords = targetFeature.geometry.coordinates.asJsonArray
        val targetLoc = Location("target").apply { latitude = coords[1].asDouble; longitude = coords[0].asDouble }

        val distance = myLoc.distanceTo(targetLoc).toInt()
        val description = targetFeature.properties.description
        val isFinal = (currentTargetIndex == waypoints.lastIndex)
        val arrivalRadius = if (isFinal) 12 else 8

        // A. 목적지/경유지 도착 로직
        if (distance <= arrivalRadius) {
            if (isFinal) {
                val finalMessage = if (isFinalDestination) {
                    "목적지 ${destName} 부근에 도착했습니다. 안내를 종료합니다."
                } else {
                    "${destName} 부근에 도착했습니다. 안내를 종료합니다. 정류장 위치 확인을 위해 화면을 더블탭하여 카메라를 켜주세요."
                }
                ttsManager.speak(finalMessage, isUrgent = true)
                finishNavigation()
                return
            }

            lastPassedLocation = Location("last").apply { latitude = targetLoc.latitude; longitude = targetLoc.longitude }
            val guideText = if (description.contains("도착지")) "목적지 방향으로 이동" else description
            ttsManager.speak("지금 $guideText 하세요.", isUrgent = true)

            currentTargetIndex++
            lastSpokenTime = System.currentTimeMillis()
        }
        // B. 🌟 복구된 이동 중 피드백 로직
        else {
            if (ttsManager.isSpeaking) return
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastSpokenTime > 12000) { // 12초마다 체크
                var allowCorrection = true
                if (lastPassedLocation != null) {
                    // 경유지를 막 지난 직후(15m 이내)에는 방향 지적을 유예 (안정성)
                    if (myLoc.distanceTo(lastPassedLocation!!) < 15) allowCorrection = false
                }

                val correction = if (allowCorrection) getCorrectionGuide(targetLoc, myLoc) else ""

                val message = if (correction.isNotEmpty()) {
                    "$correction 방향이 틀렸습니다."
                } else {
                    "다음 안내까지 ${distance}미터 남았습니다."
                }

                ttsManager.speak(message)
                lastSpokenTime = currentTime
            }
        }
    }

    fun stopAllSensors() {
        finishNavigation()
        ttsManager.shutdown()
    }

    private fun finishNavigation() {
        navigationJob?.cancel()
        locationHelper.stopListening()
        compassHelper.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        stopAllSensors()
    }
}