package io.github.zyrouge.symphony.services.radio

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Timer
import kotlin.math.pow

typealias RadioPlayerOnPreparedListener = () -> Unit
typealias RadioPlayerOnPlaybackPositionListener = (RadioPlayer.PlaybackPosition) -> Unit
typealias RadioPlayerOnFinishListener = () -> Unit
typealias RadioPlayerOnErrorListener = (Int, Int) -> Unit
typealias RadioPlayerOnCrossfadeTriggerListener = () -> Unit

class RadioPlayer(val symphony: Symphony, val song: Song) {
    val id = song.id
    val uri = song.uri

    data class PlaybackPosition(val played: Long, val total: Long) {
        val ratio: Float
            get() = (played.toFloat() / total).takeIf { it.isFinite() } ?: 0f

        companion object {
            val zero = PlaybackPosition(0L, 0L)
        }
    }

    enum class State {
        Unprepared,
        Preparing,
        Prepared,
        Finished,
        Destroyed,
    }

    private val exoPlayer: ExoPlayer
    private var onPrepared: RadioPlayerOnPreparedListener? = null
    private var onPlaybackPosition: RadioPlayerOnPlaybackPositionListener? = null
    private var onFinish: RadioPlayerOnFinishListener? = null
    private var onError: RadioPlayerOnErrorListener? = null
    private var onCrossfadeTrigger: RadioPlayerOnCrossfadeTriggerListener? = null
    private var fader: RadioEffects.Fader? = null
    private var playbackPositionUpdater: Timer? = null

    var state = State.Unprepared
        private set
    var hasPlayedOnce = false
        private set
    var volume = MAX_VOLUME
        private set
    var speed = DEFAULT_SPEED
        private set
    var pitch = DEFAULT_PITCH
        private set
    var crossfadeTriggered = false
        private set

    val usable get() = state == State.Prepared
    val fadePlayback get() = symphony.settings.fadePlayback.value
    
    @get:OptIn(UnstableApi::class)
    val audioSessionId get() = exoPlayer.audioSessionId
    
    val isPlaying get() = exoPlayer.isPlaying

    val playbackPosition
        get() = try {
            PlaybackPosition(
                played = exoPlayer.currentPosition.coerceAtLeast(0L),
                total = exoPlayer.duration.coerceAtLeast(0L),
            )
        } catch (_: Exception) {
            null
        }

    // PRO Audio: Calculate ReplayGain factor (Loudness Normalization)
    private val replayGainFactor: Float = run {
        if (symphony.settings.enableReplayGain.value) {
            val gainDb = song.replayGain ?: 0f
            // Formula: 10^(dB / 20). Coerced to prevent extreme clipping or muting.
            10f.pow(gainDb / 20f).coerceIn(0.1f, 3.0f)
        } else {
            1f
        }
    }

