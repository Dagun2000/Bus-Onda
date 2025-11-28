package com.app.busiscoming.walknavi

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
            }
        }
    }

    // ★ [추가] 지금 말하고 있는지 확인하는 기능
    val isSpeaking: Boolean
        get() = tts?.isSpeaking ?: false

    // 기존 speak 함수 수정: 급한 메시지(isUrgent=true)는 끊고 말하기
    fun speak(text: String, isUrgent: Boolean = false) {
        val queueMode = if (isUrgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, queueMode, null, null)
    }

    fun shutdown() {
        tts?.shutdown()
    }
}