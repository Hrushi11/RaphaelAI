package ai.raphael.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Picks where voice audio goes (spec §3.2).
 *
 * "Communication device" is Android's name for the device used during a call.
 * Selecting a Bluetooth headset opens its call-quality link (HFP/SCO, 16 kHz),
 * which is what lets us use the headset's microphone.
 */
class AudioRouter(context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)

    /** Headsets Android can route voice to (paired + connected in Android Settings). */
    fun headsets(): List<AudioDeviceInfo> =
        am.availableCommunicationDevices.filter { it.type in HEADSET_TYPES }

    /** Everything Android offers for calls, for the debug list. */
    fun allCommunicationDevices(): List<AudioDeviceInfo> = am.availableCommunicationDevices

    fun builtInMic(): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

    /**
     * Route call audio to [device]. Returns true once Android reports it active.
     * The Bluetooth voice link usually takes 0.5–2 s to come up.
     */
    suspend fun routeTo(device: AudioDeviceInfo, timeoutMs: Long = 4000): Boolean {
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        if (!am.setCommunicationDevice(device)) return false
        val active = withTimeoutOrNull(timeoutMs) {
            while (am.communicationDevice?.id != device.id) delay(50)
            true
        } ?: false
        if (active) delay(300) // let the headset settle before audio starts
        return active
    }

    /** Give audio routing back to Android (music etc. returns to normal quality). */
    fun release() {
        am.clearCommunicationDevice()
        am.mode = AudioManager.MODE_NORMAL
    }

    fun watch(onChange: () -> Unit): AudioDeviceCallback {
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = onChange()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = onChange()
        }
        am.registerAudioDeviceCallback(cb, null)
        return cb
    }

    fun unwatch(cb: AudioDeviceCallback) = am.unregisterAudioDeviceCallback(cb)

    companion object {
        val HEADSET_TYPES = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)
    }
}

fun AudioDeviceInfo?.label(): String {
    if (this == null) return "unknown"
    val kind = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth call link (HFP)"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE Audio"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth media (A2DP)"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "phone mic"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "phone speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "phone earpiece"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        else -> "type $type"
    }
    return "$productName — $kind"
}
