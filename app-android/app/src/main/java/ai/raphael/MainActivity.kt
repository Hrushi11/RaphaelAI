package ai.raphael

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ai.raphael.audio.AudioRouter
import ai.raphael.audio.label
import ai.raphael.audio.play
import ai.raphael.audio.record
import ai.raphael.service.Link
import ai.raphael.service.MicMode
import ai.raphael.service.RaphaelService
import ai.raphael.service.TurnController
import ai.raphael.ui.ChooseHeadsetScreen
import ai.raphael.ui.DashboardScreen
import ai.raphael.ui.HeadsetOption
import ai.raphael.ui.HomeScreen
import ai.raphael.ui.MicSource
import ai.raphael.ui.PermissionsScreen
import ai.raphael.ui.SettingsScreen
import ai.raphael.ui.SpikeScreen
import ai.raphael.ui.SpikeState
import ai.raphael.ui.TryState
import ai.raphael.ui.WakeWordScreen
import ai.raphael.ui.WelcomeScreen
import ai.raphael.ui.theme.RaphaelTheme
import ai.raphael.wake.WakeWordStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// DASHBOARD = app home (mock 13, the app always opens here); HOME = the talk screen (mocks 5–9, 12).
private enum class Screen { WELCOME, PERMISSIONS, HEADSET, WAKE, DASHBOARD, HOME, SETTINGS, TEST }

/**
 * Screens from design/ui-designs-mocks: onboarding (1–4), Home (5–9, 12), Settings (11),
 * plus the headset audio test. The session itself runs in [RaphaelService], so closing
 * this screen keeps Raphael listening.
 */
class MainActivity : ComponentActivity() {
    private lateinit var router: AudioRouter
    private lateinit var controller: TurnController
    private lateinit var wakeStore: WakeWordStore
    private val prefs by lazy { getSharedPreferences("raphael", MODE_PRIVATE) }

    private val screen = mutableStateOf(Screen.DASHBOARD)
    private var returnTo = Screen.DASHBOARD // where Headset / Wake / Test go back to
    private var settingsReturn = Screen.DASHBOARD // where Settings goes back to
    private val userName = mutableStateOf("")
    private var onboarding = false
    private var autoConnected = false

    // Persisted settings mirrored as Compose state
    private val url = mutableStateOf("")
    private val mode = mutableStateOf(MicMode.HEADSET)
    private val headsetName = mutableStateOf<String?>(null)
    private val wakeDraft = mutableStateOf("")
    private val headsetOptions = mutableStateListOf<HeadsetOption>()
    private val headsetMessage = mutableStateOf("")
    private val permissionMessage = mutableStateOf("")
    private val batteryExempt = mutableStateOf(false)

    // Headset audio test (day-1 spike)
    private var deviceCallback: AudioDeviceCallback? = null
    private var testJob: Job? = null
    private val test = SpikeState(
        headsets = mutableStateListOf(),
        log = mutableStateListOf(),
        selectedId = mutableStateOf(null),
        source = mutableStateOf(MicSource.HEADSET),
        busy = mutableStateOf(false),
        level = mutableStateOf(0f),
        status = mutableStateOf("Ready"),
        hasPermission = mutableStateOf(false),
    )

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        if (hasCorePermissions()) {
            permissionMessage.value = ""
            screen.value = if (onboarding) Screen.HEADSET else Screen.DASHBOARD
        } else {
            permissionMessage.value = "Raphael can’t work without the microphone and nearby devices. Allow them to continue."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        Raphael.init(this)
        router = Raphael.router
        controller = Raphael.controller
        wakeStore = WakeWordStore(this)

        url.value = prefs.getString("url", DEFAULT_URL) ?: DEFAULT_URL
        mode.value = MicMode.valueOf(prefs.getString("mode", MicMode.HEADSET.name) ?: MicMode.HEADSET.name)
        headsetName.value = prefs.getString("headset", null)
        wakeDraft.value = wakeStore.phrase
        if (controller.ui.value.wakePhrase.isEmpty()) controller.setWakePhrase(wakeStore.phrase)
        controller.setHandsFree(prefs.getBoolean("handsFree", true))

        onboarding = !prefs.getBoolean("onboarded", false)
        refresh()
        screen.value = when {
            onboarding -> Screen.WELCOME
            !hasCorePermissions() -> Screen.PERMISSIONS
            else -> Screen.DASHBOARD
        }
        userName.value = prefs.getString("name", "") ?: ""

        setContent { RaphaelTheme { App() } }
    }

