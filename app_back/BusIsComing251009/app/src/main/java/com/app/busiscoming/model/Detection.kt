// Detection.kt
package com.app.busiscoming.model

data class Detection(
    val classId: Int,
    val className: String,
    val confidence: Float,
    val boundingBox: BoundingBox
)

data class BoundingBox(
    val x: Float,      // center x
    val y: Float,      // center y
    val width: Float,
    val height: Float
) {
    // 좌측 상단, 우측 하단 좌표로 변환
    fun toRect(): Rect {
        return Rect(
            left = (x - width / 2).toInt(),
            top = (y - height / 2).toInt(),
            right = (x + width / 2).toInt(),
            bottom = (y + height / 2).toInt()
        )
    }
}

data class Rect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)