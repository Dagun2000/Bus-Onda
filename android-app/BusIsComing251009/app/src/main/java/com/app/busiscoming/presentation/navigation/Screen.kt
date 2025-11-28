package com.app.busiscoming.presentation.navigation

import android.net.Uri
import com.app.busiscoming.domain.model.RouteInfo
import com.google.gson.Gson
import java.net.URLEncoder

/**
 * 네비게이션 화면 정의
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object RouteResult : Screen("route_result")

    // 🌟 변경 부분: URL 경로에 파라미터 3개(stopName, lat, lng)를 추가합니다.
    data object WalkingGuide : Screen("walking_guide/{busNumber}/{stopName}/{lat}/{lng}") {

        // createRoute 함수도 4개의 인자를 받도록 수정합니다.
        fun createRoute(
            busNumber: String,
            stopName: String,
            lat: Double,
            lng: Double
        ): String {
            val encodedBusNumber = URLEncoder.encode(busNumber, "UTF-8")
            // 정류장 이름에 공백이나 특수문자가 있을 수 있으므로 인코딩합니다.
            val encodedStopName = URLEncoder.encode(stopName, "UTF-8")

            return "walking_guide/$encodedBusNumber/$encodedStopName/$lat/$lng"
        }
    }
    data object BusStopArrival : Screen("bus_stop_arrival/{busNumber}") {
        fun createRoute(busNumber: String? = null): String {
            val encodedBusNumber = if (busNumber != null) {
                URLEncoder.encode(busNumber, "UTF-8")
            } else {
                ""
            }
            return "bus_stop_arrival/$encodedBusNumber"
        }
    }
    data object BusRecognition : Screen("bus_recognition/{busNumber}") {
        fun createRoute(busNumber: String? = null): String {
            val encodedBusNumber = if (busNumber != null) {
                URLEncoder.encode(busNumber, "UTF-8")
            } else {
                ""
            }
            return "bus_recognition/$encodedBusNumber"
        }
    }
    data object BusRecognitionResult : Screen("bus_recognition_result/{busNumber}/{isFindSeats}") {
        fun createRoute(busNumber: String? = null, isFindSeats: Boolean = false): String {
            val encodedBusNumber = if (busNumber != null) {
                URLEncoder.encode(busNumber, "UTF-8")
            } else {
                ""
            }
            return "bus_recognition_result/$encodedBusNumber/$isFindSeats"
        }
    }
    data object EmptySeat : Screen("empty_seat/{busNumber}") {
        fun createRoute(busNumber: String? = null): String {
            val encodedBusNumber = if (busNumber != null) {
                URLEncoder.encode(busNumber, "UTF-8")
            } else {
                ""
            }
            return "empty_seat/$encodedBusNumber"
        }
    }
    data object DisembarkationNotification : Screen("disembarkation_notification/{busNumber}") {
        fun createRoute(busNumber: String? = null): String {
            val encodedBusNumber = if (busNumber != null) {
                URLEncoder.encode(busNumber, "UTF-8")
            } else {
                ""
            }
            return "disembarkation_notification/$encodedBusNumber"
        }
    }
    data object Navigation : Screen("navigation/{stationLat}/{stationLon}/{stationName}") {
        fun createRoute(stationLat: Double, stationLon: Double, stationName: String, routeInfo: RouteInfo? = null): String {
            val encodedStationName = URLEncoder.encode(stationName, "UTF-8")
            val baseRoute = "navigation/$stationLat/$stationLon/$encodedStationName"

            return if (routeInfo != null) {
                val routeJson = Gson().toJson(routeInfo)
                val encodedRouteJson = URLEncoder.encode(routeJson, "UTF-8")
                "$baseRoute?routeInfo=$encodedRouteJson"
            } else {
                baseRoute
            }
        }
    }
}