    @Composable
    private fun App() {
        val ui by controller.ui.collectAsState()
        when (screen.value) {
            Screen.WELCOME -> WelcomeScreen(onStart = {
                screen.value = if (hasCorePermissions()) Screen.HEADSET else Screen.PERMISSIONS
            })

            Screen.PERMISSIONS -> PermissionsScreen(
                onAllow = { askPermissions.launch(PERMISSIONS + Manifest.permission.POST_NOTIFICATIONS) },
                message = permissionMessage.value,
            )

            Screen.HEADSET -> {
                BackHandler { screen.value = if (onboarding) Screen.WELCOME else returnTo }
                ChooseHeadsetScreen(
                    options = headsetOptions,
                    selected = headsetName.value,
                    onSelect = { headsetName.value = it.name; headsetMessage.value = "" },
                    mode = mode.value,
                    onMode = { mode.value = it; headsetMessage.value = "" },
                    onPairNew = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    onBack = { screen.value = if (onboarding) Screen.WELCOME else returnTo },
                    onContinue = { saveHeadset() },
                    message = headsetMessage.value,
                )
            }

            Screen.WAKE -> {
                BackHandler { screen.value = if (onboarding) Screen.HEADSET else returnTo }
                // Apply the typed phrase live (debounced) so "Try it" checks the new phrase.
                LaunchedEffect(wakeDraft.value) {
                    delay(600)
                    wakeDraft.value.trim().lowercase().takeIf { it.isNotBlank() }?.let(controller::setWakePhrase)
                }
                val firstWake = remember { ui.wakes.firstOrNull() }
                val heard = ui.wakes.firstOrNull() != firstWake
                LaunchedEffect(heard) { if (heard) controller.cancelTurn() } // a test, not a question
                WakeWordScreen(
                    phrase = wakeDraft.value,
                    onPhrase = { wakeDraft.value = it },
                    tryState = when {
                        heard -> TryState.HEARD
                        ui.link == Link.READY -> TryState.WAITING
                        else -> TryState.CONNECTING
                    },
                    handsFree = ui.handsFree,
                    onHandsFree = { controller.setHandsFree(it); prefs.edit().putBoolean("handsFree", it).apply() },
                    onBack = { screen.value = if (onboarding) Screen.HEADSET else returnTo },
                    onDone = { saveWake() },
                    doneLabel = if (onboarding) "Done" else "Save",
                )
            }

            Screen.DASHBOARD -> {
                LaunchedEffect(Unit) {
                    // Opening the app means "I want Raphael": connect once per launch.
                    if (!autoConnected && controller.ui.value.link == Link.DISCONNECTED) connect()
                    autoConnected = true
                }
                // Say "Hey Raphael" (or tap an earbud) on the dashboard: jump to the talk screen.
                LaunchedEffect(ui.turn) {
                    if (ui.turn == ai.raphael.service.TurnState.LISTENING || ui.turn == ai.raphael.service.TurnState.THINKING) {
                        screen.value = Screen.HOME
                    }
                }
                DashboardScreen(
                    ui = ui,
                    name = userName.value,
                    earbudsLabel = if (mode.value == MicMode.PHONE) "Phone mic · reply on earbuds"
                    else "${headsetName.value ?: "Choose earbuds"} · Earbuds mic",
                    onSettings = { refresh(); settingsReturn = Screen.DASHBOARD; screen.value = Screen.SETTINGS },
                    onPause = { RaphaelService.pause(this) },
                    onResume = { connect() },
                    onRetry = { connect() },
                    onEarbuds = { refresh(); returnTo = Screen.DASHBOARD; screen.value = Screen.HEADSET },
                    onTalk = { screen.value = Screen.HOME },
                )
            }

            Screen.HOME -> {
                BackHandler { screen.value = Screen.DASHBOARD }
                LaunchedEffect(Unit) {
                    // Opening the app means "I want Raphael": connect once per launch.
                    if (!autoConnected && controller.ui.value.link == Link.DISCONNECTED) connect()
                    autoConnected = true
                }
                HomeScreen(
                    ui = ui,
                    micLevel = controller::micLevel,
                    playLevel = controller::playLevel,
                    onPill = { refresh(); returnTo = Screen.HOME; screen.value = Screen.HEADSET },
                    onSettings = { refresh(); settingsReturn = Screen.HOME; screen.value = Screen.SETTINGS },
                    onAsk = { controller.tap() },
                    onStop = { RaphaelService.stop(this) },
                    onPause = { RaphaelService.pause(this) },
                    onResume = { connect() },
                    onRetry = { connect() },
                )
            }

            Screen.SETTINGS -> {
                BackHandler { screen.value = settingsReturn }
                SettingsScreen(
                    name = userName.value,
                    onName = { userName.value = it; prefs.edit().putString("name", it).apply() },
                    serverUrl = url.value,
                    link = ui.link,
                    headsetLabel = if (mode.value == MicMode.PHONE) "Phone mic" else headsetName.value ?: "Choose earbuds",
                    listenWith = if (mode.value == MicMode.PHONE) "Phone mic" else "Earbuds mic",
                    wakePhrase = ui.wakePhrase,
                    handsFree = ui.handsFree,
                    keepRunning = batteryExempt.value,
                    version = versionName(),
                    onBack = { screen.value = settingsReturn },
                    onServerUrl = {
                        url.value = it
                        prefs.edit().putString("url", it).apply()
                        connect()
                    },
                    onEarbuds = { refresh(); returnTo = Screen.SETTINGS; screen.value = Screen.HEADSET },
                    onWakeWord = { wakeDraft.value = ui.wakePhrase; returnTo = Screen.SETTINGS; screen.value = Screen.WAKE },
                    onHandsFree = { controller.setHandsFree(it); prefs.edit().putBoolean("handsFree", it).apply() },
                    onKeepRunning = { askBatteryExemption() },
                    onAudioTest = {
                        RaphaelService.stop(this) // the test needs the mic and the earbuds' link to itself
                        returnTo = Screen.SETTINGS
                        screen.value = Screen.TEST
                    },
                )
            }

            Screen.TEST -> {
                BackHandler { screen.value = returnTo }
                SpikeScreen(
                    state = test,
                    onGrant = { askPermissions.launch(PERMISSIONS) },
                    onRefresh = { refresh() },
                    onRun = { runTest() },
                    onBack = { screen.value = returnTo },
                )
            }
        }
    }

