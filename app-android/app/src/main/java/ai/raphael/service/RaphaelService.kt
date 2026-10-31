package ai.raphael.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import ai.raphael.MainActivity
import ai.raphael.R
import ai.raphael.Raphael
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps Raphael listening with the screen off (spec §3.4): foreground service of
 * type microphone, persistent notification with Ask / Pause / Stop, CPU + Wi-Fi
 * locks, automatic reconnect, and a MediaSession so earbud taps act like the
 * Ask button (spec §5.2).
 */
class RaphaelService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller get() = Raphael.controller
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var session: MediaSession? = null
    private var watchJob: Job? = null
    private var reconnectJob: Job? = null

    private var url = ""
    private var mode = MicMode.HEADSET
    private var headsetId: Int? = null
    private var paused = false
    private var everReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Raphael.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Raphael listening", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while Raphael can hear you"
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                url = intent.getStringExtra(EXTRA_URL).orEmpty()
                mode = MicMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: MicMode.HEADSET.name)
                headsetId = intent.getIntExtra(EXTRA_HEADSET, -1).takeIf { it >= 0 }
                paused = false
                controller.setPaused(false)
                if (controller.ui.value.link != Link.DISCONNECTED) controller.disconnect() // new headset/mode: reconnect
                startForeground(NOTIFICATION_ID, buildNotification(controller.ui.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                acquireLocks()
                startMediaSession()
                watch()
                connectNow()
            }
            ACTION_TAP -> controller.tap()
            ACTION_PAUSE -> {
                paused = true
                reconnectJob?.cancel()
                controller.disconnect()
                controller.setPaused(true)
                notifyState()
            }
            ACTION_RESUME -> {
                paused = false
                controller.setPaused(false)
                connectNow()
            }
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun connectNow() {
        val headset = Raphael.router.headsets().firstOrNull { it.id == headsetId } ?: Raphael.router.headsets().firstOrNull()
        controller.connect(url, mode, headset)
    }

    /** Update the notification on every state change; reconnect if the link drops on its own. */
    private fun watch() {
        if (watchJob != null) return
        watchJob = scope.launch {
            controller.ui.collect { s ->
                notifyState()
                if (s.link == Link.READY) {
                    everReady = true
                    reconnectJob?.cancel()
                    reconnectJob = null
                }
                if (s.link == Link.DISCONNECTED && everReady && !paused && reconnectJob == null) {
                    reconnectJob = scope.launch {
                        var wait = 1000L
                        while (controller.ui.value.link != Link.READY) {
                            delay(wait)
                            if (controller.ui.value.link == Link.DISCONNECTED) {
                                Log.i(TAG, "reconnecting")
                                connectNow()
                            }
                            wait = (wait * 2).coerceAtMost(15_000)
                        }
                        reconnectJob = null
                    }
                }
            }
        }
    }

    private fun startMediaSession() {
        if (session != null) return
        session = MediaSession(this, "Raphael").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val ev = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    Log.i(TAG, "media button: ${ev?.let { KeyEvent.keyCodeToString(it.keyCode) + " action=" + it.action }}")
                    if (ev != null && ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0 && ev.keyCode in TAP_KEYS) {
                        Raphael.controller.tap()
                    }
                    return true
                }
            })
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .build()
            )
            isActive = true
        }
        claimMediaButtons()
    }

    /**
     * Android sends earbud taps to the app that most recently played media audio.
     * A moment of silence as media makes that us.
     */
    private fun claimMediaButtons() {
        scope.launch(Dispatchers.IO) {
            val samples = 1600 // 100 ms
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(16_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(samples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(ShortArray(samples), 0, samples)
            track.play()
            delay(300)
            track.release()
        }
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "raphael:listening").apply { acquire() }
        }
        if (wifiLock == null) {
            @Suppress("DEPRECATION")
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "raphael:wifi").apply { acquire() }
        }
    }

    private fun stopEverything() {
        paused = true
        reconnectJob?.cancel()
        controller.disconnect()
        controller.setPaused(true)
        session?.release(); session = null
        wakeLock?.takeIf { it.isHeld }?.release(); wakeLock = null
        wifiLock?.takeIf { it.isHeld }?.release(); wifiLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (wakeLock != null) stopEverything()
        scope.cancel()
        super.onDestroy()
    }

    private fun notifyState() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(controller.ui.value))
    }

    private fun buildNotification(s: UiState): Notification {
        val text = when {
            paused -> "Paused — not listening"
            s.link == Link.CONNECTING -> "Connecting…"
            s.link == Link.DISCONNECTED -> "Reconnecting…"
            s.turn == TurnState.LISTENING -> "Listening…"
            s.turn == TurnState.THINKING -> "Thinking…"
            s.turn == TurnState.SPEAKING -> "Speaking…"
            s.handsFree -> "Say “${s.wakePhrase}” or tap your earbud"
            else -> "Tap your earbud or Ask"
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_raphael)
            .setContentTitle("Raphael")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // readable on the lock screen (mock 10)
            .setColor(0xFF7C4DFF.toInt())
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
        if (!paused) b.addAction(0, "Ask", action(ACTION_TAP, 1))
        b.addAction(0, if (paused) "Resume" else "Pause", action(if (paused) ACTION_RESUME else ACTION_PAUSE, 2))
        b.addAction(0, "Stop", action(ACTION_STOP, 3))
        return b.build()
    }

    private fun action(name: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, RaphaelService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val TAG = "RaphaelService"
        private const val CHANNEL = "listening"
        private const val NOTIFICATION_ID = 1
        const val ACTION_CONNECT = "ai.raphael.CONNECT"
        const val ACTION_TAP = "ai.raphael.TAP"
        const val ACTION_PAUSE = "ai.raphael.PAUSE"
        const val ACTION_RESUME = "ai.raphael.RESUME"
        const val ACTION_STOP = "ai.raphael.STOP"
        const val EXTRA_URL = "url"
        const val EXTRA_MODE = "mode"
        const val EXTRA_HEADSET = "headset"
        private val TAP_KEYS = setOf(
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
        )

        fun connect(context: Context, url: String, mode: MicMode, headsetId: Int?) {
            context.startForegroundService(
                Intent(context, RaphaelService::class.java).setAction(ACTION_CONNECT)
                    .putExtra(EXTRA_URL, url).putExtra(EXTRA_MODE, mode.name).putExtra(EXTRA_HEADSET, headsetId ?: -1)
            )
        }

        fun pause(context: Context) {
            context.startService(Intent(context, RaphaelService::class.java).setAction(ACTION_PAUSE))
        }

        fun resume(context: Context) {
            context.startService(Intent(context, RaphaelService::class.java).setAction(ACTION_RESUME))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RaphaelService::class.java).setAction(ACTION_STOP))
        }
    }
}
