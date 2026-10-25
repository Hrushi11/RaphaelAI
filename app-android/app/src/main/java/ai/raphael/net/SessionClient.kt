package ai.raphael.net

import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The one WebSocket to the backend (spec §6): JSON control messages as text
 * frames, 16 kHz PCM16 audio as binary frames. Incoming traffic arrives on
 * [events]; sending is thread-safe (OkHttp queues it).
 */
class SessionClient {
    sealed interface Event {
        data object Open : Event
        data class Message(val json: JSONObject) : Event
        class Audio(val pcm: ByteArray) : Event
        data class Closed(val reason: String) : Event
    }

    val events = Channel<Event>(Channel.UNLIMITED)

    private val http = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var ws: WebSocket? = null

    fun connect(url: String) {
        ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                events.trySend(Event.Open)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                events.trySend(Event.Message(JSONObject(text)))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                events.trySend(Event.Audio(bytes.toByteArray()))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                events.trySend(Event.Closed("closed ($code) $reason"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                events.trySend(Event.Closed(t.message ?: t.javaClass.simpleName))
            }
        })
    }

    fun send(json: JSONObject): Boolean = ws?.send(json.toString()) ?: false

    fun sendAudio(frame: ByteArray): Boolean = ws?.send(frame.toByteString()) ?: false

    fun close() {
        ws?.close(1000, "bye")
        ws = null
    }
}

/** Builders for the phone → backend messages. */
object Msg {
    fun sessionStart(config: JSONObject) = JSONObject()
        .put("type", "session.start").put("config", config).put("client_version", "android-0.2")

    fun turnStart(turnId: String, trigger: String, prerollMs: Int) = JSONObject()
        .put("type", "turn.start").put("turn_id", turnId).put("trigger", trigger).put("preroll_ms", prerollMs)

    fun turnEnd(turnId: String) = JSONObject().put("type", "turn.end").put("turn_id", turnId)

    fun turnCancel(turnId: String) = JSONObject().put("type", "turn.cancel").put("turn_id", turnId)
}
