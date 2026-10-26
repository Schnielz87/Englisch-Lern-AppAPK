package de.englischlernen.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import de.englischlernen.app.data.prefs.Accent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Sprachausgabe ueber die Android-TTS-Engine (funktioniert offline, wenn Sprachdaten installiert sind). */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    enum class Status { LOADING, READY, MISSING_DATA, UNAVAILABLE }

    private val _status = MutableStateFlow(Status.LOADING)
    val status: StateFlow<Status> = _status

    private var rate = 0.9f
    private var accent = Accent.US
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) {
            _status.value = Status.UNAVAILABLE
            return
        }
        applyLanguage()
    }

    private fun applyLanguage() {
        val locale = if (accent == Accent.GB) Locale.UK else Locale.US
        val r = tts.setLanguage(locale)
        _status.value = if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Notfalls allgemeines Englisch versuchen.
            val fallback = tts.setLanguage(Locale.ENGLISH)
            if (fallback >= TextToSpeech.LANG_AVAILABLE) Status.READY else Status.MISSING_DATA
        } else Status.READY
        tts.setSpeechRate(rate)
    }

    fun configure(rate: Float, accent: Accent) {
        this.rate = rate
        val changed = this.accent != accent
        this.accent = accent
        if (_status.value == Status.READY || _status.value == Status.MISSING_DATA) {
            if (changed) applyLanguage() else tts.setSpeechRate(rate)
        }
    }

    fun speak(text: String, slow: Boolean = false) {
        if (_status.value != Status.READY) return
        tts.setSpeechRate(if (slow) rate * 0.6f else rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun stop() = tts.stop()
}
