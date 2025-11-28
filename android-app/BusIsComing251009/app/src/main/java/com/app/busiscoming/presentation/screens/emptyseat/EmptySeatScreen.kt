package com.app.busiscoming.presentation.screens.emptyseat

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.presentation.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

// 🌟 옮겨온 패키지 경로 import (빨간줄 뜨면 Alt+Enter)
import com.app.busiscoming.camera.CameraConfig
import com.app.busiscoming.camera.CameraModule
import com.app.busiscoming.detection.SeatDetectionPipeline

/**
 * 빈 좌석 찾기 화면
 */
@Composable
fun EmptySeatScreen(
    navController: NavController,
    busNumber: String? = null,
    viewModel: EmptySeatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(busNumber) {
        viewModel.initialize(busNumber)
    }

    EmptySeatScreenContent(
        onDoubleTap = {
            // 하차 알림 화면으로 이동 (버스 번호 전달)
            navController.navigate(
                Screen.DisembarkationNotification.createRoute(uiState.busNumber)
            )
        }
    )
}

/**
 * 빈 좌석 찾기 화면 컨텐츠
 */
@Composable
fun EmptySeatScreenContent(
    onDoubleTap: () -> Unit
) {
    // 🌟 1. 변수 선언 (Context, CoroutineScope)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 2. 진동기 설정
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // 3. 좌석 감지 파이프라인 준비
    var pipeline by remember { mutableStateOf<SeatDetectionPipeline?>(null) }
    val isProcessing = remember { AtomicBoolean(false) }

    // 모델 로딩
    LaunchedEffect(Unit) {
        pipeline = SeatDetectionPipeline(context)
    }

    // 메모리 해제
    DisposableEffect(Unit) {
        onDispose { pipeline?.release() }
    }

    val buttonText = "좌석 안내 종료 버튼입니다. 사용하시려면 화면을 더블탭 해주세요."
    val buttonFocusRequester = remember { FocusRequester() }

    // TalkBack 포커스
    LaunchedEffect(Unit) {
        buttonFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 🌟 4. 카메라 영역 (기존 빈 Box 대체)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // 남은 공간 채우기
                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                .background(Color.Black, RoundedCornerShape(8.dp))
                .padding(2.dp)
        ) {
            if (pipeline != null) {
                CameraModule(
                    modifier = Modifier.fillMaxSize(),
                    config = CameraConfig(
                        targetFps = 30,
                        useManualControls = false // 실내는 자동 노출이 유리
                    ),
                    showPreview = true,
                    showControls = false,
                    onFrameCallback = { imageProxy ->
                        if (!isProcessing.compareAndSet(false, true)) {
                            imageProxy.close()
                            return@CameraModule
                        }

                        scope.launch(Dispatchers.Default) {
                            try {
                                // 좌석 감지 수행
                                val results = pipeline!!.detectSeats(imageProxy)

                                // 🌟 빈 좌석(Detection)이 하나라도 있으면 진동!
                                if (results.isNotEmpty()) {
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
                    text = "좌석 찾는 중...",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. 하단 버튼 (기존 유지)
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