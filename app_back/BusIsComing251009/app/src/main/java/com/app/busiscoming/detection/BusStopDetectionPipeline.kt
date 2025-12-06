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
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.collections.get
import kotlin.math.max
import kotlin.math.min
import kotlin.text.get

data class StopPipelineResult(
    val detections: List<Detection>,
    val isVerifiedStation: Boolean,
    val mode: String
)

class BusStopDetectionPipeline(context: Context) {

    // 🌟 [수정 1] 이 변수 하나로 OCR 기능을 켜고 끕니다. (외부에서 변경 가능)
    // true: YOLO -> OCR 검증 -> 결과 (기존 로직)
    // false: YOLO -> 결과 (OCR 생략)
    var enableOcrVerification: Boolean = false

    private val detector = TfliteInferenceEngine(
        modelPath = "BusStation.tflite",
        inputSize = 640,
        confThreshold = 0.30f,
        classNames = listOf("Busstation")
    )

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var pixels: IntArray? = null
    private val matrix = Matrix()
    private var lastTrackedBox: BoundingBox? = null
    private val IOU_THRESHOLD = 0.4f
    private val targetFragments = listOf("JC", "CD", "DE", "EC", "CA", "AU", "UX")

    init {
        detector.initialize(context)
        Log.d("BusStopPipeline", "✅ 정류장 감지 파이프라인 초기화 완료")
    }

    fun detect(imageProxy: ImageProxy): StopPipelineResult {
        val finalDetections = mutableListOf<Detection>()
        var verified = false
        var currentMode = "SEARCH"

        val fullImageBitmap = imageProxyToRotatedBitmap(imageProxy)

        try {
            // 1. YOLO로 광고판 후보 찾기
            val rawDetections = detector.inferenceOnBitmap(fullImageBitmap)

            // 2. 추적 로직 (이미 찾았으면 OCR 안함 - 트래킹 우선)
            if (lastTrackedBox != null) {
                currentMode = "TRACKING"
                val bestMatch = rawDetections
                    .map { detection -> Pair(detection, calculateIoU(detection.boundingBox, lastTrackedBox!!)) }
                    .filter { it.second > IOU_THRESHOLD }
                    .maxByOrNull { it.second }

                if (bestMatch != null) {
                    val (detection, _) = bestMatch
                    lastTrackedBox = detection.boundingBox
                    verified = true // 트래킹 중에는 검증된 것으로 간주
                    finalDetections.add(detection.copy(
                        className = "JCDecaux (Tracked)",
                        confidence = 1.0f
                    ))
                } else {
                    lastTrackedBox = null
                    currentMode = "LOST"
                }

            } else {
                // 3. 검색 로직 (OCR 수행 여부 분기)
                currentMode = "SEARCH"

                val candidates = rawDetections
                    .sortedByDescending { it.boundingBox.width * it.boundingBox.height }
                    .take(3)

                if (candidates.isNotEmpty()) {

                    // 🌟 [수정 2] 변수 값에 따라 로직 분기
                    if (enableOcrVerification) {
                        // === [Case A: OCR 켜짐] 기존 로직 수행 ===
                        val verifiedResults = runBlocking {
                            candidates.map { detection ->
                                async(Dispatchers.Default) {
                                    if (verifyText(detection, fullImageBitmap)) detection else null
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
                            // OCR 켰는데 실패하면, 그냥 후보군을 보여줄지 말지는 선택사항 (여기선 기존대로 후보군 노출)
                            finalDetections.addAll(candidates)
                        }

                    } else {
                        // === [Case B: OCR 꺼짐] YOLO 결과 즉시 승인 ===
                        // OCR 검증 없이 YOLO가 찾은 것 중 가장 큰 것을 바로 정답 처리하거나, 후보군 전체 리턴
                        // 여기서는 가장 큰 후보를 바로 트래킹 대상으로 잡습니다.
                        val bestCandidate = candidates.first()

                        lastTrackedBox = bestCandidate.boundingBox
                        verified = true // OCR은 안 했지만 찾은 것으로 처리

                        finalDetections.add(bestCandidate.copy(
                            className = "BusStation (No OCR)", // 구분하기 쉽게 이름 변경
                            confidence = bestCandidate.confidence
                        ))
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

    // ... (verifyText, calculateIoU, imageProxyToRotatedBitmap 등 나머지 코드는 동일) ...
    private fun verifyText(detection: Detection, fullBitmap: Bitmap): Boolean {
        // ... 기존 verifyText 코드 유지 ...
        // (생략: 위 코드와 동일하게 두시면 됩니다)
        val box = detection.boundingBox
        val x = (box.x - box.width / 2).toInt().coerceIn(0, fullBitmap.width - 1)
        val y = (box.y - box.height / 2).toInt().coerceIn(0, fullBitmap.height - 1)
        val w = box.width.toInt().coerceAtMost(fullBitmap.width - x)
        val h = (box.height * 0.3).toInt().coerceAtMost(fullBitmap.height - y)
        if (w < 20 || h < 10) return false
        val croppedBitmap = Bitmap.createBitmap(fullBitmap, x, y, w, h)
        return try {
            val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
            val result = Tasks.await(textRecognizer.process(inputImage))
            val text = result.text.uppercase().replace("\\s".toRegex(), "")
            targetFragments.any { fragment -> text.contains(fragment) }
        } catch (e: Exception) { false } finally { croppedBitmap.recycle() }
    }

    // ... 나머지 함수들 (calculateIoU, imageProxyToRotatedBitmap, release) 동일 ...
    private fun calculateIoU(boxA: BoundingBox, boxB: BoundingBox): Float {
        // ... (생략) ...
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
        // ... (생략: 위 코드와 동일) ...
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