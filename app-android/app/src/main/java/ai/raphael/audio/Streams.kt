package ai.raphael.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.max

private const val FRAME_BYTES = FRAME_SAMPLES * 2 // 640

/**
 * Continuous mic capture in 20 ms frames on its own thread.
 * [onFrame] is called on that thread for every frame.
 */
class MicStream {
    @Volatile
    private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // caller checks RECORD_AUDIO
    fun start(source: Int, onFrame: (ByteArray) -> Unit) {
        if (running) return
        running = true
        thread = Thread({
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf, FRAME_BYTES * 8))
                .build()
            try {
                rec.startRecording()
                while (running) {
                    val frame = ByteArray(FRAME_BYTES)
                    var got = 0
                    while (got < FRAME_BYTES && running) {
                        val n = rec.read(frame, got, FRAME_BYTES - got)
                        if (n <= 0) break
                        got += n
                    }
                    if (got == FRAME_BYTES) onFrame(frame)
                }
            } catch (e: Exception) {
                Log.e("RaphaelMic", "capture failed", e)
            } finally {
                rec.stop()
                rec.release()
            }
        }, "raphael-mic").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }
}

/** RMS of a PCM16 little-endian buffer, scaled to roughly 0..1 for speech. */
fun rms(pcm: ByteArray): Float {
    var sum = 0.0
    val n = pcm.size / 2
    for (i in 0 until n) {
        val s = ((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xff)).toShort().toDouble()
        sum += s * s
    }
    return if (n == 0) 0f else (Math.sqrt(sum / n) / 6000.0).toFloat().coerceIn(0f, 1f)
}

/**
 * Plays reply audio as it streams in. [flush] drops everything queued (barge-in).
 */
class ReplyPlayer(usage: Int) {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
        )
        .setBufferSizeInBytes(FRAME_BYTES * 10) // 200 ms: small buffer = low latency
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    @Volatile
    private var running = true

    @Volatile
    private var framesWritten = 0L

    /** Loudness (0..1) of the audio being handed to the speaker right now, for the UI. */
    @Volatile
    var level = 0f
        private set
    private var playedBase = 0L

    private val writer = Thread({
        track.play()
        while (running) {
            val chunk = queue.poll(100, TimeUnit.MILLISECONDS)
            if (chunk == null) {
                level *= 0.5f
                continue
            }
            level = rms(chunk)
            track.write(chunk, 0, chunk.size) // blocks while the buffer is full
            framesWritten += chunk.size / 2
        }
    }, "raphael-player").apply { start() }

    fun write(pcm: ByteArray) {
        queue.put(pcm)
    }

    /** True once everything queued has actually been played. */
    fun isDrained(): Boolean =
        queue.isEmpty() && (track.playbackHeadPosition.toLong() and 0xffffffffL) - playedBase >= framesWritten

    fun flush() {
        queue.clear()
        track.pause()
        track.flush()
        playedBase = track.playbackHeadPosition.toLong() and 0xffffffffL
        framesWritten = 0
        track.play()
    }

    fun release() {
        running = false
        writer.join(500)
        track.stop()
        track.release()
    }
}
