package com.app.busiscoming.camera

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class CameraConfig(
    val targetFps: Int = 30,
    val useManualControls: Boolean = false
)

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraModule(
    modifier: Modifier = Modifier,
    config: CameraConfig = CameraConfig(),
    showPreview: Boolean = true,
    showControls: Boolean = false,
    onFrameCallback: (ImageProxy) -> Unit = {},
    // 🌟 [추가] ISO 변경 시 호출될 콜백
    onIsoChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    val targetShutterNs = 11_111_111L // 1/90초
    var isCalibrating by remember { mutableStateOf(config.useManualControls) }
    var calibrationFrameCount by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    val builder = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1920, 1080))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

                    val camera2Interop = Camera2Interop.Extender(builder)

                    camera2Interop.setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            super.onCaptureCompleted(session, request, result)

                            // 🌟 [수정] 현재 ISO 값 추출 및 외부 전달
                            // (보정 중이 아니더라도 ISO 값은 계속 바뀔 수 있으므로 항상 내보냅니다)
                            val currentIso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
                            val currentShutter = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L

                            // UI로 ISO 전달 (콜백)
                            if (currentIso > 0) {
                                onIsoChanged(currentIso)
                            }

                            // 자동 보정 로직
                            if (config.useManualControls && isCalibrating && currentIso > 0 && currentShutter > 0) {
                                if (calibrationFrameCount < 15) {
                                    calibrationFrameCount++
                                    return
                                }

                                val ratio = currentShutter.toDouble() / targetShutterNs.toDouble()
                                var calculatedIso = (currentIso * ratio).toInt()
                                calculatedIso = calculatedIso.coerceIn(1, 10000)

                                Log.d("AutoISO", "⚖️ 보정: ISO $currentIso -> $calculatedIso")

                                camera?.let { cam ->
                                    val camera2Control = Camera2CameraControl.from(cam.cameraControl)
                                    val captureOptions = CaptureRequestOptions.Builder()
                                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                                        .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, targetShutterNs)
                                        .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, calculatedIso)
                                        .build()

                                    camera2Control.captureRequestOptions = captureOptions
                                    isCalibrating = false
                                }
                            }
                        }
                    })

                    val imageAnalysis = builder.build()
                    imageAnalysis.setAnalyzer(cameraExecutor, FrameRateController(config.targetFps, onFrameCallback))

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)

                        // 초기화 시 자동 노출 모드 시작
                        val camera2Control = Camera2CameraControl.from(camera!!.cameraControl)
                        val autoOptions = CaptureRequestOptions.Builder()
                            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            .build()
                        camera2Control.captureRequestOptions = autoOptions

                    } catch (e: Exception) {
                        Log.e("CameraModule", "카메라 바인딩 실패", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private class FrameRateController(
    private val targetFps: Int,
    private val onFrame: (ImageProxy) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastFrameTime = 0L
    private val frameInterval = if (targetFps >= 30) 0L else 1000L / targetFps

    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFrameTime >= frameInterval) {
            lastFrameTime = currentTime
            onFrame(imageProxy)
        } else {
            imageProxy.close()
        }
    }
}