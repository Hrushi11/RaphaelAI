package ai.raphael.service

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.util.Log
import ai.raphael.audio.AudioRouter
import ai.raphael.audio.MicStream
import ai.raphael.audio.ReplyPlayer
import ai.raphael.audio.label
import ai.raphael.net.Msg
import ai.raphael.net.SessionClient
import ai.raphael.wake.VadGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue

/** CHECKING = speech heard, backend is checking for the wake phrase (nothing shown yet). */
enum class TurnState { IDLE, CHECKING, LISTENING, THINKING, SPEAKING }
enum class Link { DISCONNECTED, CONNECTING, READY }
enum class MicMode { HEADSET, PHONE }

data class UiState(
    val link: Link = Link.DISCONNECTED,
    val turn: TurnState = TurnState.IDLE,
    val route: String = "",
    val heard: String = "",
    val answer: String = "",
    val timings: String = "",
    val error: String = "",
    val handsFree: Boolean = true,
    val wakePhrase: String = "",
    val wakes: List<String> = emptyList(),
    val ignoredBursts: Int = 0,
    val paused: Boolean = false,       // user paused/stopped Raphael (mic off on purpose)
    val deviceName: String = "",       // shown in the top pill, e.g. "Nord Buds 2"
    val answeredMs: Int? = null,       // end of question → first audio, for "answered in 1.4 s"
)

/**
 * Phone-side turn state machine (spec §3.5).
 *
 * Triggers: on-screen tap, or hands-free: the on-device VAD hears speech, the
 * burst is streamed to the backend, which keeps the turn only if it starts with
 * the wake phrase (wake.detected) and drops it otherwise (turn.dropped no_wake).
 *
 * All state changes happen on [scope] (Main). The mic thread only reads volatile flags.
 */
