package com.app.busiscoming.presentation.screens.busstoparrival

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.app.busiscoming.camera.CameraConfig
import com.app.busiscoming.camera.CameraModule
import com.app.busiscoming.detection.BusStopDetectionPipeline
import com.app.busiscoming.util.SelectedRouteHolder // [추가] 글로벌 홀더 임포트
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

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
    val isDebugMode = true

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // [수정] 글로벌 홀더에서 현재 단계(버스 구간)의 정보를 가져옵니다.
    // 이전 화면(도보)에서 인덱스를 올렸기 때문에, 여기서는 '버스' 정보가 잡힙니다.
    val currentLeg = remember { SelectedRouteHolder.getCurrentLeg() }
    val finalBusNumber = currentLeg?.routeName ?: busNumber ?: ""

    LaunchedEffect(finalBusNumber) {
        if (finalBusNumber.isNotEmpty()) {
            viewModel.initialize(finalBusNumber)
        }
    }

    BusStopArrivalScreenContent(
        onDoubleTap = {
            android.util.Log.d("BusStopArrival", "더블탭 감지됨. 대상 버스: $finalBusNumber")

            if (isDebugMode) {
                android.util.Log.d("BusStopArrival", "🛑 디버그 모드 ON: 즉시 이동")
                navController.navigate(Screen.BusRecognition.createRoute(finalBusNumber))
            } else {
                scope.launch {
                    val result = viewModel.sendBoardingNotification()
                    result.fold(
                        onSuccess = { requestId ->
                            navController.navigate(Screen.BusRecognition.createRoute(finalBusNumber))
                        },
                        onFailure = { exception ->
                            val errorMessage = when {
                                exception.message == "BUS_NOT_FOUND" -> "버스를 찾을 수 없습니다."
                                else -> "승차 요청에 실패했습니다."
                            }
                            android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
                            // 실패해도 인식 화면으로는 일단 보냄
                            navController.navigate(Screen.BusRecognition.createRoute(finalBusNumber))
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

    // 진동기 및 소리 설정
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_ALARM, 100) }
    var isBeeping by remember { mutableStateOf(false) }
    var stopSoundJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(isBeeping) {
        if (isBeeping) {
            while (isActive) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                delay(500)
            }
        }
    }

    var pipeline by remember { mutableStateOf<BusStopDetectionPipeline?>(null) }
    val isProcessing = remember { AtomicBoolean(false) }

    LaunchedEffect(Unit) {
        pipeline = BusStopDetectionPipeline(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            pipeline?.release()
            isBeeping = false
            toneGenerator.release()
        }
    }

    val buttonText = "정류장에 도착하신 후 화면을 더블탭해서 기사에게 탑승 알림을 보내주세요."
    val buttonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        buttonFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                .background(Color.Black, RoundedCornerShape(8.dp))
        ) {
            if (pipeline != null) {
                CameraModule(
                    modifier = Modifier.fillMaxSize(),
                    config = CameraConfig(targetFps = 30),
                    onFrameCallback = { imageProxy ->
                        if (!isProcessing.compareAndSet(false, true)) {
                            imageProxy.close()
                            return@CameraModule
                        }
                        scope.launch(Dispatchers.Default) {
                            try {
                                val result = pipeline!!.detect(imageProxy)
                                if (result.isVerifiedStation) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                    launch(Dispatchers.Main) {
                                        stopSoundJob?.cancel()
                                        isBeeping = true
                                        stopSoundJob = launch { delay(600); isBeeping = false }
                                    }
                                }
                            } finally {
                                imageProxy.close()
                                isProcessing.set(false)
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onDoubleTap,
            modifier = Modifier.fillMaxWidth().focusRequester(buttonFocusRequester).focusable(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}