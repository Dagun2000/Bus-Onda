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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.presentation.navigation.Screen
import com.app.busiscoming.walknavi.NavigationViewModel

/**
 * 도보 안내 시작 화면
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
    // 화면 진입 시 즉시 실행: 목적지 설정 후 네비게이션 자동 시작
    LaunchedEffect(stopName, destLat, destLon) {
        viewModel.setDestination(stopName, destLat.toDouble(), destLon.toDouble())
        viewModel.startNavigation() // 여기서 바로 시작합니다.
    }

    WalkingGuideScreenContent(
        onStartNavigation = {
            // 🌟 1. 네비게이션(센서, TTS) 먼저 끄기
            viewModel.stopAllSensors()

            // 🌟 2. 그 다음 화면 이동
            navController.navigate(Screen.BusStopArrival.createRoute(busNumber))
        }
    )
}

/**
 * 도보 안내 시작 화면 컨텐츠 (원본 UI 유지)
 */
@Composable
fun WalkingGuideScreenContent(
    onStartNavigation: () -> Unit
) {
    val buttonText = ""

    val buttonFocusRequester = remember { FocusRequester() }

    // 화면 진입 시 버튼에 포커스 요청 (TalkBack 지원)
    LaunchedEffect(Unit) {
        buttonFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 정류장까지 안내 시작 버튼 (상단)
        Button(
            onClick = onStartNavigation,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .focusRequester(buttonFocusRequester)
                .focusable()
                // ▼▼▼▼▼ 수정된 부분 ▼▼▼▼▼
                .semantics {
                    contentDescription = "ㅣ" // 공백(스페이스) 하나만 딱 넣으세요.
                },
            // ▲▲▲▲▲ 수정 끝 ▲▲▲▲▲
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            // 내용 없음
        }

        // 도보 안내 네비 부분은 비워둠 (Spacer로 공간 확보)
        Spacer(modifier = Modifier.weight(1f))
    }
}