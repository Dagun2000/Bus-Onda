    package com.app.busiscoming.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.app.busiscoming.model.BoundingBox
import com.app.busiscoming.model.Detection
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions // 🌟 여기가 핵심입니다
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.collections.get
import kotlin.math.max
import kotlin.math.min
import kotlin.text.get

// 데이터 클래스 (없으면 에러나니 꼭 포함하세요)
data class StopPipelineResult(
    val detections: List<Detection>,
    val isVerifiedStation: Boolean,
    val mode: String
)

class BusStopDetectionPipeline(context: Context) {

    // 1. 광고판(정류장) 위치 찾는 YOLO 모델
    private val detector = TfliteInferenceEngine(
        modelPath = "BusStation.tflite",
        inputSize = 640,
        confThreshold = 0.30f,
        classNames = listOf("Busstation")
    )

    // 2. 🌟 [수정] 오직 영어(Latin)만 인식하는 설정
    // 숫자 인식기(Korean/Number)가 아닙니다. DEFAULT_OPTIONS가 바로 Latin(영어) 전용입니다.
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // 이미지 처리용 변수
    private var pixels: IntArray? = null
    private val matrix = Matrix()

    // 추적용 변수
    private var lastTrackedBox: BoundingBox? = null
    private val IOU_THRESHOLD = 0.4f

    // 타겟: JCDecaux (대문자 기준 2글자 파편들)
    private val targetFragments = listOf("JC", "CD", "DE", "EC", "CA", "AU", "UX")

    init {
        detector.initialize(context)
        Log.d("BusStopPipeline", "✅ 정류장 감지(JCDecaux) 준비 완료")
    }

    fun detect(imageProxy: ImageProxy): StopPipelineResult {
        val finalDetections = mutableListOf<Detection>()
        var verified = false
        var currentMode = "SEARCH"

        val fullImageBitmap = imageProxyToRotatedBitmap(imageProxy)

        try {
            // 1. YOLO로 광고판 후보 찾기
            val rawDetections = detector.inferenceOnBitmap(fullImageBitmap)

            // 2. 추적 로직 (이미 찾았으면 OCR 안함)
            if (lastTrackedBox != null) {
                currentMode = "TRACKING"

                val bestMatch = rawDetections
                    .map { detection -> Pair(detection, calculateIoU(detection.boundingBox, lastTrackedBox!!)) }
                    .filter { it.second > IOU_THRESHOLD }
                    .maxByOrNull { it.second }

                if (bestMatch != null) {
                    val (detection, _) = bestMatch
                    lastTrackedBox = detection.boundingBox
                    verified = true

                    finalDetections.add(detection.copy(
                        className = "JCDecaux (Tracked)",
                        confidence = 1.0f
                    ))
                } else {
                    lastTrackedBox = null
                    currentMode = "LOST"
                }

            } else {
                // 3. 검색 로직 (OCR 수행)
                currentMode = "SEARCH"

                val candidates = rawDetections
                    .sortedByDescending { it.boundingBox.width * it.boundingBox.height }
                    .take(3) // 큰 거 3개만 검사

                if (candidates.isNotEmpty()) {
                    val verifiedResults = runBlocking {
                        candidates.map { detection ->
                            async(Dispatchers.Default) {
                                // 🌟 영어 텍스트 인식 실행
                                if (verifyText(detection, fullImageBitmap)) {
                                    detection
                                } else {
                                    null
                                }
                            }
                        }.awaitAll()
                    }

                    val found = verifiedResults.filterNotNull().firstOrNull()

                    if (found != null) {
                        lastTrackedBox = found.boundingBox
                        verified = true
                        finalDetections.add(found.copy(
                            className = "JCDecaux (Found!)",
                            confidence = 1.0f
                        ))
                    } else {
                        finalDetections.addAll(candidates)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("BusStopPipeline", "Error", e)
        } finally {
            fullImageBitmap.recycle()
        }

        return StopPipelineResult(finalDetections, verified, currentMode)
    }

    // 🌟 [핵심] 상단 30% 자르고 영어 OCR 수행
    private fun verifyText(detection: Detection, fullBitmap: Bitmap): Boolean {
        val box = detection.boundingBox

        // 좌표 계산 (이미지 범위 벗어나지 않게)
        val x = (box.x - box.width / 2).toInt().coerceIn(0, fullBitmap.width - 1)
        val y = (box.y - box.height / 2).toInt().coerceIn(0, fullBitmap.height - 1)
        val w = box.width.toInt().coerceAtMost(fullBitmap.width - x)

        // 🌟 높이의 30%만 자름 (상단 글씨만 보기 위해)
        val h = (box.height * 0.3).toInt().coerceAtMost(fullBitmap.height - y)

        if (w < 20 || h < 10) return false

        val croppedBitmap = Bitmap.createBitmap(fullBitmap, x, y, w, h)

        return try {
            val inputImage = InputImage.fromBitmap(croppedBitmap, 0)

            // 🌟 영어 인식기 실행
            val result = Tasks.await(textRecognizer.process(inputImage))

            // 공백 제거 및 대문자 변환
            val text = result.text.uppercase().replace("\\s".toRegex(), "")

            // 조각이 하나라도 있으면 OK
            targetFragments.any { fragment -> text.contains(fragment) }

        } catch (e: Exception) {
            false
        } finally {
            croppedBitmap.recycle()
        }
    }

    private fun calculateIoU(boxA: BoundingBox, boxB: BoundingBox): Float {
        val xA = max(boxA.x - boxA.width / 2, boxB.x - boxB.width / 2)
        val yA = max(boxA.y - boxA.height / 2, boxB.y - boxB.height / 2)
        val xB = min(boxA.x + boxA.width / 2, boxB.x + boxB.width / 2)
        val yB = min(boxA.y + boxA.height / 2, boxB.y + boxB.height / 2)
        val interArea = max(0f, xB - xA) * max(0f, yB - yA)
        if (interArea == 0f) return 0f
        val boxAArea = boxA.width * boxA.height
        val boxBArea = boxB.width * boxB.height
        return interArea / (boxAArea + boxBArea - interArea)
    }

    private fun imageProxyToRotatedBitmap(imageProxy: ImageProxy): Bitmap {
        val width = imageProxy.width
        val height = imageProxy.height
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val ySize = width * height
        if (pixels == null || pixels?.size != ySize) { pixels = IntArray(ySize) }
        val safePixels = pixels!!
        buffer.rewind()
        if (width == rowStride) {
            val tempBuffer = ByteArray(ySize)
            buffer.get(tempBuffer)
            for (i in 0 until ySize) {
                val yValue = tempBuffer[i].toInt() and 0xFF
                safePixels[i] = -0x1000000 or (yValue shl 16) or (yValue shl 8) or yValue
            }
        } else {
            val rowBuffer = ByteArray(width)
            var outputPos = 0
            for (row in 0 until height) {
                buffer.position(row * rowStride)
                buffer.get(rowBuffer, 0, width)
                for (col in 0 until width) {
                    val yValue = rowBuffer[col].toInt() and 0xFF
                    safePixels[outputPos++] = -0x1000000 or (yValue shl 16) or (yValue shl 8) or yValue
                }
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(safePixels, 0, width, 0, 0, width, height)
        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
        return if (rotation != 0f) {
            matrix.reset()
            matrix.postRotate(rotation)
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
            if (bitmap != rotated) bitmap.recycle()
            rotated
        } else {
            bitmap
        }
    }

    fun release() {
        detector.release()
        textRecognizer.close()
    }
}