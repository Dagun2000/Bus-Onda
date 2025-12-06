// DetectionPipeline.kt
package com.app.busiscoming.detection

import android.content.Context
import androidx.camera.core.ImageProxy
import com.app.busiscoming.model.DetectionResult

interface DetectionPipeline {
    fun initialize(context: Context)
    fun processFrame(imageProxy: ImageProxy): DetectionResult
    fun release()
}