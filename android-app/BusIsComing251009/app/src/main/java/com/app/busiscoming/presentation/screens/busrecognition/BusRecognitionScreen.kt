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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.presentation.navigation.Screen

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
    
    LaunchedEffect(busNumber) {
        viewModel.initialize(busNumber)
    }
    
    BusRecognitionScreenContent(
        onFindBusOnly = {
            viewModel.selectFindBusOnly()
            // 버스 인식 결과 화면으로 이동 (버스 번호 전달, 버스만 찾기)
            navController.navigate(
                Screen.BusRecognitionResult.createRoute(uiState.busNumber, isFindSeats = false)
            )
        },
        onFindBusAndSeats = {
            viewModel.selectFindBusAndSeats()
            // 버스 인식 결과 화면으로 이동 (버스 번호 전달, 빈 좌석 찾기 포함)
            navController.navigate(
                Screen.BusRecognitionResult.createRoute(uiState.busNumber, isFindSeats = true)
            )
        },
        onCancelNotification = { viewModel.cancelBoardingNotification() }
    )
}

/**
 * 버스 인식 기능 화면 컨텐츠
 */
@Composable
fun BusRecognitionScreenContent(
    onFindBusOnly: () -> Unit,
    onFindBusAndSeats: () -> Unit,
    onCancelNotification: () -> Unit
) {
    val instructionText = "버스가 근처에 오면 버스 인식기능이 사용할 수 있습니다.\n화면을 오른쪽으로 스와이프해서 사용하실 기능을\n선택하세요"

    val instructionFocusRequester = remember { FocusRequester() }
    
    // 화면 진입 시 안내 텍스트에 포커스 요청 (TalkBack이 자동으로 읽어줌)
    LaunchedEffect(Unit) {
        instructionFocusRequester.requestFocus()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 안내 텍스트 박스 (파란색 테두리)
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
                    text = "승차 알림 취소",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

