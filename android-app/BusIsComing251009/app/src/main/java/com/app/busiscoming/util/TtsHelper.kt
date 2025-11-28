package com.app.busiscoming.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private val ttsInitChannel = Channel<Boolean>(Channel.CONFLATED)

    private var isInitialized = false
    
    init {
        initializeTts()
    }
    
    private fun initializeTts() {
        tts = TextToSpeech(context) { status ->
            isInitialized = status == TextToSpeech.SUCCESS
            
            if (isInitialized) {
                tts?.language = Locale.KOREAN
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
                ttsInitChannel.trySend(true)
            } else {
                ttsInitChannel.trySend(false)
            }
        }
        
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
        })
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (isInitialized) {
            tts?.speak(text, queueMode, null, "TTS_ID")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun speakLocationLoading() {
        speak("현재 위치를 찾는 중입니다")
    }

    /**
     * 목적지 입력 확인 TTS (TalkBack과의 겹침 방지를 위해 3초 지연)
     * TalkBack이 화면 요소(라벨, EditText 등)를 읽는 시간을 확보하기 위해 지연됩니다.
     */
    suspend fun speakDestinationConfirmed(text: String) {
        delay(3000) // 3초 지연 - TalkBack이 화면 요소를 읽을 시간 확보
        speak("목적지로 ${text}가 입력되었습니다. 틀리면 화면을 더블 탭 후 목적지를 다시 말해주세요. 맞으면 화면을 오른쪽으로 쓸어서 경로 안내 시작 버튼을 찾은 뒤 화면을 두 번 탭해주세요.")
    }

    fun speakRouteListIntro(count: Int) {
        val n = if (count < 0) 0 else count
        speak("총 ${n}개의 경로를 찾았습니다. 현재 제공된 경로는 최소환승순입니다. 화면을 오른쪽으로 쓸어서 원하는 경로를 선택하신 뒤 화면을 더블탭 해주세요.")
    }
}




