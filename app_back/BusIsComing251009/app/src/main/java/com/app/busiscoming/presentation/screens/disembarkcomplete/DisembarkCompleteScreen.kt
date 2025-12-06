package com.app.busiscoming.presentation.screens.disembarkcomplete

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.domain.model.RouteInfo
import com.app.busiscoming.domain.model.TransitMode
import com.app.busiscoming.presentation.navigation.Screen
import com.app.busiscoming.presentation.screens.home.HomeViewModel

/**
 * 하차 완료 확인 화면
 */
@Composable
fun DisembarkCompleteScreen(
    navController: NavController,
    busNumber: String? = null
) {
    val parentEntry = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry(Screen.Home.route)
    }
    val homeViewModel: HomeViewModel = hiltViewModel(parentEntry)
    val homeUiState by homeViewModel.uiState.collectAsState()

    val currentRoute: RouteInfo? = homeUiState.routes.firstOrNull()

    // 🔥 [수정됨] HomeUiState에서 진짜 목적지 이름을 가져옵니다.
    // endPlace가 없으면 destinationText(검색어)를, 그것도 없으면 "최종 목적지"를 사용합니다.
    val realDestName = homeUiState.endPlace?.name
        ?: homeUiState.destinationText.ifEmpty { "최종 목적지" }

    DisembarkCompleteScreenContent(
        onDoubleTap = {
            android.util.Log.d("DisembarkationComplete", "하차 완료 버튼 클릭됨.")

            if (currentRoute == null || busNumber == null) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = true }
                }
                return@DisembarkCompleteScreenContent
            }

            // 🔥 [수정됨] 진짜 목적지 이름(realDestName)을 분석기에 함께 전달합니다.
            val action = RouteAnalyzer.determineNextAction(currentRoute, busNumber, realDestName)

            when (action) {
                is NextAction.Transfer -> {
                    navController.navigate(
                        Screen.BusRecognition.createRoute(action.nextRouteName)
                    ) {
                        popUpTo(Screen.DisembarkComplete.route) { inclusive = true }
                    }
                }

                is NextAction.FinalDestination -> {
                    // [CASE B] 최종 하차
                    // 이제 action.destName에는 "도착지"가 아닌 "중앙대 후문"이 들어있습니다.
                    navController.navigate(
                        Screen.WalkingGuide.createRoute(
                            "도보 이동", // 버스 번호 자리 (구분용)
                            action.destName, // -> 중앙대 후문
                            action.destLat,
                            action.destLon
                        )
                    )
                }

                is NextAction.Error -> {
                    navController.navigate(Screen.Home.route)
                }
            }
        }
    )
}

@Composable
fun DisembarkCompleteScreenContent(
    onDoubleTap: () -> Unit
) {
    val buttonText = "하차 완료 시 더블탭 하세요"
    val buttonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        buttonFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onDoubleTap,
            modifier = Modifier.fillMaxWidth().focusRequester(buttonFocusRequester).focusable()
                .semantics { contentDescription = buttonText },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)
        ) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

// =========================================================
// 🧩 로직 분석 클래스 (수정됨)
// =========================================================

sealed class NextAction {
    data class Transfer(val nextRouteName: String) : NextAction()
    data class FinalDestination(val destName: String, val destLat: Double, val destLon: Double) : NextAction()
    object Error : NextAction()
}

object RouteAnalyzer {
    // 🔥 [수정됨] realDestName 인자 추가
    fun determineNextAction(route: RouteInfo, currentBusNum: String, realDestName: String): NextAction {

        // 1. 현재 버스 구간 찾기
        val currentLegIndex = route.legs.indexOfFirst { leg ->
            leg.mode == TransitMode.BUS &&
                    (leg.routeName?.replace(" ", "")?.contains(currentBusNum.replace(" ", "")) == true)
        }

        if (currentLegIndex == -1) return NextAction.Error

        // 2. 남은 구간 확인
        val remainingLegs = route.legs.drop(currentLegIndex + 1)
        val nextTransitLeg = remainingLegs.find {
            it.mode == TransitMode.BUS || it.mode == TransitMode.SUBWAY
        }

        return if (nextTransitLeg != null) {
            // 환승 필요
            NextAction.Transfer(nextRouteName = nextTransitLeg.routeName ?: "다음 교통수단")
        } else {
            // 최종 도착
            val lastLeg = route.legs.last()

            // 🔥 [수정됨] 여기서 lastLeg.endName("도착지")를 버리고
            // 외부에서 받아온 진짜 이름(realDestName = "중앙대 후문")을 사용합니다.
            NextAction.FinalDestination(
                destName = realDestName,
                destLat = lastLeg.endLat,
                destLon = lastLeg.endLon
            )
        }
    }
}