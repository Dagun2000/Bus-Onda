package com.app.busiscoming.presentation.screens.emptyseat

import android.content.Context
import android.media.AudioManager // 🌟 추가
import android.media.ToneGenerator // 🌟 추가
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
import kotlinx.coroutines.Job // 🌟 추가
import kotlinx.coroutines.delay // 🌟 추가
import kotlinx.coroutines.isActive // 🌟 추가
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. 진동기 설정
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // 🌟 [추가] 소리 관련 설정
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_ALARM, 100) }
    var isBeeping by remember { mutableStateOf(false) }
    var stopSoundJob by remember { mutableStateOf<Job?>(null) }

    // 🌟 [추가] 소리 재생 루프
    LaunchedEffect(isBeeping) {
        if (isBeeping) {
            while (isActive) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                delay(500)
            }
        }
    }

    // 2. 좌석 감지 파이프라인 준비
    var pipeline by remember { mutableStateOf<SeatDetectionPipeline?>(null) }
    val isProcessing = remember { AtomicBoolean(false) }

    // 모델 로딩
    LaunchedEffect(Unit) {
        pipeline = SeatDetectionPipeline(context)
    }

    // 메모리 해제
    DisposableEffect(Unit) {
        onDispose {
            pipeline?.release()
            // 🌟 [추가] 화면 이탈 시 소리 즉시 종료
            isBeeping = false
            toneGenerator.release()
        }
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
        // 카메라 영역
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
                    config = CameraConfig(
                        targetFps = 30,
                        useManualControls = false
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

                                // 🌟 빈 좌석(Detection)이 하나라도 있으면
                                if (results.isNotEmpty()) {
                                    // 진동
                                    vibrator.vibrate(
                                        VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                                    )

                                    // 🌟 [추가] 소리 타이머 리셋
                                    launch(Dispatchers.Main) {
                                        stopSoundJob?.cancel()
                                        isBeeping = true
                                        stopSoundJob = launch {
                                            delay(500)
                                            isBeeping = false
                                        }
                                    }
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

        // 하단 버튼
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