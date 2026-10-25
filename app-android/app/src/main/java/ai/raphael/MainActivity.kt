package ai.raphael

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ai.raphael.audio.AudioRouter
import ai.raphael.audio.label
import ai.raphael.audio.play
import ai.raphael.audio.record
import ai.raphael.service.MicMode
import ai.raphael.service.TurnController
import ai.raphael.ui.HomeScreen
import ai.raphael.ui.MicSource
import ai.raphael.ui.SpikeScreen
import ai.raphael.ui.SpikeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Headset audio spike (CLAUDE.md: first Android task).
 * Records 5 s from the chosen mic and plays it back, reporting which devices
 * Android actually used. Answers: does the headset mic work on this phone?
 */
class MainActivity : ComponentActivity() {
    private lateinit var router: AudioRouter
    private var deviceCallback: AudioDeviceCallback? = null
    private var job: Job? = null
    private lateinit var controller: TurnController
    private val showTest = mutableStateOf(false)
    private val url = mutableStateOf("")
    private val mode = mutableStateOf(MicMode.HEADSET)

    private val state = SpikeState(
        headsets = mutableStateListOf(),
        log = mutableStateListOf(),
        selectedId = mutableStateOf(null),
        source = mutableStateOf(MicSource.HEADSET),
        busy = mutableStateOf(false),
        level = mutableStateOf(0f),
        status = mutableStateOf("Ready"),
        hasPermission = mutableStateOf(false),
    )

    private val askPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        router = AudioRouter(this)
        controller = TurnController(router, lifecycleScope)
        val prefs = getSharedPreferences("raphael", MODE_PRIVATE)
        url.value = prefs.getString("url", DEFAULT_URL) ?: DEFAULT_URL
        setContent {
            val ui by controller.ui.collectAsState()
            if (showTest.value || !state.hasPermission.value) {
                SpikeScreen(
                    state = state,
                    onGrant = { askPermissions.launch(PERMISSIONS) },
                    onRefresh = { refresh() },
                    onRun = { runTest() },
                    onBack = if (state.hasPermission.value) ({ showTest.value = false }) else null,
                )
            } else {
                HomeScreen(
                    ui = ui,
                    url = url.value,
                    onUrl = { url.value = it },
                    mode = mode.value,
                    onMode = { mode.value = it },
                    headsets = state.headsets,
                    selectedHeadset = state.selectedId.value,
                    onHeadset = { state.selectedId.value = it },
                    onConnect = {
                        prefs.edit().putString("url", url.value.trim()).apply()
                        val headset = state.headsets.firstOrNull { it.id == state.selectedId.value }
                        controller.connect(url.value.trim(), mode.value, headset)
                    },
                    onDisconnect = { controller.disconnect() },
                    onTap = { controller.tap() },
                    onOpenTest = { showTest.value = true },
                )
            }
        }
        refresh()
    }

    override fun onStart() {
        super.onStart()
        deviceCallback = router.watch { refresh() }
    }

    override fun onStop() {
        deviceCallback?.let(router::unwatch)
        job?.cancel()
        // No background service yet: leaving the app ends the session.
        controller.disconnect()
        router.release()
        super.onStop()
    }

    private fun refresh() {
        state.hasPermission.value = PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!state.hasPermission.value) return
        val found = router.headsets()
        state.headsets.clear()
        state.headsets.addAll(found)
        if (found.none { it.id == state.selectedId.value }) state.selectedId.value = found.firstOrNull()?.id
        log("Call devices: " + router.allCommunicationDevices().joinToString { it.label() })
    }

    private fun runTest() {
        job = lifecycleScope.launch {
            state.busy.value = true
            try {
                when (state.source.value) {
                    MicSource.HEADSET -> headsetTest()
                    MicSource.PHONE -> phoneMicTest()
                }
            } catch (e: Exception) {
                log("FAILED: ${e.javaClass.simpleName}: ${e.message}")
                state.status.value = "Failed — see log"
            } finally {
                router.release()
                state.level.value = 0f
                state.busy.value = false
            }
        }
    }

    /** Headset mic mode: mic + speaker both on the headset's call link. */
    private suspend fun headsetTest() {
        val headset = state.headsets.firstOrNull { it.id == state.selectedId.value }
        if (headset == null) {
            log("No headset selected. Connect the buds in Android Settings → Bluetooth, then Refresh.")
            return
        }
        state.status.value = "Opening headset voice link…"
        val t0 = System.currentTimeMillis()
        if (!router.routeTo(headset)) {
            log("FAILED: headset voice link didn't open (${headset.label()})")
            state.status.value = "Headset link failed"
            return
        }
        log("Voice link open in ${System.currentTimeMillis() - t0} ms → ${headset.label()}")

        state.status.value = "Recording 5 s — speak into the buds"
        val rec = record(5, MediaRecorder.AudioSource.VOICE_COMMUNICATION, null) { state.level.value = it }
        log("Recorded ${rec.samples.size / 16} ms via ${rec.routedTo.label()}, peak ${rec.peak}")
        if (rec.peak < 500) log("WARNING: very quiet — the mic may not be working")

        state.status.value = "Playing back on the headset"
        val out = play(rec.samples, AudioAttributes.USAGE_VOICE_COMMUNICATION, null)
        log("Played via ${out.label()}")
        verdict(rec.routedTo, out, wantMic = AudioRouter.HEADSET_TYPES)
    }

    /** Phone mic mode: phone mic records, reply plays on the buds as normal media. */
    private suspend fun phoneMicTest() {
        state.status.value = "Recording 5 s — speak to the phone"
        val rec = record(5, MediaRecorder.AudioSource.MIC, router.builtInMic()) { state.level.value = it }
        log("Recorded ${rec.samples.size / 16} ms via ${rec.routedTo.label()}, peak ${rec.peak}")
        state.status.value = "Playing back"
        val out = play(rec.samples, AudioAttributes.USAGE_MEDIA, null)
        log("Played via ${out.label()}")
        verdict(rec.routedTo, out, wantMic = setOf(AudioDeviceInfo.TYPE_BUILTIN_MIC))
    }

    private fun verdict(mic: AudioDeviceInfo?, speaker: AudioDeviceInfo?, wantMic: Set<Int>) {
        val micOk = mic?.type in wantMic
        val spkOk = speaker?.type in AudioRouter.HEADSET_TYPES + AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        state.status.value = when {
            micOk && spkOk -> "PASS — mic and speaker both routed correctly"
            else -> "CHECK — mic ok: $micOk, speaker on buds: $spkOk"
        }
        log(state.status.value)
    }

    private fun log(line: String) {
        Log.i(TAG, line)
        state.log.add(0, line)
    }

    companion object {
        private const val TAG = "RaphaelSpike"
        private const val DEFAULT_URL = "ws://192.168.0.2:8000/v1/session"
        private val PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT)
    }
}
