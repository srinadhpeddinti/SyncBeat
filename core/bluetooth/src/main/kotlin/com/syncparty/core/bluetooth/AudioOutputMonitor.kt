package com.syncparty.core.bluetooth

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.syncparty.core.common.AudioOutput
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Detects the device's CURRENT audio output route using AudioManager's
 * device APIs — this is the modern, permission-light way to know "is sound
 * going to Bluetooth, wired headset, or the phone speaker" (Section 8),
 * and it works per-device: each phone routes its own audio, exactly as
 * required ("the host phone should NOT need to connect to everyone's
 * earbuds").
 *
 * Uses AudioDeviceCallback (API 23+) rather than the deprecated
 * ACTION_HEADSET_PLUG / BluetoothA2dp broadcasts, which reduces the
 * permission surface — no BLUETOOTH_CONNECT needed just to know "what's
 * the current output," only to actively manage BT connections.
 */
class AudioOutputMonitor(private val context: Context) {

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun currentOutput(): AudioOutput {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val hasBluetoothA2dp = devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
        if (hasBluetoothA2dp) return AudioOutput.BLUETOOTH_A2DP

        val hasWired = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (hasWired) return AudioOutput.WIRED_HEADSET

        val hasUsb = devices.any {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        if (hasUsb) return AudioOutput.USB

        val hasSpeaker = devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (hasSpeaker) return AudioOutput.PHONE_SPEAKER

        return AudioOutput.UNKNOWN
    }

    /** Emits whenever the active output route changes (BT connect/disconnect, headset plug, etc). */
    fun observeOutputChanges(): Flow<AudioOutput> = callbackFlow {
        trySend(currentOutput())

        val callback = object : AudioDeviceCallbackCompat() {
            override fun onChanged() {
                trySend(currentOutput())
            }
        }
        callback.register(audioManager)

        awaitClose { callback.unregister(audioManager) }
    }.distinctUntilChanged()

    /** Human-readable label for diagnostics/UI (Section 8/29). */
    fun outputLabel(output: AudioOutput): String = when (output) {
        AudioOutput.BLUETOOTH_A2DP -> "Bluetooth"
        AudioOutput.WIRED_HEADSET -> "Wired headphones"
        AudioOutput.PHONE_SPEAKER -> "Phone speaker"
        AudioOutput.USB -> "USB audio"
        AudioOutput.UNKNOWN -> "Unknown"
    }
}
