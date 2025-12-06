package com.app.busiscoming.presentation.screens.busrecognitionresult

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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.app.busiscoming.presentation.navigation.Screen

import com.app.busiscoming.camera.CameraConfig
import com.app.busiscoming.camera.CameraModule
import com.app.busiscoming.detection.BusNumberDetectionPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job // 🌟 추가
import kotlinx.coroutines.delay // 🌟 추가
import kotlinx.coroutines.isActive // 🌟 추가
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
                navController.navigate(
                    Screen.EmptySeat.createRoute(uiState.busNumber)
                )
            } else {
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

    // 🌟 [추가] 소리 관련 설정
    // ToneGenerator: 삐 소리를 내는 가벼운 객체 (알람 볼륨 사용)
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_ALARM, 100) }

    // 소리 재생 상태
    var isBeeping by remember { mutableStateOf(false) }

    // 3초 카운트다운을 관리할 Job (리셋을 위해 변수로 저장)
    var stopSoundJob by remember { mutableStateOf<Job?>(null) }

    // 🌟 [추가] 소리 재생 로직 (isBeeping이 true인 동안 반복)
    LaunchedEffect(isBeeping) {
        if (isBeeping) {
            while (isActive) {
                // TONE_CDMA_PIP: 짧은 삐 소리 (150ms 지속)
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                delay(500) // 0.5초 간격으로 반복 (삐... 삐... 삐...)
            }
        }
    }

    // 2. 파이프라인 및 상태 변수
    var pipeline by remember { mutableStateOf<BusNumberDetectionPipeline?>(null) }
    val isProcessing = remember { AtomicBoolean(false) }

    // 3. 버튼 텍스트 및 포커스
    val buttonText = "버스에 탑승완료 하시면 화면을 두번 탭해주세요"
    val buttonFocusRequester = remember { FocusRequester() }

    // 4. 모델 로딩
    LaunchedEffect(Unit) {
        pipeline = BusNumberDetectionPipeline(context)
    }

    // 5. 메모리 해제 (화면 꺼질 때)
    DisposableEffect(Unit) {
        onDispose {
            pipeline?.release()

            // 🌟 [추가] 화면 나갈 때 소리 즉시 끄기 및 자원 해제
            isBeeping = false
            toneGenerator.release()
        }
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
                                    // 진동
                                    vibrator.vibrate(
                                        VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                                    )

                                    // 🌟 [추가] 소리 타이머 리셋 로직
                                    launch(Dispatchers.Main) {
                                        // 1. 기존에 돌던 '정지 타이머'가 있다면 취소 (리셋 효과)
                                        stopSoundJob?.cancel()

                                        // 2. 소리 켜기
                                        isBeeping = true

                                        // 3. 새로운 3초 타이머 시작
                                        stopSoundJob = launch {
                                            delay(600) // 3초 대기
                                            isBeeping = false // 소리 끄기
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
                    text = "카메라 준비 중...",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
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