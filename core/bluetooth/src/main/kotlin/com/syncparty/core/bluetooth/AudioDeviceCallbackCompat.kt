package com.syncparty.core.bluetooth

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Thin wrapper around AudioManager.AudioDeviceCallback so callers don't need
 * to touch the platform API's slightly awkward add/remove array signature
 * directly. minSdk 26 covers AudioDeviceCallback (added API 23) with margin.
 */
abstract class AudioDeviceCallbackCompat {

    abstract fun onChanged()

    private val handler = Handler(Looper.getMainLooper())

    private val delegate = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            onChanged()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            onChanged()
        }
    }

    fun register(audioManager: AudioManager) {
        audioManager.registerAudioDeviceCallback(delegate, handler)
    }

    fun unregister(audioManager: AudioManager) {
        audioManager.unregisterAudioDeviceCallback(delegate)
    }
}
