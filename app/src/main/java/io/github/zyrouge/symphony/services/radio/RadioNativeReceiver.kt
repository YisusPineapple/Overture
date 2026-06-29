package io.github.zyrouge.symphony.services.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import io.github.zyrouge.symphony.Symphony

class RadioNativeReceiver(private val symphony: Symphony) : BroadcastReceiver() {
    fun start() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            symphony.applicationContext.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
        } else {
            symphony.applicationContext.registerReceiver(this, filter)
        }
    }

    fun destroy() {
        symphony.applicationContext.unregisterReceiver(this)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.action?.let { action ->
            when (action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    intent.extras?.getInt("state", -1)?.let {
                        when (it) {
                            0 -> onHeadphonesDisconnect()
                            1 -> onHeadphonesConnect()
                            else -> {}
                        }
                    }
                }

                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> onHeadphonesDisconnect()
                else -> {}
            }
        }
    }

    private fun onHeadphonesConnect() {
        if (!symphony.radio.hasPlayer) {
            return
        }
        if (!symphony.radio.isPlaying && symphony.settings.playOnHeadphonesConnect.value) {
            symphony.radio.resume()
        }
    }

    private fun onHeadphonesDisconnect() {
        if (!symphony.radio.hasPlayer) {
            return
        }
        if (symphony.radio.isPlaying && symphony.settings.pauseOnHeadphonesDisconnect.value) {
            symphony.radio.pauseInstant()
        }
    }
}