    // ---------------------------------------------------------------- actions

    private fun connect() {
        val id = router.headsets().firstOrNull { it.productName?.toString() == headsetName.value }?.id
        RaphaelService.connect(this, url.value.trim(), mode.value, id)
    }

    private fun saveHeadset() {
        val chosen = headsetOptions.firstOrNull { it.name == headsetName.value }
        if (mode.value == MicMode.HEADSET && chosen?.connected != true) {
            headsetMessage.value = if (chosen == null) "Choose your earbuds, or switch to Phone mic."
            else "Turn on ${chosen.name} and connect it in Bluetooth settings first, or switch to Phone mic."
            return
        }
        prefs.edit().putString("mode", mode.value.name).putString("headset", headsetName.value).apply()
        connect() // (re)connects with the new earbuds / mic source
        if (onboarding) {
            wakeDraft.value = wakeStore.phrase
            screen.value = Screen.WAKE
        } else {
            screen.value = returnTo
        }
    }

    private fun saveWake() {
        val phrase = wakeDraft.value.trim().lowercase()
        wakeStore.phrase = phrase
        controller.setWakePhrase(phrase)
        if (onboarding) {
            onboarding = false
            prefs.edit().putBoolean("onboarded", true).apply()
            autoConnected = true // already connected during onboarding
            screen.value = Screen.DASHBOARD
        } else {
            screen.value = returnTo
        }
    }

