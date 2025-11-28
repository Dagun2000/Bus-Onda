package com.app.busiscoming.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.app.busiscoming.model.BoundingBox
import com.app.busiscoming.model.Detection
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate // 2.16.1 호환
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.collections.get
import kotlin.math.max
import kotlin.math.min

class TfliteInferenceEngine(
    private val modelPath: String,
    private val inputSize: Int = 640,
    private val confThreshold: Float = 0.25f,
    private val iouThreshold: Float = 0.45f,
    private val classNames: List<String> = listOf("Bus")
) {

    private var interpreter: Interpreter? = null
    private var inputBuffer: ByteBuffer? = null
    private lateinit var outputBuffer: Array<Array<FloatArray>>
    private var gpuDelegate: GpuDelegate? = null

    // 외부 확인용
    var isNpuEnabled: Boolean = false
        private set

    private var isOutputChannelLast = false
    private var numAnchors = 0
    private var numElements = 0

    fun initialize(context: Context) {
        try {
            val model = FileUtil.loadMappedFile(context, modelPath)

            // 🌟 [2.16.1 방식] 복잡한 호환성 체크 제거하고 바로 GPU 옵션 생성
            val options = Interpreter.Options()
            var activeDevice = "CPU"

            try {
                // 최신 폰 + 최신 버전 라이브러리 조합이므로 기본 생성자가 가장 잘 먹힙니다.
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)

                activeDevice = "GPU"
                isNpuEnabled = true
                Log.d("TfliteEngine", "🔥 GPU 가속 활성화 (v2.16.1)")
            } catch (e: Throwable) {
                // GPU 실패 시 CPU Fallback
                Log.e("TfliteEngine", "⚠️ GPU 실패 -> CPU 전환", e)
                isNpuEnabled = false
                options.setNumThreads(4)
                activeDevice = "CPU"
            }

            interpreter = Interpreter(model, options)

            Log.i("TfliteEngine", "🚀 구동 모드: $activeDevice")

            // --- 아래는 기존 로직(전처리 등) 그대로 유지 ---

            inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
            inputBuffer?.order(ByteOrder.nativeOrder())

            val outputTensor = interpreter!!.getOutputTensor(0)
            val shape = outputTensor.shape()
            // [1, 8400, 84] or [1, 84, 8400] 처리
            val dim1 = shape[1]
            val dim2 = shape[2]

            if (dim1 > dim2) {
                numAnchors = dim1
                numElements = dim2
                isOutputChannelLast = true
                outputBuffer = Array(1) { Array(numAnchors) { FloatArray(numElements) } }
            } else {
                numAnchors = dim2
                numElements = dim1
                isOutputChannelLast = false
                outputBuffer = Array(1) { Array(numElements) { FloatArray(numAnchors) } }
            }

        } catch (e: Exception) {
            Log.e("TfliteEngine", "초기화 실패", e)
        }
    }

    fun inferenceOnBitmap(bitmap: Bitmap): List<Detection> {
        if (interpreter == null) return emptyList()

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        inputBuffer?.rewind()
        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        // 🌟 [수정됨] 흑백 변환 제거 -> RGB 그대로 넣기 (단, 255.0f로 나누기는 유지)
        for (pixelValue in intValues) {
            // Android의 Bitmap Color 순서는 보통 ARGB입니다.
            val r = (pixelValue shr 16) and 0xFF
            val g = (pixelValue shr 8) and 0xFF
            val b = pixelValue and 0xFF

            // YOLO는 학습할 때 0~1 사이의 실수(Float)를 봅니다.
            // 따라서 255.0f로 나누는 '정규화(Normalization)'는 필수입니다.
            inputBuffer?.putFloat(r / 255.0f)
            inputBuffer?.putFloat(g / 255.0f)
            inputBuffer?.putFloat(b / 255.0f)
        }

        if (bitmap != resizedBitmap) resizedBitmap.recycle()

        interpreter?.run(inputBuffer, outputBuffer)

        val rawDetections = parseOutput(outputBuffer)

        // 좌표 스케일링 (640x640 -> 원본 해상도)
        val scaleX = bitmap.width.toFloat() / inputSize.toFloat()
        val scaleY = bitmap.height.toFloat() / inputSize.toFloat()

        return rawDetections.map { detection ->
            val box = detection.boundingBox
            detection.copy(
                boundingBox = BoundingBox(
                    x = box.x * scaleX,
                    y = box.y * scaleY,
                    width = box.width * scaleX,
                    height = box.height * scaleY
                )
            )
        }
    }

    // ... (parseOutput, nms, calculateIoU는 기존과 동일하므로 생략, 그대로 두세요) ...
    private fun parseOutput(output: Array<Array<FloatArray>>): List<Detection> {
        val detections = ArrayList<Detection>()

        for (i in 0 until numAnchors) {
            var cx: Float
            var cy: Float
            var w: Float
            var h: Float
            var maxScore = 0f
            var classId = -1

            if (isOutputChannelLast) {
                for (c in 4 until numElements) {
                    val score = output[0][i][c]
                    if (score > maxScore) {
                        maxScore = score
                        classId = c - 4
                    }
                }
                if (maxScore > confThreshold) {
                    cx = output[0][i][0]
                    cy = output[0][i][1]
                    w = output[0][i][2]
                    h = output[0][i][3]
                } else {
                    continue
                }
            } else {
                for (c in 4 until numElements) {
                    val score = output[0][c][i]
                    if (score > maxScore) {
                        maxScore = score
                        classId = c - 4
                    }
                }
                if (maxScore > confThreshold) {
                    cx = output[0][0][i]
                    cy = output[0][1][i]
                    w = output[0][2][i]
                    h = output[0][3][i]
                } else {
                    continue
                }
            }

            val finalCx = if (cx > 1.0f) cx else cx * inputSize
            val finalCy = if (cy > 1.0f) cy else cy * inputSize
            val finalW = if (w > 1.0f) w else w * inputSize
            val finalH = if (h > 1.0f) h else h * inputSize

            detections.add(
                Detection(
                    classId = classId,
                    className = classNames.getOrElse(classId) { "Unknown" },
                    confidence = maxScore,
                    boundingBox = BoundingBox(finalCx, finalCy, finalW, finalH)
                )
            )
        }

        return nms(detections)
    }

    private fun nms(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }
        val selected = ArrayList<Detection>()
        val active = BooleanArray(sorted.size) { true }

        for (i in sorted.indices) {
            if (!active[i]) continue
            val boxA = sorted[i]
            selected.add(boxA)

            for (j in i + 1 until sorted.size) {
                if (!active[j]) continue
                val boxB = sorted[j]

                if (calculateIoU(boxA.boundingBox, boxB.boundingBox) > iouThreshold) {
                    active[j] = false
                }
            }
        }
        return selected
    }

    private fun calculateIoU(boxA: BoundingBox, boxB: BoundingBox): Float {
        val leftA = boxA.x - boxA.width / 2
        val topA = boxA.y - boxA.height / 2
        val rightA = boxA.x + boxA.width / 2
        val bottomA = boxA.y + boxA.height / 2

        val leftB = boxB.x - boxB.width / 2
        val topB = boxB.y - boxB.height / 2
        val rightB = boxB.x + boxB.width / 2
        val bottomB = boxB.y + boxB.height / 2

        val xA = max(leftA, leftB)
        val yA = max(topA, topB)
        val xB = min(rightA, rightB)
        val yB = min(bottomA, bottomB)

        val interArea = max(0f, xB - xA) * max(0f, yB - yA)
        val boxAArea = boxA.width * boxA.height
        val boxBArea = boxB.width * boxB.height

        return interArea / (boxAArea + boxBArea - interArea)
    }

    fun release() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }
}