package com.app.busiscoming.presentation.screens.busrecognitionresult

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // 🌟 이거 없어서 에러 났었음
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.presentation.navigation.Screen

// 🌟 옮겨온 패키지 import (빨간줄 뜨면 알트+엔터로 다시 잡으세요)
import com.app.busiscoming.camera.CameraConfig
import com.app.busiscoming.camera.CameraModule
import com.app.busiscoming.detection.BusNumberDetectionPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 버스 인식 결과 화면
 */
@Composable
fun BusRecognitionResultScreen(
    navController: NavController,
    busNumber: String? = null,
    isFindSeats: Boolean = false,
    viewModel: BusRecognitionResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(busNumber) {
        viewModel.initialize(busNumber)
    }

    BusRecognitionResultScreenContent(
        targetBusNumber = uiState.busNumber,
        onDoubleTap = {
            if (isFindSeats) {
                // 빈 좌석 찾기 화면으로 이동
                navController.navigate(
                    Screen.EmptySeat.createRoute(uiState.busNumber)
                )
            } else {
                // 하차 알림 화면으로 이동
                navController.navigate(
                    Screen.DisembarkationNotification.createRoute(uiState.busNumber)
                )
            }
        }
    )
}

/**
 * 버스 인식 결과 화면 컨텐츠
 */
@Composable
fun BusRecognitionResultScreenContent(
    targetBusNumber: String?,
    onDoubleTap: () -> Unit
) {
    val cleanTarget = remember(targetBusNumber) {
        targetBusNumber?.filter { it.isDigit() }
    }
    // 🌟 [중요] 변수 선언부 (여기 있던 게 날아가서 에러 난 겁니다)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. 진동기 초기화
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // 2. 파이프라인 및 상태 변수
    var pipeline by remember { mutableStateOf<BusNumberDetectionPipeline?>(null) }
    val isProcessing = remember { AtomicBoolean(false) }

    // 3. 버튼 텍스트 및 포커스 (기존 코드 복구)
    val buttonText = "버스에 탑승완료 하시면 화면을 두번 탭해주세요"
    val buttonFocusRequester = remember { FocusRequester() }

    // 4. 모델 로딩 (화면 켜질 때)
    LaunchedEffect(Unit) {
        pipeline = BusNumberDetectionPipeline(context)
    }

    // 5. 메모리 해제 (화면 꺼질 때)
    DisposableEffect(Unit) {
        onDispose { pipeline?.release() }
    }

    // 접근성 포커스
    LaunchedEffect(Unit) {
        buttonFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 📸 카메라 영역
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                .background(Color.Black, RoundedCornerShape(8.dp))
                .padding(2.dp)
        ) {
            if (pipeline != null) {
                CameraModule(
                    modifier = Modifier.fillMaxSize(),
                    config = CameraConfig(targetFps = 10, useManualControls = true),
                    showPreview = true,
                    showControls = false,
                    onFrameCallback = { imageProxy ->
                        if (!isProcessing.compareAndSet(false, true)) {
                            imageProxy.close()
                            return@CameraModule
                        }

                        scope.launch(Dispatchers.Default) {
                            try {
                                val result = pipeline!!.detectBusAndNumber(
                                    imageProxy,
                                    cleanTarget?.ifEmpty { null }
                                )

                                if (result.targetFound) {
                                    vibrator.vibrate(
                                        VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                                    )
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                imageProxy.close()
                                isProcessing.set(false)
                            }
                        }
                    }
                )
            } else {
                Text(
                    text = "카메라 준비 중...",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // 🟡 디버그용 노란 글씨 (왼쪽 상단)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Target: ${cleanTarget ?: "전체"}",
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 👇 하단 버튼
        Button(
            onClick = onDoubleTap,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(buttonFocusRequester)
                .focusable(),
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
    }
}