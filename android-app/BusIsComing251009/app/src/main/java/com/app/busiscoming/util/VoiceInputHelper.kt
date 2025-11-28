package com.app.busiscoming.util

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.ActivityResult

object VoiceInputHelper {
    fun buildKoreanFreeFormIntent(prompt: String? = null): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (prompt != null) putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
    }

    fun parseResult(result: ActivityResult): String? {
        val data = result.data ?: return null
        val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        return results?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }
}


