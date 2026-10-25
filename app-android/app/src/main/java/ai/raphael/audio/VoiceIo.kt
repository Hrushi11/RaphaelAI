package ai.raphael.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

const val SAMPLE_RATE = 16_000
const val FRAME_SAMPLES = 320 // 20 ms

class Recording(val samples: ShortArray, val routedTo: AudioDeviceInfo?, val peak: Int)

/** Records [seconds] of 16 kHz mono PCM16. [onLevel] gets 0..1 every 20 ms. */
@SuppressLint("MissingPermission") // caller checks RECORD_AUDIO first
suspend fun record(
    seconds: Int,
    source: Int,
    preferred: AudioDeviceInfo?,
    onLevel: (Float) -> Unit,
): Recording = withContext(Dispatchers.IO) {
    val format = AudioFormat.Builder()
        .setSampleRate(SAMPLE_RATE)
        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build()
    val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val rec = AudioRecord.Builder()
        .setAudioSource(source)
        .setAudioFormat(format)
        .setBufferSizeInBytes(max(minBuf, FRAME_SAMPLES * 2 * 8))
        .build()
    if (preferred != null) rec.setPreferredDevice(preferred)

    val out = ShortArray(SAMPLE_RATE * seconds)
    val frame = ShortArray(FRAME_SAMPLES)
    var n = 0
    var peak = 0
    var routed: AudioDeviceInfo? = null
    try {
        rec.startRecording()
        while (n < out.size && coroutineContext.isActive) {
            val got = rec.read(frame, 0, minOf(FRAME_SAMPLES, out.size - n))
            if (got <= 0) break
            var sumSq = 0.0
            for (i in 0 until got) {
                val s = frame[i]
                out[n + i] = s
                sumSq += s * s.toDouble()
                peak = max(peak, abs(s.toInt()))
            }
            n += got
            if (routed == null) routed = rec.routedDevice
            onLevel((sqrt(sumSq / got) / 8000.0).toFloat().coerceIn(0f, 1f))
        }
    } finally {
        rec.stop()
        rec.release()
    }
    Recording(out.copyOf(n), routed, peak)
}

/** Plays PCM16 and returns the device Android actually played it on. */
suspend fun play(samples: ShortArray, usage: Int, preferred: AudioDeviceInfo?): AudioDeviceInfo? =
    withContext(Dispatchers.IO) {
        val track = AudioTrack.Builder()
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
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (preferred != null) track.setPreferredDevice(preferred)
        try {
            track.play()
            track.write(samples, 0, samples.size) // blocks until queued
            val routed = track.routedDevice
            while (track.playbackHeadPosition < samples.size && coroutineContext.isActive) delay(20)
            routed
        } finally {
            track.stop()
            track.release()
        }
    }
