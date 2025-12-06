package com.app.busiscoming.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.app.busiscoming.domain.model.RouteInfo
import com.app.busiscoming.presentation.screens.busrecognition.BusRecognitionScreen
import com.app.busiscoming.presentation.screens.busrecognitionresult.BusRecognitionResultScreen
import com.app.busiscoming.presentation.screens.busstoparrival.BusStopArrivalScreen
import com.app.busiscoming.presentation.screens.disembarkationnotification.DisembarkationNotificationScreen
import com.app.busiscoming.presentation.screens.emptyseat.EmptySeatScreen
import com.app.busiscoming.presentation.screens.home.HomeScreen
import com.app.busiscoming.presentation.screens.navigation.NavigationScreen
import com.app.busiscoming.presentation.screens.routeresult.RouteResultScreen
import com.app.busiscoming.presentation.screens.settings.SettingsScreen
import com.app.busiscoming.presentation.screens.walkingguide.WalkingGuideScreen
import com.app.busiscoming.presentation.screens.disembarkcomplete.DisembarkCompleteScreen
import com.google.gson.Gson
import java.net.URLDecoder

/**
 * 앱 네비게이션 그래프
 */
@Composable
fun NavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // 1. 홈 화면
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        // 2. 경로 결과 화면
        composable(Screen.RouteResult.route) {
            RouteResultScreen(navController = navController)
        }

        // 3. 도보 안내 화면 (수정됨: 인자 4개 받음)
        composable(
            route = Screen.WalkingGuide.route, // "walking_guide/{busNumber}/{stopName}/{lat}/{lng}"
            arguments = listOf(
                navArgument("busNumber") { type = NavType.StringType },
                navArgument("stopName") { type = NavType.StringType },
                navArgument("lat") { type = NavType.FloatType },
                navArgument("lng") { type = NavType.FloatType }
            )
        ) { backStackEntry ->
            // (1) 버스 번호 추출
            val busNumber = try {
                val raw = backStackEntry.arguments?.getString("busNumber") ?: ""
                URLDecoder.decode(raw, "UTF-8")
            } catch (e: Exception) {
                ""
            }

            // (2) 정류장 이름 추출
            val stopName = try {
                val raw = backStackEntry.arguments?.getString("stopName") ?: ""
                URLDecoder.decode(raw, "UTF-8")
            } catch (e: Exception) {
                "목적지"
            }

            // (3) 좌표 추출
            val lat = backStackEntry.arguments?.getFloat("lat") ?: 0f
            val lng = backStackEntry.arguments?.getFloat("lng") ?: 0f

            // 화면 연결
            WalkingGuideScreen(
                navController = navController,
                busNumber = busNumber,
                stopName = stopName,
                destLat = lat,
                destLon = lng
            )
        }

        // 4. 버스 도착 정보 화면
        composable(
            route = Screen.BusStopArrival.route,
            arguments = listOf(
                navArgument("busNumber") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val busNumber = try {
                val encodedBusNumber = backStackEntry.arguments?.getString("busNumber")
                if (encodedBusNumber != null && encodedBusNumber.isNotEmpty()) {
                    URLDecoder.decode(encodedBusNumber, "UTF-8")
                } else {
                    null
                }
            } catch (e: Exception) {
                backStackEntry.arguments?.getString("busNumber")
            }

            BusStopArrivalScreen(
                navController = navController,
                busNumber = busNumber
            )
        }

        // 5. 버스 인식 화면
        composable(
            route = Screen.BusRecognition.route,
            arguments = listOf(
                navArgument("busNumber") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val busNumber = try {
                val encodedBusNumber = backStackEntry.arguments?.getString("busNumber")
                if (encodedBusNumber != null && encodedBusNumber.isNotEmpty()) {
                    URLDecoder.decode(encodedBusNumber, "UTF-8")
                } else {
                    null
                }
            } catch (e: Exception) {
                backStackEntry.arguments?.getString("busNumber")
            }

            BusRecognitionScreen(
                navController = navController,
                busNumber = busNumber
            )
        }

        // 6. 버스 인식 결과 화면
        composable(
            route = Screen.BusRecognitionResult.route,
            arguments = listOf(
                navArgument("busNumber") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("isFindSeats") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val busNumber = try {
                val encodedBusNumber = backStackEntry.arguments?.getString("busNumber")
                if (encodedBusNumber != null && encodedBusNumber.isNotEmpty()) {
                    URLDecoder.decode(encodedBusNumber, "UTF-8")
                } else {
                    null
                }
            } catch (e: Exception) {
                backStackEntry.arguments?.getString("busNumber")
            }
            val isFindSeats = backStackEntry.arguments?.getBoolean("isFindSeats") ?: false

            BusRecognitionResultScreen(
                navController = navController,
                busNumber = busNumber,
                isFindSeats = isFindSeats
            )
        }

        // 7. 빈 좌석 확인 화면
        composable(
            route = Screen.EmptySeat.route,
            arguments = listOf(
                navArgument("busNumber") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val busNumber = try {
                val encodedBusNumber = backStackEntry.arguments?.getString("busNumber")
                if (encodedBusNumber != null && encodedBusNumber.isNotEmpty()) {
                    URLDecoder.decode(encodedBusNumber, "UTF-8")
                } else {
                    null
                }
            } catch (e: Exception) {
                backStackEntry.arguments?.getString("busNumber")
            }

            EmptySeatScreen(
                navController = navController,
                busNumber = busNumber
            )
        }

        // 8. 하차 알림 화면
        composable(
            route = Screen.DisembarkationNotification.route,
            arguments = listOf(
                navArgument("busNumber") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val busNumber = try {
                val encodedBusNumber = backStackEntry.arguments?.getString("busNumber")
                if (encodedBusNumber != null && encodedBusNumber.isNotEmpty()) {
                    URLDecoder.decode(encodedBusNumber, "UTF-8")
                } else {
                    null
                }
            } catch (e: Exception) {
                backStackEntry.arguments?.getString("busNumber")
            }

            DisembarkationNotificationScreen(
                navController = navController,
                busNumber = busNumber
            )
        }

        // 9. 네비게이션 화면
        composable(
            route = Screen.Navigation.route,
            arguments = listOf(
                navArgument("stationLat") { type = NavType.FloatType },
                navArgument("stationLon") { type = NavType.FloatType },
                navArgument("stationName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val stationLat = backStackEntry.arguments?.getFloat("stationLat")?.toDouble() ?: 0.0
            val stationLon = backStackEntry.arguments?.getFloat("stationLon")?.toDouble() ?: 0.0
            val stationName = backStackEntry.arguments?.getString("stationName") ?: ""

            // RouteInfo 파싱
            val routeInfo = try {
                val routeInfoJson = backStackEntry.arguments?.getString("routeInfo")
                if (!routeInfoJson.isNullOrEmpty()) {
                    val decodedJson = URLDecoder.decode(routeInfoJson, "UTF-8")
                    Gson().fromJson(decodedJson, RouteInfo::class.java)
                } else null
            } catch (e: Exception) {
                null
            }

            NavigationScreen(
                navController = navController,
                stationLat = stationLat,
                stationLon = stationLon,
                stationName = stationName,
                routeInfo = routeInfo
            )
        }

        // 10. 설정 화면
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(
            route = Screen.DisembarkComplete.route + "/{busNumber}", // 1. 경로에 인자 추가
            arguments = listOf(
                navArgument("busNumber") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            // 2. 인자 추출
            val busNumber = backStackEntry.arguments?.getString("busNumber")

            // 3. 화면에 전달
            DisembarkCompleteScreen(
                navController = navController,
                busNumber = busNumber
            )
        }
    }
}