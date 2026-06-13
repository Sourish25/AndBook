package com.example.andbook.reader

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsHelper(context: Context, private val onInitComplete: (Boolean) -> Unit) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onPageFinishedListener: (() -> Unit)? = null
    var onWordRangeListener: ((start: Int, end: Int) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.getDefault()
                setupListener()
                onInitComplete(true)
            } else {
                onInitComplete(false)
            }
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId == "page_tts") {
                    onPageFinishedListener?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}

            override fun onError(utteranceId: String?, errorCode: Int) {
                super.onError(utteranceId, errorCode)
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId == "page_tts") {
                    onWordRangeListener?.invoke(start, end)
                }
            }
        })
    }

    fun speak(text: String, rate: Float = 1.0f, pitch: Float = 1.0f, onFinished: () -> Unit) {
        if (!isInitialized) return
        onPageFinishedListener = onFinished
        
        tts?.apply {
            setSpeechRate(rate)
            setPitch(pitch)
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "page_tts")
            }
            speak(text, TextToSpeech.QUEUE_FLUSH, params, "page_tts")
        }
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
