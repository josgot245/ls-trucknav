package de.ls.trucknav.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.ArrayDeque
import java.util.Locale

/** Sprachausgabe fuer Fahranweisungen (deutsch, Navigations-Audiokanal). */
class SpeechPlayer(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var enabled = true
    private val pending = ArrayDeque<String>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    val result = engine.setLanguage(Locale.GERMAN)
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        engine.setLanguage(Locale.getDefault())
                    }
                    engine.setSpeechRate(1.0f)
                    engine.setPitch(1.0f)
                    engine.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) = Unit

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) = Unit
                    })
                }
                ready = true
                synchronized(pending) {
                    while (pending.isNotEmpty()) speakNow(pending.removeFirst())
                }
            }
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) tts?.stop()
    }

    fun speak(text: String) {
        if (!enabled || text.isBlank()) return
        if (ready) {
            speakNow(text)
        } else {
            synchronized(pending) {
                if (pending.size < 3) pending.addLast(text)
            }
        }
    }

    fun stop() {
        tts?.stop()
        synchronized(pending) { pending.clear() }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "nav-${System.nanoTime()}")
    }
}
