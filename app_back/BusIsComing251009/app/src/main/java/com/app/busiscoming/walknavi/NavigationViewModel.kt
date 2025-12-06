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

    private var lastPassedLocation: Location? = null // 방향 지적 유예용



// 목적지 정보

    private var destName = ""

    private var destLat = 0.0

    private var destLon = 0.0



// 🌟 [추가] 최종 목적지 여부 (true: 최종 목적지, false: 버스 정류장)

    private var isFinalDestination = false



    private var lastKnownLat: Double? = null

    private var lastKnownLon: Double? = null

    var currentHeading by mutableStateOf(0f)

        private set



    private var navigationJob: Job? = null



// 🌟 1. 함수 시그니처 변경: isFinal 인자 추가 (기본값 false로 기존 코드 호환)

    fun setDestination(name: String, lat: Double, lon: Double, isFinal: Boolean = false) {

        this.destName = name

        this.destLat = lat

        this.destLon = lon

        this.isFinalDestination = isFinal // 값 저장



        Log.d("NAVI", "목적지 설정됨: $destName ($lat, $lon), 최종목적지여부: $isFinal")

    }



// 2. 내비게이션 시작

    fun startNavigation() {

        ttsManager.speak("경로를 탐색합니다.")

        compassHelper.startListening { azimuth -> currentHeading = azimuth }

        locationHelper.startListening { lat, lon ->

            lastKnownLat = lat

            lastKnownLon = lon

            if (waypoints.isEmpty()) requestRouteFromApi(lat, lon)

        }

    }



// ... (requestRouteFromApi, startNavigationLoop 함수는 기존과 동일 - 생략) ...

// ... (전체 코드를 다 붙여넣으셔도 됩니다. 아래 trackCurrentTarget만 수정됨) ...



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

                ttsManager.speak("경로 탐색 완료. 목적지 $destName 까지 안내합니다. $directionGuide 약 ${distToNext}미터 이동하세요.")

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



// 4. 추적 로직 (★ 여기를 수정)

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

        val facility = targetFeature.properties.facilityType



        val isFinal = (currentTargetIndex == waypoints.lastIndex)

        val arrivalRadius = if (isFinal) 10 else 7



        fun getCorrectionGuide(): String {

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



        fun getClockDirection(target: Location): String {

            val bearing = myLoc.bearingTo(target).let { if (it < 0) it + 360 else it }

            var rel = bearing - currentHeading

            if (rel < 0) rel += 360

            val h = (((rel + 15) / 30).toInt()).let { if (it == 0 || it == 12) 12 else it % 12 }

            return "${h}시 방향"

        }



// =========================================================

// A. 도착 (도착 반경 내 진입)

// =========================================================

        if (distance <= arrivalRadius) {



// 1. 목적지 도착

            if (isFinal) {

// 🌟 [수정] 최종 목적지 여부(isFinalDestination)에 따라 멘트 분기 처리

                val finalMessage = if (isFinalDestination) {

// [CASE B] 최종 목적지 도착 시

                    "목적지, $destName 부근에 도착했습니다. 안내를 종료합니다."

                } else {

// [CASE A] 버스 정류장 도착 시 (기존 멘트)

                    "목적지, $destName 부근에 도착했습니다. 안내를 종료합니다. 정확한 정류장 위치 확인를 찾으시려면 화면을 더블탭해서 카메라를 켜주세요."

                }



                ttsManager.speak(finalMessage, isUrgent = true)

                finishNavigation()

                return

            }



// 2. 경유지 도착 (이하 기존과 동일)

            lastPassedLocation = Location("last").apply { latitude = targetLoc.latitude; longitude = targetLoc.longitude }

            var arriveMsg = "지금 $description 하세요."



            if (facility == "15") {

                var clockDir = ""

                if (currentTargetIndex + 1 < waypoints.size) {

                    val nextC = waypoints[currentTargetIndex + 1].geometry.coordinates.asJsonArray

                    val nextL = Location("n").apply { latitude = nextC[1].asDouble; longitude = nextC[0].asDouble }

                    clockDir = getClockDirection(nextL)

                } else {

                    clockDir = getClockDirection(targetLoc)

                }

                arriveMsg = "전방 $clockDir 횡단보도입니다. 건너가세요."

            }



            currentTargetIndex++



            if (currentTargetIndex < waypoints.size) {

                val nextFeature = waypoints[currentTargetIndex]

                val nextCoords = nextFeature.geometry.coordinates.asJsonArray

                val nextLoc = Location("next").apply { latitude = nextCoords[1].asDouble; longitude = nextCoords[0].asDouble }

                val distToNext = myLoc.distanceTo(nextLoc).toInt()



                val rawDesc = nextFeature.properties.description

                val turnType = nextFeature.properties.turnType

                val nextFacility = nextFeature.properties.facilityType



                val nextAction = when {

                    nextFacility == "15" -> "횡단보도"

                    nextFacility == "12" -> "육교"

                    nextFacility == "17" -> "계단"

                    turnType == 12 -> "좌회전"

                    turnType == 13 -> "우회전"

                    turnType == 14 -> "유턴"

                    turnType == 11 -> "직진"

                    else -> if (rawDesc.contains("이동")) "다음 지점" else rawDesc

                }

                arriveMsg += " 그 다음, $nextAction 까지 ${distToNext}미터 남았습니다."

                lastSpokenTime = System.currentTimeMillis()

            } else {

                arriveMsg += " 안내를 종료합니다."

            }



            ttsManager.speak(arriveMsg, isUrgent = true)

        }

// =========================================================

// B. 이동 중

// =========================================================

        else {

            if (ttsManager.isSpeaking) return



            val currentTime = System.currentTimeMillis()

            if (currentTime - lastSpokenTime > 12000) {

                var allowCorrection = true

                if (lastPassedLocation != null) {

                    if (myLoc.distanceTo(lastPassedLocation!!) < 15) allowCorrection = false

                }



                val guide = if (allowCorrection) getCorrectionGuide() else ""

                val message = if (guide.isNotEmpty()) "$guide 방향이 틀렸습니다."

                else "다음 안내까지 ${distance}미터."



                ttsManager.speak(message)

                lastSpokenTime = currentTime

            }

        }

    }



// ... (나머지 함수들은 기존과 동일) ...

    fun searchAndSetDestination(keyword: String) {

        ttsManager.speak("$keyword 검색 중입니다.")

        tmapService.searchLocation(APP_KEY, keyword,

            onResult = { name, lat, lon ->

                destName = name

                destLat = lat

                destLon = lon

                ttsManager.speak("목적지가 $name 로 설정되었습니다. 내비게이션을 시작하세요.")

                Log.d("NAVI", "목적지 설정 완료: $name ($lat, $lon)")

            },

            onError = {

                ttsManager.speak("장소를 찾지 못했습니다.")

            }

        )

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

    fun simulateLocation(lat: Double, lon: Double) {

        lastKnownLat = lat

        lastKnownLon = lon

        if (navigationJob == null || navigationJob?.isActive == false) startNavigationLoop()

    }



    fun loadNextPoint(): Pair<Double, Double>? {

        if (currentTargetIndex < waypoints.size) {

            val feature = waypoints[currentTargetIndex]

            val coords = feature.geometry.coordinates.asJsonArray

            return Pair(coords[1].asDouble, coords[0].asDouble)

        }

        return null

    }



    override fun onCleared() {

        super.onCleared()

        stopAllSensors()

    }

}