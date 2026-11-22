package app.kaori.reader.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Reading na głos systemowym syntezatorem.
 *
 * Świadomie bez wbudowanych modeli głosu: silnik systemowy nie waży nic w APK,
 * działa offline i nie wymaga licencji na niczyj głos.
 */
class TtsPlayer(context: Context) {

    private var tts: TextToSpeech? = null
    private var onDone: (() -> Unit)? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    @Volatile var ready: Boolean = false
        private set

    @Volatile var speaking: Boolean = false
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    speaking = true
                }

                override fun onDone(utteranceId: String?) {
                    speaking = false
                    val next = onDone
                    onDone = null
                    next?.let { main.post(it) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    speaking = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    speaking = false
                }
            })
        }
    }

    fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
    }

    /** Czyta [text]; po zakończeniu woła [onFinished] (np. żeby przewrócić stronę). */
    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        val engine = tts ?: return
        if (!ready || text.isBlank()) return
        engine.language = localeFor(text)
        onDone = onFinished
        speaking = true
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kaori-${System.currentTimeMillis()}")
    }

    /**
     * Język dobierany z tekstu: polski interfejs nie może czytać angielskiej książki
     * polskim głosem (brzmi jak parodia). Diakrytyki to wystarczający wskaźnik.
     */
    private fun localeFor(text: String): Locale {
        var pl = 0
        for (c in text) {
            if (c in "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ") pl++
            if (pl > 2) return Locale.forLanguageTag("pl-PL")
        }
        return Locale.US
    }

    fun stop() {
        onDone = null
        speaking = false
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
        speaking = false
    }
}
