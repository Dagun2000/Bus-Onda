package com.app.busiscoming.presentation.screens.disembarkationnotification

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import android.widget.Toast
import com.app.busiscoming.presentation.navigation.Screen
// 🌟 데이터 참조를 위한 추가 import
import com.app.busiscoming.presentation.screens.home.HomeViewModel
import com.app.busiscoming.domain.model.TransitMode

/**
 * 하차 알림 화면
 */
@Composable
fun DisembarkationNotificationScreen(
    navController: NavController,
    busNumber: String? = null,
    viewModel: DisembarkationNotificationViewModel = hiltViewModel()
) {
    // 🔥 [디버그 스위치]
    val isDebugMode = false

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 🌟 1. 하차할 정류장 이름을 찾기 위해 HomeViewModel 접근
    val parentEntry = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry(Screen.Home.route)
    }
    val homeViewModel: HomeViewModel = hiltViewModel(parentEntry)
    val homeUiState by homeViewModel.uiState.collectAsState()

    // 🌟 2. 버스 번호와 일치하는 구간(Leg)을 찾아 도착 정류장 이름 추출
    val targetStopName = remember(homeUiState.routes, busNumber) {
        val currentRoute = homeUiState.routes.firstOrNull()
        if (currentRoute != null && busNumber != null) {
            // 현재 버스 번호가 포함된 구간의 '도착 정류장(endName)' 찾기
            val targetLeg = currentRoute.legs.find { leg ->
                leg.mode == TransitMode.BUS &&
                        (leg.routeName?.replace(" ", "")?.contains(busNumber.replace(" ", "")) == true)
            }
            targetLeg?.endName ?: "목적지" // 못 찾으면 기본값
        } else {
            "목적지"
        }
    }

    LaunchedEffect(busNumber) {
        viewModel.initialize(busNumber)
    }

    val scope = rememberCoroutineScope()

    DisembarkationNotificationScreenContent(
        stopName = targetStopName, // 🌟 3. 찾은 정류장 이름 전달
        onDoubleTap = {
            android.util.Log.d("DisembarkationScreen", "하차 요청 버튼 클릭됨")

            // ==========================================
            // 🛠️ 디버그 모드 분기 처리
            // ==========================================
            if (isDebugMode) {
                android.util.Log.d("DisembarkationScreen", "🛑 디버그 모드 ON: 서버 요청 없이 즉시 이동합니다.")
                // 버스 번호 전달 필수
                navController.navigate(Screen.DisembarkComplete.route + "/$busNumber")
            } else {
                scope.launch {
                    try {
                        var busInfo = viewModel.getBusInfo()

                        if (busInfo == null && busNumber != null) {
                            val plateNumber = busNumber.replace(" ", "").takeLast(4)
                            val lineName = busNumber
                            val stopNo = "unknown"

                            val result = viewModel.requestAlight(plateNumber, lineName, stopNo)
                            result.fold(
                                onSuccess = {
                                    android.util.Log.i("DisembarkationScreen", "하차 요청 성공")
                                    // 🌟 성공 시에도 버스 번호 전달
                                    navController.navigate(Screen.DisembarkComplete.route + "/$busNumber")
                                },
                                onFailure = { exception ->
                                    // (에러 처리 로직 동일)
                                    val errorMessage = when {
                                        exception.message?.contains("서버") == true -> exception.message ?: "서버 오류"
                                        exception.message?.contains("위치") == true -> exception.message ?: "위치 정보 오류"
                                        else -> exception.message ?: "하차 요청 실패"
                                    }
                                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else if (busInfo != null) {
                            val (plateNumber, lineName, stopNo) = busInfo
                            val result = viewModel.requestAlight(plateNumber, lineName, stopNo)
                            result.fold(
                                onSuccess = {
                                    android.util.Log.i("DisembarkationScreen", "하차 요청 성공")
                                    // 🌟 성공 시에도 버스 번호 전달
                                    navController.navigate(Screen.DisembarkComplete.route + "/$busNumber")
                                },
                                onFailure = { exception ->
                                    // (에러 처리 로직 동일)
                                    val errorMessage = when {
                                        exception.message?.contains("서버") == true -> exception.message ?: "서버 오류"
                                        exception.message?.contains("위치") == true -> exception.message ?: "위치 정보 오류"
                                        else -> exception.message ?: "하차 요청 실패"
                                    }
                                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            Toast.makeText(context, "버스 정보가 없습니다.", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )
}

/**
 * 하차 알림 화면 컨텐츠
 */
@Composable
fun DisembarkationNotificationScreenContent(
    stopName: String, // 🌟 인자 추가
    onDoubleTap: () -> Unit
) {
    // 🌟 텍스트 변경
    val buttonText = "하차하실 정류장은 ${stopName}입니다.\n정류장에 도착하기 전, 화면을 더블탭해서\n기사님께 하차 알림을 보내세요."

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
        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onDoubleTap,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(buttonFocusRequester)
                .focusable()
                .semantics {
                    contentDescription = buttonText
                },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            )
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