    /** Spec §3.4: ask Android not to kill Raphael in the background. */
    @SuppressLint("BatteryLife")
    private fun askBatteryExemption() {
        val intent = if (batteryExempt.value) Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        else Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun versionName(): String =
        (packageManager.getPackageInfo(packageName, 0).versionName ?: "").substringBefore("-")

    // ---------------------------------------------------------------- devices & permissions

    private fun hasCorePermissions() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Re-read headsets (connected now + paired audio devices) and permission state. */
    private fun refresh() {
        test.hasPermission.value = hasCorePermissions()
        if (!hasCorePermissions()) return
        val connected = router.headsets()
        test.headsets.clear()
        test.headsets.addAll(connected)
        if (connected.none { it.id == test.selectedId.value }) test.selectedId.value = connected.firstOrNull()?.id

        val names = connected.mapNotNull { it.productName?.toString() }.distinct()
        val paired = try {
            getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
                .filter { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }
                .mapNotNull { it.name }
        } catch (e: SecurityException) {
            emptyList()
        }
        headsetOptions.clear()
        headsetOptions.addAll(names.map { HeadsetOption(null, it, connected = true) })
        headsetOptions.addAll(paired.filter { it !in names }.distinct().map { HeadsetOption(null, it, connected = false) })
        if (headsetName.value == null) headsetName.value = names.firstOrNull()
    }

    override fun onStart() {
        super.onStart()
        deviceCallback = router.watch { refresh() }
    }

    override fun onResume() {
        super.onResume()
        batteryExempt.value = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        refresh()
    }

    override fun onStop() {
        // "Anytime the app is closed it should start from the dashboard": leaving from the talk
        // screen or Settings comes back to the dashboard. (Screens that open Android pages,
        // like Bluetooth pairing or the headset test, keep their place.)
        if (!isChangingConfigurations && screen.value in setOf(Screen.HOME, Screen.SETTINGS)) {
            screen.value = Screen.DASHBOARD
        }
        deviceCallback?.let(router::unwatch)
        testJob?.cancel() // only the audio test; the session keeps running in RaphaelService
        super.onStop()
    }

    // ---------------------------------------------------------------- headset audio test

    private fun runTest() {
        testJob = lifecycleScope.launch {
            test.busy.value = true
            try {
                when (test.source.value) {
                    MicSource.HEADSET -> headsetTest()
                    MicSource.PHONE -> phoneMicTest()
                }
            } catch (e: Exception) {
                log("FAILED: ${e.javaClass.simpleName}: ${e.message}")
                test.status.value = "Failed — see log"
            } finally {
                router.release()
                test.level.value = 0f
                test.busy.value = false
            }
        }
    }

    /** Headset mic mode: mic + speaker both on the headset's call link. */
    private suspend fun headsetTest() {
        val headset = test.headsets.firstOrNull { it.id == test.selectedId.value }
        if (headset == null) {
            log("No headset selected. Connect the buds in Android Settings → Bluetooth, then Refresh.")
            return
        }
        test.status.value = "Opening headset voice link…"
        val t0 = System.currentTimeMillis()
        if (!router.routeTo(headset)) {
            log("FAILED: headset voice link didn't open (${headset.label()})")
            test.status.value = "Headset link failed"
            return
        }
        log("Voice link open in ${System.currentTimeMillis() - t0} ms → ${headset.label()}")
        test.status.value = "Recording 5 s — speak into the buds"
        val rec = record(5, MediaRecorder.AudioSource.VOICE_COMMUNICATION, null) { test.level.value = it }
        log("Recorded ${rec.samples.size / 16} ms via ${rec.routedTo.label()}, peak ${rec.peak}")
        if (rec.peak < 500) log("WARNING: very quiet — the mic may not be working")
        test.status.value = "Playing back on the headset"
        val out = play(rec.samples, AudioAttributes.USAGE_VOICE_COMMUNICATION, null)
        log("Played via ${out.label()}")
        verdict(rec.routedTo, out, wantMic = AudioRouter.HEADSET_TYPES)
    }

    /** Phone mic mode: phone mic records, reply plays on the buds as normal media. */
    private suspend fun phoneMicTest() {
        test.status.value = "Recording 5 s — speak to the phone"
        val rec = record(5, MediaRecorder.AudioSource.MIC, router.builtInMic()) { test.level.value = it }
        log("Recorded ${rec.samples.size / 16} ms via ${rec.routedTo.label()}, peak ${rec.peak}")
        test.status.value = "Playing back"
        val out = play(rec.samples, AudioAttributes.USAGE_MEDIA, null)
        log("Played via ${out.label()}")
        verdict(rec.routedTo, out, wantMic = setOf(AudioDeviceInfo.TYPE_BUILTIN_MIC))
    }

    private fun verdict(mic: AudioDeviceInfo?, speaker: AudioDeviceInfo?, wantMic: Set<Int>) {
        val micOk = mic?.type in wantMic
        val spkOk = speaker?.type in AudioRouter.HEADSET_TYPES + AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        test.status.value = when {
            micOk && spkOk -> "PASS — mic and speaker both routed correctly"
            else -> "CHECK — mic ok: $micOk, speaker on buds: $spkOk"
        }
        log(test.status.value)
    }

    private fun log(line: String) {
        Log.i(TAG, line)
        test.log.add(0, line)
    }

    companion object {
        private const val TAG = "RaphaelSpike"
        private const val DEFAULT_URL = "ws://192.168.0.5:8000/v1/session"
        private val PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT)
    }
}
