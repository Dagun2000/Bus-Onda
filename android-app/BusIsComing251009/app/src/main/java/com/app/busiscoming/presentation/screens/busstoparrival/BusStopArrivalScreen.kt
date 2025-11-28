package com.app.busiscoming.presentation.screens.busstoparrival

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

// 🌟 아까 복사한 패키지 import (빨간줄 뜨면 알트+엔터)
import com.app.busiscoming.camera.CameraConfig
import com.app.busiscoming.camera.CameraModule
import com.app.busiscoming.detection.BusStopDetectionPipeline

/**
 * 정류장 도착 알림 화면
 */
@Composable
fun BusStopArrivalScreen(
    navController: NavController,
    busNumber: String? = null,
    viewModel: BusStopArrivalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(busNumber) {
        viewModel.initialize(busNumber)
    }

    BusStopArrivalScreenContent(
        onDoubleTap = {
            // 승차 알림 전송
            viewModel.sendBoardingNotification()
            // 버스 인식 화면으로 이동
            navController.navigate(
                Screen.BusRecognition.createRoute(uiState.busNumber)
            )
        }
    )
}

/**
 * 정류장 도착 알림 화면 컨텐츠
 */
@Composable
fun BusStopArrivalScreenContent(
    onDoubleTap: () -> Unit
) {
    // 🌟 1. 변수 선언 및 초기화
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 진동기 설정
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // 정류장 감지 파이프라인
    var pipeline by remember { mutableStateOf<BusStopDetectionPipeline?>(null) }
    val isProcessing = remember { AtomicBoolean(false) }

    // 모델 로딩
    LaunchedEffect(Unit) {
        pipeline = BusStopDetectionPipeline(context)
    }

    // 메모리 해제
    DisposableEffect(Unit) {
        onDispose { pipeline?.release() }
    }

    val buttonText = "정류장에 도착하신 후 화면을 더블탭해서 기사에게 탑승 알림을 보내주세요."
    val buttonFocusRequester = remember { FocusRequester() }

    // TalkBack 포커스
    LaunchedEffect(Unit) {
        delay(100)
        buttonFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 🌟 2. 상단 공간: 카메라 배치 (Spacer 대신 Box+CameraModule 사용)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // 남은 공간 꽉 채우기
                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                .background(Color.Black, RoundedCornerShape(8.dp))
                .padding(2.dp)
        ) {
            if (pipeline != null) {
                CameraModule(
                    modifier = Modifier.fillMaxSize(),
                    config = CameraConfig(targetFps = 30, useManualControls = false), // 정류장은 자동노출 OK
                    showPreview = true,
                    showControls = false,
                    onFrameCallback = { imageProxy ->
                        if (!isProcessing.compareAndSet(false, true)) {
                            imageProxy.close()
                            return@CameraModule
                        }

                        scope.launch(Dispatchers.Default) {
                            try {
                                // 정류장(Shelter) 감지
                                val result = pipeline!!.detect(imageProxy)

                                // 🌟 정류장 확인되면 진동!
                                if (result.isVerifiedStation) {
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
                    text = "정류장 찾는 중...",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🌟 3. 하단 버튼 (TalkBack 자동 테두리 믿고 border 제거함)
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