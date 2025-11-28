package com.app.busiscoming.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.app.busiscoming.model.Detection

class SeatDetectionPipeline(context: Context) {

    private val objectDetector = TfliteInferenceEngine(
        modelPath = "BasicYolo.tflite",
        inputSize = 640,
        confThreshold = 0.20f,
        classNames = listOf(
            "person",
            "bicycle",
            "car",
            "motorcycle",
            "airplane",
            "bus",
            "train",
            "truck",
            "boat",
            "traffic light",
            "fire hydrant",
            "stop sign",
            "parking meter",
            "bench",
            "bird",
            "cat",
            "dog",
            "horse",
            "sheep",
            "cow",
            "elephant",
            "bear",
            "zebra",
            "giraffe",
            "backpack",
            "umbrella",
            "handbag",
            "tie",
            "suitcase",
            "frisbee",
            "skis",
            "snowboard",
            "sports ball",
            "kite",
            "baseball bat",
            "baseball glove",
            "skateboard",
            "surfboard",
            "tennis racket",
            "bottle",
            "wine glass",
            "cup",
            "fork",
            "knife",
            "spoon",
            "bowl",
            "banana",
            "apple",
            "sandwich",
            "orange",
            "broccoli",
            "carrot",
            "hot dog",
            "pizza",
            "donut",
            "cake",
            "chair",
            "couch",
            "potted plant",
            "bed",
            "dining table",
            "toilet",
            "tv",
            "laptop",
            "mouse",
            "remote",
            "keyboard",
            "cell phone",
            "microwave",
            "oven",
            "toaster",
            "sink",
            "refrigerator",
            "book",
            "clock",
            "vase",
            "scissors",
            "teddy bear",
            "hair drier",
            "toothbrush"
        )
    )

    init {
        objectDetector.initialize(context)
        Log.d("SeatPipeline", "✅ 좌석(Chair) 탐지기 초기화 완료")
    }

    fun detectSeats(imageProxy: ImageProxy): List<Detection> {
        try {
            val bitmap = imageProxy.toBitmap() ?: return emptyList()

            // 1. 회전 각도 확인
            val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()

            // 2. 이미지가 돌아가 있다면 똑바로 세우기 (Matrix 활용)
            val finalBitmap = if (rotation != 0f) {
                rotateBitmap(bitmap, rotation)
            } else {
                bitmap
            }

            // 3. 똑바로 선 이미지로 추론 실행
            val allDetections = objectDetector.inferenceOnBitmap(finalBitmap)

            // 'chair'만 필터링
            return allDetections.filter {
                it.className == "chair"
            }

        } catch (e: Exception) {
            Log.e("SeatPipeline", "좌석 탐지 실패", e)
            return emptyList()
        }
        // imageProxy.close()는 Screen(호출자)에서 담당하므로 여기선 닫지 않음
    }

    // 비트맵 회전 헬퍼 함수
    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, matrix, true
        )
    }

    fun release() {
        objectDetector.release()
    }
}