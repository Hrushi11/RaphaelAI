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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue

enum class TurnState { IDLE, LISTENING, THINKING, SPEAKING }
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
)

/**
 * Phone-side turn state machine (spec §3.5), tap-to-talk for now.
 *
 * All state changes happen on the [scope]'s thread (Main). The mic thread only
 * reads [streamingTurn] to decide whether a frame goes to the backend.
 */
class TurnController(private val router: AudioRouter, private val scope: CoroutineScope) {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private var client: SessionClient? = null
    private var mic: MicStream? = null
    private var player: ReplyPlayer? = null
    private var eventsJob: Job? = null
    private var turnId: String? = null

    @Volatile
    private var streamingTurn: String? = null

    // Last 300 ms of mic audio, sent right after turn.start so the first word isn't clipped.
    private val preroll = ArrayBlockingQueue<ByteArray>(PREROLL_FRAMES)
    private val chime = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 50)

    fun connect(url: String, mode: MicMode, headset: AudioDeviceInfo?) {
        if (_ui.value.link != Link.DISCONNECTED) return
        _ui.value = UiState(link = Link.CONNECTING)
        scope.launch {
            // 1. Audio route. Held open while connected so a tap starts capture instantly.
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
            eventsJob = scope.launch { for (ev in c.events) onEvent(ev, mode) }
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
            TurnState.IDLE -> {
                val id = UUID.randomUUID().toString().take(8)
                turnId = id
                chime.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                val frames = preroll.toList()
                c.send(Msg.turnStart(id, "tap", frames.size * 20))
                frames.forEach(c::sendAudio)
                streamingTurn = id
                _ui.update { it.copy(turn = TurnState.LISTENING, heard = "", answer = "", timings = "", error = "") }
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

    // Runs on the mic thread.
    private fun onMicFrame(frame: ByteArray) {
        val id = streamingTurn
        if (id != null) {
            client?.sendAudio(frame)
        } else {
            if (!preroll.offer(frame)) {
                preroll.poll()
                preroll.offer(frame)
            }
        }
    }

    private fun onEvent(ev: SessionClient.Event, mode: MicMode) {
        when (ev) {
            SessionClient.Event.Open -> client?.send(Msg.sessionStart(config(mode)))
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
            "transcript.partial" -> _ui.update { it.copy(heard = m.getString("text")) }
            "transcript.final" -> {
                streamingTurn = null
                _ui.update { it.copy(turn = TurnState.THINKING, heard = m.getString("text")) }
            }
            "reply.text" -> _ui.update { it.copy(answer = it.answer + m.getString("delta")) }
            "reply.audio_start" -> _ui.update { it.copy(turn = TurnState.SPEAKING) }
            "reply.done" -> {
                _ui.update { it.copy(timings = formatTimings(m.optJSONObject("timings"))) }
                val id = turnId
                scope.launch {
                    while (player?.isDrained() == false && turnId == id && _ui.value.turn == TurnState.SPEAKING) delay(50)
                    if (turnId == id && _ui.value.turn == TurnState.SPEAKING) toIdle()
                }
            }
            "turn.dropped" -> {
                streamingTurn = null
                _ui.update { it.copy(error = "Didn't catch a question (${m.optString("reason")})") }
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

    private fun config(mode: MicMode) = JSONObject()
        .put("version", 1)
        .put("device", JSONObject().put("mic_source", if (mode == MicMode.HEADSET) "headset" else "phone"))
        .put("assistant", JSONObject().put("max_sentences", 2).put("language", "en-IN"))
        .put("asr", JSONObject().put("provider", "deepgram").put("endpoint_ms", 500))

    private fun formatTimings(t: JSONObject?): String {
        if (t == null) return ""
        val total = t.opt("total_ms")
        return "end of speech → first audio ${t.opt("first_audio_ms")} ms · silence wait ${t.opt("eos_ms")} ms · " +
            "LLM first sentence ${t.opt("llm_first_sentence_ms")} ms · TTS ${t.opt("tts_first_audio_ms")} ms · total $total ms"
    }

    companion object {
        private const val TAG = "RaphaelTurn"
        private const val PREROLL_FRAMES = 15
    }
}
