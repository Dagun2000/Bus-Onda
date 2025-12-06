package com.app.busiscoming.presentation.screens.busrecognition

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.presentation.navigation.Screen
import android.widget.Toast

/**
 * 버스 인식 기능 화면
 */
@Composable
fun BusRecognitionScreen(
    navController: NavController,
    busNumber: String? = null,
    viewModel: BusRecognitionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(busNumber) {
        viewModel.initialize(busNumber)
    }

    val scope = rememberCoroutineScope()

    // 🌟 1. UI 상태에 있는 버스 번호를 Content로 전달합니다.
    // (uiState에 없으면 인자로 받은 busNumber 사용, 그것도 없으면 빈 문자열)
    val currentBusNumber = uiState.busNumber ?: busNumber ?: ""

    BusRecognitionScreenContent(
        busNumber = currentBusNumber, // 전달!
        onFindBusOnly = {
            viewModel.selectFindBusOnly()
            navController.navigate(
                Screen.BusRecognitionResult.createRoute(uiState.busNumber, isFindSeats = false)
            )
        },
        onFindBusAndSeats = {
            viewModel.selectFindBusAndSeats()
            navController.navigate(
                Screen.BusRecognitionResult.createRoute(uiState.busNumber, isFindSeats = true)
            )
        },
        onCancelNotification = {
            android.util.Log.d("BusRecognition", "승차 알림 취소 버튼 클릭됨")
            scope.launch {
                val result = viewModel.cancelBoardingNotification()
                result.onSuccess {
                    android.util.Log.i("BusRecognition", "승차 알림 취소 성공")
                    navController.popBackStack()
                }
                result.onFailure { exception ->
                    android.util.Log.e("BusRecognition", "승차 알림 취소 실패: ${exception.message}", exception)
                    val errorMessage = exception.message ?: "승차 알림 취소에 실패했습니다."
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    )
}

/**
 * 버스 인식 기능 화면 컨텐츠
 */
@Composable
fun BusRecognitionScreenContent(
    busNumber: String, // 🌟 2. 버스 번호를 인자로 받음
    onFindBusOnly: () -> Unit,
    onFindBusAndSeats: () -> Unit,
    onCancelNotification: () -> Unit
) {
    // 🌟 3. 텍스트에 버스 번호 적용
    val instructionText = "탑승하셔야 하는 버스는 ${busNumber}버스입니다.\n화면을 오른쪽으로 스와이프해서 사용하실 기능을\n선택하세요"
    val cancelButtonText = "승차 알림 취소"

    val instructionFocusRequester = remember { FocusRequester() }
    val cancelButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        instructionFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 안내 텍스트 박스
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(instructionFocusRequester)
                .focusable()
        ) {
            Text(
                text = instructionText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 버튼들
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 버스만 찾기 버튼
            Button(
                onClick = onFindBusOnly,
                modifier = Modifier
                    .fillMaxWidth(),
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
                    text = "버스만 찾기",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }

            // 버스 및 빈 좌석 찾기 버튼
            Button(
                onClick = onFindBusAndSeats,
                modifier = Modifier
                    .fillMaxWidth(),
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
                    text = "버스 및 빈 좌석 찾기",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }

            // 승차 알림 취소 버튼
            Button(
                onClick = onCancelNotification,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(cancelButtonFocusRequester)
                    .focusable()
                    .semantics {
                        contentDescription = cancelButtonText
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
                    text = cancelButtonText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}