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
import com.app.busiscoming.detection.BusNumberDetectionPipeline
import com.app.busiscoming.util.SelectedRouteHolder
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun BusRecognitionResultScreen(
    navController: NavController,
    busNumber: String? = null,
    isFindSeats: Boolean = false,
    viewModel: BusRecognitionResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // [로직 삽입] 홀더 정보 사용
    val currentLeg = remember { SelectedRouteHolder.getCurrentLeg() }
    val targetBusNumber = currentLeg?.routeName ?: busNumber ?: ""

    LaunchedEffect(targetBusNumber) {
        viewModel.initialize(targetBusNumber)
    }

    BusRecognitionResultScreenContent(
        targetBusNumber = targetBusNumber,
        onDoubleTap = {
            // [핵심 로직] 탑승 완료 시 인덱스 증가


            if (isFindSeats) {
                navController.navigate(Screen.EmptySeat.createRoute(targetBusNumber))
            } else {
                navController.navigate(Screen.DisembarkationNotification.createRoute(targetBusNumber))
            }
        }
    )
}

@Composable
fun BusRecognitionResultScreenContent(
    targetBusNumber: String?,
    onDoubleTap: () -> Unit
) {
    val cleanTarget = remember(targetBusNumber) { targetBusNumber?.filter { it.isDigit() } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vibrator = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_ALARM, 100) }
    var isBeeping by remember { mutableStateOf(false) }
    var stopSoundJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(isBeeping) {
        if (isBeeping) { while (isActive) { toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150); delay(500) } }
    }

    var pipeline by remember { mutableStateOf<BusNumberDetectionPipeline?>(null) }
    val isProcessing = remember { AtomicBoolean(false) }
    val buttonText = if (targetBusNumber != null) "현재 $targetBusNumber 번 버스를 찾고 있습니다.\n탑승완료 하시면 화면을 두번 탭해주세요" else "버스 번호를 확인하는 중입니다..."
    val buttonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { pipeline = BusNumberDetectionPipeline(context); buttonFocusRequester.requestFocus() }
    DisposableEffect(Unit) { onDispose { pipeline?.release(); isBeeping = false; toneGenerator.release() } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f).border(2.dp, Color.Gray, RoundedCornerShape(8.dp)).background(Color.Black, RoundedCornerShape(8.dp)).padding(2.dp)) {
            if (pipeline != null) {
                CameraModule(modifier = Modifier.fillMaxSize(), config = CameraConfig(targetFps = 10, useManualControls = true), onFrameCallback = { imageProxy ->
                    if (!isProcessing.compareAndSet(false, true)) { imageProxy.close(); return@CameraModule }
                    scope.launch(Dispatchers.Default) {
                        try {
                            val result = pipeline!!.detectBusAndNumber(imageProxy, cleanTarget?.ifEmpty { null })
                            if (result.targetFound) {
                                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                launch(Dispatchers.Main) { stopSoundJob?.cancel(); isBeeping = true; stopSoundJob = launch { delay(600); isBeeping = false } }
                            }
                        } finally { imageProxy.close(); isProcessing.set(false) }
                    }
                })
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDoubleTap, modifier = Modifier.fillMaxWidth().focusRequester(buttonFocusRequester).focusable(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface), elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)) {
            Text(text = buttonText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
        }
    }
}