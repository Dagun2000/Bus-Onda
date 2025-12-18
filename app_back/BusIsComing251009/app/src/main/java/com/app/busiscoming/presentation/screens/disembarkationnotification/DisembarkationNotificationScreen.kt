package com.app.busiscoming.presentation.screens.disembarkationnotification

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext // [추가] LocalContext 에러 해결
import androidx.compose.ui.semantics.contentDescription // [추가] contentDescription 에러 해결
import androidx.compose.ui.semantics.semantics // [추가] semantics 에러 해결
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import android.widget.Toast
import com.app.busiscoming.presentation.navigation.Screen
import com.app.busiscoming.util.SelectedRouteHolder
import kotlinx.coroutines.launch

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
    val isDebugMode = true

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // [로직] SelectedRouteHolder에서 현재 구간의 내릴 정류장 정보를 가져옵니다.
    val currentLeg = remember { SelectedRouteHolder.getCurrentLeg() }
    val targetStopName = currentLeg?.endName ?: "목적지"

    LaunchedEffect(busNumber) {
        viewModel.initialize(busNumber)
    }

    DisembarkationNotificationScreenContent(
        stopName = targetStopName,
        onDoubleTap = {
            android.util.Log.d("DisembarkationScreen", "하차 요청 버튼 클릭됨")
            SelectedRouteHolder.incrementIndex()
            // [핵심] 하차 요청 시점에 인덱스를 증가시켜 다음 여정(환승/도보) 준비
            SelectedRouteHolder.incrementIndex()

            if (isDebugMode) {
                android.util.Log.d("DisembarkationScreen", "🛑 디버그 모드 ON: 서버 요청 없이 즉시 이동")
                navController.navigate(Screen.DisembarkComplete.route + "/$busNumber")
            } else {
                scope.launch {
                    try {
                        val busInfo = viewModel.getBusInfo()

                        if (busInfo == null && busNumber != null) {
                            val plate = busNumber.replace(" ", "").takeLast(4)
                            viewModel.requestAlight(plate, busNumber, "unknown").fold(
                                onSuccess = {
                                    navController.navigate(Screen.DisembarkComplete.route + "/$busNumber")
                                },
                                onFailure = { exception ->
                                    Toast.makeText(context, exception.message ?: "요청 실패", Toast.LENGTH_LONG).show()
                                    navController.navigate(Screen.DisembarkComplete.route + "/$busNumber")
                                }
                            )
                        } else if (busInfo != null) {
                            val (plateNumber, lineName, stopNo) = busInfo
                            viewModel.requestAlight(plateNumber, lineName, stopNo).fold(
                                onSuccess = {
                                    navController.navigate(Screen.DisembarkComplete.route + "/$busNumber")
                                },
                                onFailure = { exception ->
                                    Toast.makeText(context, exception.message ?: "요청 실패", Toast.LENGTH_LONG).show()
                                    navController.navigate(Screen.DisembarkComplete.route + "/$busNumber")
                                }
                            )
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
 * 하차 알림 화면 컨텐츠 (원래 UI 디자인 100% 복구)
 */
@Composable
fun DisembarkationNotificationScreenContent(
    stopName: String,
    onDoubleTap: () -> Unit
) {
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
                fontWeight = FontWeight.Normal, // 원래대로 Normal 유지
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}