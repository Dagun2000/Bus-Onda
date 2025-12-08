package com.app.busiscoming.presentation.screens.busrecognitionresult

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    // 🌟 [수정] 테스트 모드 설정 변수 추가
    val isTestMode = true // true면 하드코딩된 번호 사용, false면 넘어온 busNumber 사용
    val testBusNumber = "5511" // 테스트하고 싶은 버스 번호 (하드코딩)

    // 🌟 [수정] 실제 사용할 버스 번호 결정 로직
    val targetBusNumber = if (isTestMode) testBusNumber else busNumber

    val uiState by viewModel.uiState.collectAsState()

    // 🌟 [수정] 결정된 targetBusNumber로 초기화 수행
    LaunchedEffect(targetBusNumber) {
        viewModel.initialize(targetBusNumber)
    }

    BusRecognitionResultScreenContent(
        targetBusNumber = uiState.busNumber, // ViewModel이 초기화되면 이 값도 targetBusNumber가 됨
        onDoubleTap = {
            // 네비게이션 시에도 현재 인식 중인 번호(uiState.busNumber)를 넘김
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
    // targetBusNumber가 "150" 등으로 들어오면 여기서 숫자만 필터링하여 감지 로직에 사용됨
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

    // 소리 관련 설정
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_ALARM, 100) }
    var isBeeping by remember { mutableStateOf(false) }
    var stopSoundJob by remember { mutableStateOf<Job?>(null) }

    // 소리 재생 로직
    LaunchedEffect(isBeeping) {
        if (isBeeping) {
            while (isActive) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                delay(500)
            }
        }
    }

    // 2. 파이프라인 및 상태 변수
    var pipeline by remember { mutableStateOf<BusNumberDetectionPipeline?>(null) }
    val isProcessing = remember { AtomicBoolean(false) }

    // 3. 버튼 텍스트 및 포커스
    // 테스트 모드인지 알 수 있게 텍스트에 표시해주는 것도 좋음 (선택사항)
    val buttonText = if (targetBusNumber != null)
        "현재 $targetBusNumber 번 버스를 찾고 있습니다.\n탑승완료 하시면 화면을 두번 탭해주세요"
    else
        "버스 번호를 확인하는 중입니다..."

    val buttonFocusRequester = remember { FocusRequester() }

    // 4. 모델 로딩
    LaunchedEffect(Unit) {
        pipeline = BusNumberDetectionPipeline(context)
    }

    // 5. 메모리 해제
    DisposableEffect(Unit) {
        onDispose {
            pipeline?.release()
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

                                    // 소리 타이머 리셋 로직
                                    launch(Dispatchers.Main) {
                                        stopSoundJob?.cancel()
                                        isBeeping = true
                                        stopSoundJob = launch {
                                            delay(600)
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