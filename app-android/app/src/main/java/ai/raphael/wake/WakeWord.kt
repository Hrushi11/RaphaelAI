package ai.raphael.wake

import android.content.Context
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * On-device speech gate (Silero VAD via sherpa-onnx). It only decides *whether*
 * someone is speaking; the wake phrase itself is checked by the backend on the
 * speech burst (spec §5, revised Oct 25). Silence never leaves the phone.
 */
class VadGate(context: Context) {
    private val vad = Vad(
        assetManager = context.assets,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "vad/silero_vad.onnx",
                threshold = 0.5f,
                minSilenceDuration = 0.3f,
                minSpeechDuration = 0.2f,
                windowSize = WINDOW,
                maxSpeechDuration = 20f,
            ),
            sampleRate = 16_000,
            numThreads = 1,
            provider = "cpu",
        ),
    )
    private val window = FloatArray(WINDOW)
    private var filled = 0

    /** Feed one 20 ms PCM16 frame (mic thread). Returns true while speech is detected. */
    fun accept(frame: ByteArray): Boolean {
        for (i in 0 until frame.size / 2) {
            val s = ((frame[2 * i + 1].toInt() shl 8) or (frame[2 * i].toInt() and 0xff)).toShort()
            window[filled++] = s / 32768f
            if (filled == WINDOW) {
                vad.acceptWaveform(window.copyOf())
                filled = 0
                while (!vad.empty()) vad.pop() // we only need the live state, not segments
            }
        }
        return vad.isSpeechDetected()
    }

    fun reset() {
        vad.reset()
        filled = 0
    }

    fun release() = vad.release()

    companion object {
        private const val WINDOW = 512 // Silero works on 32 ms windows at 16 kHz
    }
}

/** The saved wake phrase. Changing it is just a config update to the backend. */
class WakeWordStore(context: Context) {
    private val prefs = context.getSharedPreferences("raphael_wake", Context.MODE_PRIVATE)

    var phrase: String
        get() = prefs.getString("phrase", DEFAULT_PHRASE) ?: DEFAULT_PHRASE
        set(v) = prefs.edit().putString("phrase", v).apply()

    companion object {
        const val DEFAULT_PHRASE = "hey raphael"
    }
}
