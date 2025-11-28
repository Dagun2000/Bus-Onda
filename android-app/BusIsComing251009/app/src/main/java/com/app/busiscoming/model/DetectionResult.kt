// DetectionResult.kt
package com.app.busiscoming.model

sealed class DetectionResult {
    data class Success(val detections: List<Detection>) : DetectionResult()
    data class Error(val message: String) : DetectionResult()
}