    init {
        exoPlayer = ExoPlayer.Builder(symphony.applicationContext).build().apply {
            skipSilenceEnabled = false 
            
            setMediaItem(MediaItem.fromUri(uri))
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (state != State.Prepared) {
                                state = State.Prepared
                                createDurationTimer()
                                onPrepared?.invoke()
                            }
                        }
                        Player.STATE_ENDED -> {
                            state = State.Finished
                            onFinish?.invoke()
                        }
                        else -> {}
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    state = State.Destroyed
                    onError?.invoke(error.errorCode, error.errorCode)
                }
            })
        }
    }

    fun prepare() {
        when (state) {
            State.Unprepared -> {
                state = State.Preparing
                exoPlayer.prepare()
            }
            State.Prepared -> onPrepared?.invoke()
            else -> {}
        }
    }

    fun stop() = destroy()

    fun destroy() {
        state = State.Destroyed
        destroyDurationTimer()
        symphony.groove.coroutineScope.launch(Dispatchers.Main) {
            try {
                exoPlayer.stop()
                exoPlayer.release()
            } catch (_: Exception) {}
        }
    }

    fun start() {
        exoPlayer.play()
        createDurationTimer()
        if (!hasPlayedOnce) {
            hasPlayedOnce = true
            updatePlaybackParameters()
        }
    }

    fun pause() {
        exoPlayer.pause()
        destroyDurationTimer()
    }

    fun seek(to: Int) {
        exoPlayer.seekTo(to.toLong())
        emitPlaybackPosition()
    }

    fun changeVolume(
        to: Float,
        forceFade: Boolean = false,
        onFinish: (Boolean) -> Unit,
    ) {
        fader?.stop()
        when {
            to == volume -> onFinish(true)
            forceFade || fadePlayback -> {
                val duration = (symphony.settings.fadePlaybackDuration.value * 1000).toInt()
                fader = RadioEffects.Fader(
                    RadioEffects.Fader.Options(
                        from = volume, 
                        to = to, 
                        duration = duration,
                        curve = RadioEffects.FadeCurve.EQUAL_POWER
                    ),
                    onUpdate = {
                        changeVolumeInstant(it)
                    },
                    onFinish = {
                        onFinish(it)
                        fader = null
                    }
                )
                fader?.start()
            }
            else -> {
                changeVolumeInstant(to)
                onFinish(true)
            }
        }
    }

    fun changeVolumeInstant(to: Float) {
        volume = to
        symphony.groove.coroutineScope.launch(Dispatchers.Main) {
            try {
                if (state != State.Destroyed) {
                    // Apply ReplayGain factor to the final volume output
                    exoPlayer.volume = to * replayGainFactor
                }
            } catch (_: Exception) {}
        }
    }

    fun changeSpeed(to: Float) {
        speed = to
        if (hasPlayedOnce) {
            updatePlaybackParameters()
        }
    }

    fun changePitch(to: Float) {
        pitch = to
        if (hasPlayedOnce) {
            updatePlaybackParameters()
        }
    }

    private fun updatePlaybackParameters() {
        try {
            val wasPlaying = exoPlayer.isPlaying
            exoPlayer.playbackParameters = PlaybackParameters(speed, pitch)
            if (!wasPlaying) {
                exoPlayer.pause()
            }
        } catch (err: Exception) {
            Logger.error("RadioPlayer", "changing playback parameters failed", err)
        }
    }

    fun setOnPreparedListener(listener: RadioPlayerOnPreparedListener?) {
        onPrepared = listener
    }

    fun setOnPlaybackPositionListener(listener: RadioPlayerOnPlaybackPositionListener?) {
        onPlaybackPosition = listener
    }

    fun setOnFinishListener(listener: RadioPlayerOnFinishListener?) {
        onFinish = listener
    }

    fun setOnErrorListener(listener: RadioPlayerOnErrorListener?) {
        onError = listener
    }
    
    fun setOnCrossfadeTriggerListener(listener: RadioPlayerOnCrossfadeTriggerListener?) {
        onCrossfadeTrigger = listener
    }

    private fun createDurationTimer() {
        if (playbackPositionUpdater != null) return
        playbackPositionUpdater = kotlin.concurrent.timer(period = 100L) {
            emitPlaybackPosition()
        }
    }

    private fun emitPlaybackPosition() {
        playbackPosition?.let { pos ->
            onPlaybackPosition?.invoke(pos)
            
            if (!crossfadeTriggered && fadePlayback) {
                val fadeDurationMs = (symphony.settings.fadePlaybackDuration.value * 1000).toLong()
                if (pos.total > 0 && pos.played >= pos.total - fadeDurationMs) {
                    crossfadeTriggered = true
                    symphony.groove.coroutineScope.launch(Dispatchers.Main) {
                        onCrossfadeTrigger?.invoke()
                    }
                }
            }
        }
    }

    private fun destroyDurationTimer() {
        playbackPositionUpdater?.cancel()
        playbackPositionUpdater = null
    }

    companion object {
        const val MIN_VOLUME = 0f
        const val MAX_VOLUME = 1f
        const val DUCK_VOLUME = 0.2f
        const val DEFAULT_SPEED = 1f
        const val DEFAULT_PITCH = 1f
    }
}