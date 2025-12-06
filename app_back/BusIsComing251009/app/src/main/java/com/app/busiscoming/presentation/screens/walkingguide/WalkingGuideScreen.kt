package com.app.busiscoming.presentation.screens.walkingguide

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.presentation.navigation.Screen
import com.app.busiscoming.walknavi.NavigationViewModel

/**
 * 도보 안내 화면
 * (정류장행 / 최종목적지행 공용)
 */
@Composable
fun WalkingGuideScreen(
    navController: NavController,
    busNumber: String,
    stopName: String,
    destLat: Float,
    destLon: Float,
    viewModel: NavigationViewModel = hiltViewModel()
) {
    // '도보 이동'이라는 문자열이 오면 최종 목적지 안내로 판단
    val isFinalLeg = (busNumber == "도보 이동")

    // 화면이 열리자마자 목적지 설정하고 안내 시작
    LaunchedEffect(stopName, destLat, destLon) {
        viewModel.setDestination(
            name = stopName,
            lat = destLat.toDouble(),
            lon = destLon.toDouble(),
            isFinal = isFinalLeg
        )
        viewModel.startNavigation()
    }

    WalkingGuideScreenContent(
        onButtonClick = {
            // 1. 네비게이션/센서 종료
            viewModel.stopAllSensors()

            // 2. 상황에 따른 이동
            if (isFinalLeg) {
                // [CASE B] 최종 목적지 도착 완료 -> 홈 화면으로 복귀
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = true }
                }
            } else {
                // [CASE A] 버스 정류장 도착 완료 -> 버스 기다리는 화면으로 이동
                navController.navigate(Screen.BusStopArrival.createRoute(busNumber))
            }
        }
    )
}

/**
 * 도보 안내 화면 컨텐츠
 * - 불필요한 설명(semantics) 제거함
 */
@Composable
fun WalkingGuideScreenContent(
    onButtonClick: () -> Unit
) {
    val buttonText = "ㅣ"

    val buttonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        buttonFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 투명 버튼
        Button(
            onClick = onButtonClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .focusRequester(buttonFocusRequester)
                .focusable(), // semantics 블록 제거 완료
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}