class TurnController(
    private val router: AudioRouter,
    private val scope: CoroutineScope,
    private val vad: VadGate,
) {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private var client: SessionClient? = null
    private var mic: MicStream? = null
    private var player: ReplyPlayer? = null
    private var eventsJob: Job? = null
    private var turnId: String? = null
    private var mode = MicMode.HEADSET

    @Volatile
    private var streamingTurn: String? = null

    // Hands-free gate state (read on the mic thread).
    @Volatile
    private var vadArmed = false

    @Volatile
    private var vadResetPending = true

    @Volatile
    private var waitForSilence = false // after a dropped burst, ignore the rest of that utterance

    // Audio from just before a trigger, sent first so the start of the phrase isn't clipped.
    // 700 ms: the VAD needs ~200-400 ms of speech before it fires.
    private val preroll = ArrayBlockingQueue<ByteArray>(PREROLL_FRAMES)
    private val chime = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 50)

    // Live loudness for the waveform / diamond (0..1).
    @Volatile
    private var micLevel = 0f
    fun micLevel(): Float = if (mic != null) micLevel else 0f
    fun playLevel(): Float = player?.level ?: 0f

    fun setPaused(paused: Boolean) = _ui.update { it.copy(paused = paused) }

    /** Abandon the current turn without an answer (e.g. the wake word test). */
    fun cancelTurn() {
        val c = client ?: return
        turnId?.let { c.send(Msg.turnCancel(it)) }
        streamingTurn = null
        player?.flush()
        toIdle()
    }

    init {
        scope.launch {
            ui.collect { s ->
                val armed = s.handsFree && s.link == Link.READY && s.turn == TurnState.IDLE
                if (armed && !vadArmed) vadResetPending = true
                vadArmed = armed
            }
        }
    }

    fun setWakePhrase(phrase: String) {
        _ui.update { it.copy(wakePhrase = phrase) }
        client?.send(Msg.configUpdate(config()))
    }

    fun setHandsFree(on: Boolean) = _ui.update { it.copy(handsFree = on) }

    fun connect(url: String, mode: MicMode, headset: AudioDeviceInfo?) {
        if (_ui.value.link != Link.DISCONNECTED) return
        this.mode = mode
        val name = if (mode == MicMode.PHONE) "Phone mic" else shortName(headset?.productName?.toString())
        _ui.update {
            it.copy(link = Link.CONNECTING, error = "", heard = "", answer = "", timings = "", paused = false, deviceName = name)
        }
        scope.launch {
            // 1. Audio route. Held open while connected so a trigger starts capture instantly.
            val routeLabel = if (mode == MicMode.HEADSET) {
                if (headset == null || !router.routeTo(headset)) {
                    fail("Couldn't open the headset voice link. Try Phone mic mode.")
                    return@launch
                }
                headset.label()
            } else {
                "phone mic, reply as media audio"
            }
            _ui.update { it.copy(route = routeLabel) }

            // 2. Mic + player.
            val source = if (mode == MicMode.HEADSET) MediaRecorder.AudioSource.VOICE_COMMUNICATION
            else MediaRecorder.AudioSource.MIC
            player = ReplyPlayer(
                if (mode == MicMode.HEADSET) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
            )
            mic = MicStream().also { it.start(source, ::onMicFrame) }

            // 3. Backend.
            val c = SessionClient()
            client = c
            eventsJob = scope.launch { for (ev in c.events) onEvent(ev) }
            c.connect(url)
        }
    }

    fun disconnect() {
        eventsJob?.cancel()
        client?.close()
        mic?.stop()
        player?.release()
        router.release()
        client = null; mic = null; player = null; streamingTurn = null; turnId = null
        preroll.clear()
        _ui.update { it.copy(link = Link.DISCONNECTED, turn = TurnState.IDLE) }
    }

    /** The one button (later also the earbud tap): start, finish, or interrupt. */
    fun tap() {
        val c = client ?: return
        if (_ui.value.link != Link.READY) return
        when (_ui.value.turn) {
            TurnState.IDLE -> startTurn(c, "tap", chimeNow = true)
            TurnState.CHECKING -> { // user didn't wait for the wake check: make it a tap turn
                turnId?.let { c.send(Msg.turnCancel(it)) }
                streamingTurn = null
                startTurn(c, "tap", chimeNow = true)
            }
            TurnState.LISTENING -> turnId?.let { c.send(Msg.turnEnd(it)) }
            TurnState.THINKING, TurnState.SPEAKING -> {
                turnId?.let { c.send(Msg.turnCancel(it)) }
                streamingTurn = null
                player?.flush()
                toIdle()
            }
        }
    }

    private fun startTurn(c: SessionClient, trigger: String, chimeNow: Boolean) {
        val id = UUID.randomUUID().toString().take(8)
        turnId = id
        if (chimeNow) chime.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
        val frames = preroll.toList()
        preroll.clear()
        c.send(Msg.turnStart(id, trigger, frames.size * 20))
        frames.forEach(c::sendAudio)
        streamingTurn = id
        val state = if (trigger == "vad") TurnState.CHECKING else TurnState.LISTENING
        _ui.update {
            if (state == TurnState.CHECKING) it.copy(turn = state)
            else it.copy(turn = state, heard = "", answer = "", timings = "", error = "", answeredMs = null)
        }
    }

    private fun onSpeechStart() {
        val c = client ?: return
        if (_ui.value.link == Link.READY && _ui.value.turn == TurnState.IDLE && _ui.value.handsFree) {
            startTurn(c, "vad", chimeNow = false)
        }
    }

    // Runs on the mic thread. Nothing leaves the phone until a trigger sets streamingTurn.
    private fun onMicFrame(frame: ByteArray) {
        micLevel = ai.raphael.audio.rms(frame)
        if (streamingTurn != null) {
            client?.sendAudio(frame)
            return
        }
        if (!preroll.offer(frame)) {
            preroll.poll()
            preroll.offer(frame)
        }
        if (!vadArmed) return
        if (vadResetPending) {
            vad.reset()
            vadResetPending = false
        }
        val speech = vad.accept(frame)
        if (waitForSilence) {
            if (!speech) waitForSilence = false
        } else if (speech) {
            waitForSilence = true // one burst per utterance
            scope.launch { onSpeechStart() }
        }
    }

    private fun onEvent(ev: SessionClient.Event) {
        when (ev) {
            SessionClient.Event.Open -> client?.send(Msg.sessionStart(config()))
            is SessionClient.Event.Closed -> {
                if (_ui.value.link != Link.DISCONNECTED) {
                    disconnect()
                    _ui.update { it.copy(error = "Connection lost: ${ev.reason}") }
                }
            }
            is SessionClient.Event.Audio -> {
                if (_ui.value.turn == TurnState.SPEAKING) player?.write(ev.pcm)
            }
            is SessionClient.Event.Message -> onMessage(ev.json)
        }
    }

    private fun onMessage(m: JSONObject) {
        val type = m.optString("type")
        val forTurn = m.optString("turn_id", "")
        if (forTurn.isNotEmpty() && forTurn != turnId) return // stale turn
        when (type) {
            "session.ready" -> _ui.update { it.copy(link = Link.READY) }
            "wake.detected" -> {
                chime.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                _ui.update {
                    it.copy(turn = TurnState.LISTENING, heard = "", answer = "", timings = "", error = "", answeredMs = null,
                        wakes = (listOf("$time  “${m.optString("phrase")}”") + it.wakes).take(5))
                }
            }
            "transcript.partial" -> _ui.update { it.copy(heard = m.getString("text")) }
            "transcript.final" -> {
                streamingTurn = null
                _ui.update { it.copy(turn = TurnState.THINKING, heard = m.getString("text")) }
            }
            "reply.text" -> _ui.update { it.copy(answer = it.answer + m.getString("delta")) }
            "reply.audio_start" -> _ui.update { it.copy(turn = TurnState.SPEAKING) }
            "reply.done" -> {
                val t = m.optJSONObject("timings")
                _ui.update {
                    it.copy(timings = formatTimings(t), answeredMs = t?.optInt("total_ms", -1)?.takeIf { ms -> ms > 0 })
                }
                val id = turnId
                scope.launch {
                    while (player?.isDrained() == false && turnId == id && _ui.value.turn == TurnState.SPEAKING) delay(50)
                    if (turnId == id && _ui.value.turn == TurnState.SPEAKING) toIdle()
                }
            }
            "turn.dropped" -> {
                streamingTurn = null
                when (val reason = m.optString("reason")) {
                    "no_wake" -> _ui.update { it.copy(ignoredBursts = it.ignoredBursts + 1) } // silent
                    else -> _ui.update { it.copy(error = "Didn't catch a question ($reason)") }
                }
                toIdle()
            }
            "error" -> {
                Log.w(TAG, "backend error: $m")
                _ui.update { it.copy(error = "${m.optString("code")}: ${m.optString("message")}") }
            }
        }
    }

    private fun toIdle() = _ui.update { it.copy(turn = TurnState.IDLE) }

    private fun fail(message: String) {
        disconnect()
        _ui.update { it.copy(error = message) }
    }

    private fun config() = JSONObject()
        .put("version", 1)
        .put("device", JSONObject().put("mic_source", if (mode == MicMode.HEADSET) "headset" else "phone"))
        .put("wake", JSONObject().put("phrases", JSONArray().put(_ui.value.wakePhrase.ifBlank { "hey raphael" })))
        .put("assistant", JSONObject().put("max_sentences", 2).put("language", "en-IN"))
        .put("asr", JSONObject().put("provider", "deepgram").put("endpoint_ms", 500))

    private fun formatTimings(t: JSONObject?): String {
        if (t == null) return ""
        return "end of speech → first audio ${t.opt("first_audio_ms")} ms · silence wait ${t.opt("eos_ms")} ms · " +
            "LLM first sentence ${t.opt("llm_first_sentence_ms")} ms · TTS ${t.opt("tts_first_audio_ms")} ms · total ${t.opt("total_ms")} ms"
    }

    companion object {
        /** "OnePlus Nord Buds 2" → "Nord Buds 2" so it fits the top pill. */
        fun shortName(name: String?): String {
            val n = name?.trim().orEmpty()
            if (n.isEmpty()) return "Earbuds"
            val words = n.split(" ")
            return if (n.length > 14 && words.size > 1) words.drop(1).joinToString(" ") else n
        }

        private const val TAG = "RaphaelTurn"
        private const val PREROLL_FRAMES = 35
    }
}
