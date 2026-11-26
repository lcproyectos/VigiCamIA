package com.vigicamia.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale

class AlertManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var lastAlertTime = 0L

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "CL")
                isReady = true
            }
        }
    }

    fun triggerIntruderAlert() {
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < 6000L) return
        lastAlertTime = now

        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 700)
        } catch (_: Exception) {}

        if (isReady) {
            tts?.speak("Alerta intruso detectado", TextToSpeech.QUEUE_FLUSH, null, "INTRUDER_ID")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
