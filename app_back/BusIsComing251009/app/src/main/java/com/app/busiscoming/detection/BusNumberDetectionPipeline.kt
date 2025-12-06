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
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.collections.get
import kotlin.math.max
import kotlin.math.min
import kotlin.text.get

data class PipelineStats(
    val preprocessMs: Long = 0,
    val inferenceMs: Long = 0,
    val ocrMs: Long = 0,
    val totalMs: Long = 0,
    val isNpuEnabled: Boolean = false,
    val mode: String = "SEARCH"
)

data class PipelineResult(
    val detections: List<Detection>,
    val stats: PipelineStats,
    val targetFound: Boolean = false
)

class BusNumberDetectionPipeline(context: Context) {

    // 1단계: 버스 차체 감지
    private val busDetector = TfliteInferenceEngine(
        modelPath = "BasicYolo.tflite",
        inputSize = 640,
        confThreshold = 0.35f,
        classNames = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
            "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard",
            "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase",
            "scissors", "teddy bear", "hair drier", "toothbrush"
        )
    )

    // 2단계: 번호판 감지
    private val plateDetector = TfliteInferenceEngine(
        modelPath = "BusDetector.tflite",
        inputSize = 640,
        confThreshold = 0.01f,
        classNames = listOf("Number_Plate")
    )

    private val textRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private var pixels: IntArray? = null
    private val matrix = Matrix()

    // 트래킹 변수
    private var lastTrackedBusBox: BoundingBox? = null
    private val IOU_THRESHOLD = 0.3f

    init {
        busDetector.initialize(context)
        plateDetector.initialize(context)
    }

    fun detectBusAndNumber(imageProxy: ImageProxy, targetNumber: String?): PipelineResult {
        val finalDetections = mutableListOf<Detection>()
        var fullImageBitmap: Bitmap? = null

        val tStart = System.currentTimeMillis()
        var tPreprocessEnd = 0L
        var tInferenceEnd = 0L
        var tOcrEnd = 0L
        var isTargetFound = false
        var currentMode = "SEARCH"

        // 타겟 번호가 없으면 추적 정보 리셋
        if (targetNumber.isNullOrEmpty()) {
            lastTrackedBusBox = null
        }

        try {
            // 1. [전처리]
            fullImageBitmap = imageProxyToRotatedBitmap(imageProxy)
            tPreprocessEnd = System.currentTimeMillis()

            // 2. [버스 감지]
            val busRawDetections = busDetector.inferenceOnBitmap(fullImageBitmap)
            val allBuses = busRawDetections.filter { it.classId == 5 } // Bus

            // 🌟 [분기점] 트래킹 모드 vs 검색 모드
            if (targetNumber != null && lastTrackedBusBox != null) {
                currentMode = "TRACKING"

                // [추적 모드] IoU를 사용하여 이전에 찾은 버스와 같은 버스 찾기
                val bestMatchBus = allBuses
                    .map { bus -> Pair(bus, calculateIoU(bus.boundingBox, lastTrackedBusBox!!)) }
                    .filter { it.second > IOU_THRESHOLD }
                    .maxByOrNull { it.second }

                if (bestMatchBus != null) {
                    val matchedBus = bestMatchBus.first
                    lastTrackedBusBox = matchedBus.boundingBox // 위치 업데이트
                    isTargetFound = true

                    finalDetections.add(
                        matchedBus.copy(
                            className = "Target: $targetNumber",
                            confidence = 1.0f
                        )
                    )
                } else {
                    // 추적 실패 -> 검색 모드로 리셋
                    lastTrackedBusBox = null
                    currentMode = "LOST -> SEARCH"
                }

                tInferenceEnd = System.currentTimeMillis()
                tOcrEnd = tInferenceEnd

            } else {
                // [검색 모드] OCR 수행
                currentMode = "SEARCH"

                val targetBuses = allBuses
                    .sortedByDescending { it.boundingBox.width * it.boundingBox.height }
                    .take(2)

                if (targetBuses.isNotEmpty()) {
                    val detectionResults = runBlocking {
                        targetBuses.map { bus ->
                            async(Dispatchers.Default) {
                                processSingleBus(bus, fullImageBitmap!!)
                            }
                        }.awaitAll()
                    }

                    detectionResults.forEach { results ->
                        if (!targetNumber.isNullOrEmpty()) {
                            // 🌟 수정된 부분: 정확한 일치 대신 isFuzzyMatch 사용
                            val foundTarget = results.find {
                                it.classId == 999 && isFuzzyMatch(it.className, targetNumber)
                            }

                            if (foundTarget != null) {
                                // 타겟 발견! 해당 버스 추적 시작
                                val parentBus = results.firstOrNull { it.className == "Bus" }
                                if (parentBus != null) {
                                    lastTrackedBusBox = parentBus.boundingBox
                                    isTargetFound = true

                                    // 화면엔 찾은 버스만 표시 (선택사항)
                                    finalDetections.addAll(results)
                                }
                            } else {
                                finalDetections.addAll(results)
                            }
                        } else {
                            finalDetections.addAll(results)
                        }
                    }
                }
                tInferenceEnd = System.currentTimeMillis()
                tOcrEnd = System.currentTimeMillis()
            }

        } catch (e: Exception) {
            Log.e("BusPipeline", "에러", e)
        } finally {
            fullImageBitmap?.recycle()
        }

        val preprocessTime = tPreprocessEnd - tStart
        val inferenceTime = tInferenceEnd - tPreprocessEnd
        val ocrTime = if (tOcrEnd > tInferenceEnd) (tOcrEnd - tInferenceEnd) else 0
        val totalTime = System.currentTimeMillis() - tStart

        return PipelineResult(
            detections = finalDetections,
            stats = PipelineStats(preprocessTime, inferenceTime, ocrTime, totalTime, busDetector.isNpuEnabled, currentMode),
            targetFound = isTargetFound
        )
    }

    /**
     * 🌟 [추가됨] 번호판 유연한 매칭 (Fuzzy Matching)
     * 예: target="5511" 일 때, "551", "511", "5511" 모두 True 반환
     */
    private fun isFuzzyMatch(detected: String, target: String): Boolean {
        // 1. 정확히 일치
        if (detected == target) return true

        // 2. 타겟 번호가 3자리 이상일 때만 유연성 적용 (짧은 번호는 오인식 위험)
        if (target.length >= 3) {
            // 인식된 번호가 타겟보다 1자리까지만 짧아도 허용
            if (detected.length >= target.length - 1) {
                // (1) 부분 문자열 매칭 (앞뒤가 잘린 경우: 5511 -> 551)
                if (target.contains(detected)) return true

                // (2) 한 글자 오타 허용 (5511 -> 5571) - 필요시 활성화
                /*
                var matchCount = 0
                val length = min(detected.length, target.length)
                for (i in 0 until length) {
                    if (detected[i] == target[i]) matchCount++
                }
                if (matchCount >= target.length - 1) return true
                */
            }
        }
        return false
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

    private fun processSingleBus(targetBus: Detection, fullImageBitmap: Bitmap): List<Detection> {
        val results = mutableListOf<Detection>()
        results.add(targetBus.copy(className = "Bus"))

        val busBox = targetBus.boundingBox
        if (busBox.width <= 30 || busBox.height <= 30) return results

        val busCropX = (busBox.x - busBox.width / 2).toInt().coerceIn(0, fullImageBitmap.width - 1)
        val busCropY = (busBox.y - busBox.height / 2).toInt().coerceIn(0, fullImageBitmap.height - 1)
        val busCropW = busBox.width.toInt().coerceAtMost(fullImageBitmap.width - busCropX)
        val busCropH = busBox.height.toInt().coerceAtMost(fullImageBitmap.height - busCropY)
        if (busCropW <= 0 || busCropH <= 0) return results

        val busBitmap = Bitmap.createBitmap(fullImageBitmap, busCropX, busCropY, busCropW, busCropH)

        val plateDetections = synchronized(plateDetector) {
            plateDetector.inferenceOnBitmap(busBitmap)
        }

        val validPlates = plateDetections.filter { plate ->
            val cy = plate.boundingBox.y
            val isTopHalf = if (cy < 1.0f) cy < 0.5f else (cy / busBitmap.height.toFloat()) < 0.5f
            isTopHalf
        }
        val targetPlate = validPlates.maxByOrNull { it.boundingBox.width * it.boundingBox.height }

        if (targetPlate != null) {
            val plateBox = targetPlate.boundingBox
            val globalPlateCenterX = busCropX + plateBox.x
            val globalPlateCenterY = busCropY + plateBox.y

            results.add(targetPlate.copy(className = "Plate",
                boundingBox = BoundingBox(globalPlateCenterX, globalPlateCenterY, plateBox.width, plateBox.height)))

            val padXRatio = 3.0f; val padYRatio = 0.8f
            val pPadX = plateBox.width * padXRatio; val pPadY = plateBox.height * padYRatio
            val pCropX = (globalPlateCenterX - plateBox.width/2 - pPadX).toInt().coerceIn(0, fullImageBitmap.width - 1)
            val pCropY = (globalPlateCenterY - plateBox.height/2 - pPadY).toInt().coerceIn(0, fullImageBitmap.height - 1)
            val pCropW = (plateBox.width + pPadX*2).toInt().coerceAtMost(fullImageBitmap.width - pCropX)
            val pCropH = (plateBox.height + pPadY*2).toInt().coerceAtMost(fullImageBitmap.height - pCropY)

            if (pCropW > 0 && pCropH > 0) {
                var plateBitmap = Bitmap.createBitmap(fullImageBitmap, pCropX, pCropY, pCropW, pCropH)
                if (plateBitmap.width < 120) {
                    val scale = 120f / plateBitmap.width
                    plateBitmap = Bitmap.createScaledBitmap(plateBitmap, (plateBitmap.width*scale).toInt(), (plateBitmap.height*scale).toInt(), true)
                }

                val bestNumber = runSmartOcr(plateBitmap)
                if (bestNumber.isNotEmpty()) {
                    results.add(Detection(classId = 999, className = bestNumber, confidence = 1.0f,
                        boundingBox = BoundingBox(globalPlateCenterX, globalPlateCenterY, plateBox.width, plateBox.height)))
                }
                plateBitmap.recycle()
            }
        }
        busBitmap.recycle()
        return results
    }

    private fun runSmartOcr(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = Tasks.await(textRecognizer.process(image))
            val candidates = result.textBlocks.flatMap { block ->
                block.lines.map { line -> line.text.filter { it.isDigit() } }
            }.filter { it.length >= 2 }
            if (candidates.isEmpty()) return ""
            candidates.sortedWith(
                compareByDescending<String> { it.length == 3 || it.length == 4 }
                    .thenByDescending { it.length == 2 }.thenByDescending { it.length }
            ).first()
        } catch (e: Exception) { "" }
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
        busDetector.release()
        plateDetector.release()
        textRecognizer.close()
    }
}