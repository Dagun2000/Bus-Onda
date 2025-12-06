package com.app.busiscoming.presentation.screens.busstoparrival

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
    // 🔥 [디버그 스위치] true면 로직 무시하고 바로 이동
    val isDebugMode = false

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(busNumber) {
        viewModel.initialize(busNumber)
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    BusStopArrivalScreenContent(
        onDoubleTap = {
            android.util.Log.d("BusStopArrival", "더블탭 감지됨")

            if (isDebugMode) {
                android.util.Log.d("BusStopArrival", "🛑 디버그 모드 ON: 서버 요청 없이 즉시 이동합니다.")
                navController.navigate(
                    Screen.BusRecognition.createRoute(uiState.busNumber ?: busNumber ?: "")
                )
            } else {
                scope.launch {
                    val result = viewModel.sendBoardingNotification()
                    result.fold(
                        onSuccess = { requestId ->
                            android.util.Log.d("BusStopArrival", "승차 요청 성공: $requestId")
                            navController.navigate(
                                Screen.BusRecognition.createRoute(uiState.busNumber)
                            )
                        },
                        onFailure = { exception ->
                            android.util.Log.e("BusStopArrival", "승차 요청 실패: ${exception.message}", exception)
                            val errorMessage = when {
                                exception.message == "BUS_NOT_FOUND" -> "버스를 찾을 수 없습니다."
                                exception.message?.contains("서버") == true -> exception.message ?: "서버 오류가 발생했습니다."
                                else -> exception.message ?: "오류가 발생했습니다."
                            }
                            android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
                            navController.navigate(
                                Screen.BusRecognition.createRoute(uiState.busNumber)
                            )
                        }
                    )
                }
            }
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

    // 🌟 [추가] 소리 관련 설정 (ToneGenerator & State)
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

    // 정류장 감지 파이프라인
    var pipeline by remember { mutableStateOf<BusStopDetectionPipeline?>(null) }
    val isProcessing = remember { AtomicBoolean(false) }

    // 모델 로딩
    LaunchedEffect(Unit) {
        pipeline = BusStopDetectionPipeline(context)
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
                    config = CameraConfig(targetFps = 30, useManualControls = false),
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

                                if (result.isVerifiedStation) {
                                    // 진동
                                    vibrator.vibrate(
                                        VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                                    )

                                    // 🌟 [추가] 소리 타이머 리셋 로직
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
                    text = "정류장 찾는 중...",
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