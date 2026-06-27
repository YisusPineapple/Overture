package io.github.zyrouge.symphony.services.radio

import android.content.Context
import android.os.PowerManager
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.utils.Eventer
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Date
import java.util.Timer

class Radio(private val symphony: Symphony) : Symphony.Hooks {
    sealed class Events {
        sealed class Player : Events() {
            object Staged : Player()
            object Started : Player()
            object Stopped : Player()
            object Paused : Player()
            object Resumed : Player()
            object Seeked : Player()
            object Ended : Player()
        }

        sealed class Queue : Events() {
            object Modified : Queue()
            object IndexChanged : Queue()
            object Cleared : Queue()
        }

        sealed class QueueOption : Events() {
            object LoopModeChanged : QueueOption()
            object ShuffleModeChanged : QueueOption()
            object SleepTimerChanged : QueueOption()
            object SpeedChanged : QueueOption()
            object PitchChanged : QueueOption()
            object PauseOnCurrentSongEndChanged : QueueOption()
        }
    }

    data class SleepTimer(
        val duration: Long,
        val endsAt: Long,
        val timer: Timer,
        var quitOnEnd: Boolean,
    )

    val onUpdate = Eventer<Events>()
    val queue = RadioQueue(symphony)
    val shorty = RadioShorty(symphony)
    val session = RadioSession(symphony)
    var observatory = RadioObservatory(symphony)

    private val focus = RadioFocus(symphony)
    private val nativeReceiver = RadioNativeReceiver(symphony)
    
    private var player: RadioPlayer? = null
    private var fadingPlayer: RadioPlayer? = null
    private var nextPlayer: RadioPlayer? = null

    private var isPauseRequested = false

    val hasPlayer get() = player?.usable == true
    val isPlaying get() = player?.isPlaying == true && !isPauseRequested
    val currentPlaybackPosition get() = player?.playbackPosition
    val currentSpeed get() = player?.speed ?: RadioPlayer.DEFAULT_SPEED
    val currentPitch get() = player?.pitch ?: RadioPlayer.DEFAULT_PITCH
    val audioSessionId get() = player?.audioSessionId
    val onPlaybackPositionUpdate = Eventer<RadioPlayer.PlaybackPosition>()

    var persistedSpeed = RadioPlayer.DEFAULT_SPEED
    var persistedPitch = RadioPlayer.DEFAULT_PITCH
    var sleepTimer: SleepTimer? = null
    var pauseOnCurrentSongEnd = false

    private val wakeLock: PowerManager.WakeLock by lazy {
        val powerManager = symphony.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Overture:PlaybackWakeLock").apply {
            setReferenceCounted(false)
        }
    }

    init {
        nativeReceiver.start()
        onUpdate.subscribe(this::watchQueueUpdates)
    }

    fun ready() {
        attachGrooveListener()
        session.start()
        observatory.start()
    }

    fun destroy() {
        stop()
        observatory.destroy()
        session.destroy()
        nativeReceiver.destroy()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    data class PlayOptions(
        val index: Int = 0,
        val autostart: Boolean = true,
        val startPosition: Long? = null,
        // True when the transition is triggered automatically (crossfade trigger or natural end).
        // False for explicit user-initiated skips (Next / Previous / jumpTo).
        // Controls whether the full fadePlaybackDuration or MANUAL_SKIP_FADE_MS is used.
        val isAutoSkip: Boolean = false,
    )

    fun play(options: PlayOptions) {
        if (!queue.hasSongAt(options.index)) {
            stopCurrentSong()
            onSongFinish(SongFinishSource.Exception)
            return
        }
        
        queue.currentSongIndex = options.index
        val songId = queue.getSongIdAt(options.index)
        val song = songId?.let { symphony.groove.song.get(it) }
        
        if (song == null) {
            queue.remove(options.index, forceAutostart = options.autostart)
            return
        }
        
        try {
            // Overture: Anti-Spam Overlap Fix
            // Detach the current player immediately so it doesn't trigger late events
            val prevPlayer = player
            player = null 
            
            fadingPlayer?.destroy()
            fadingPlayer = null

            if (prevPlayer != null) {
                if (prevPlayer.isPlaying && symphony.settings.fadePlayback.value) {
                    fadingPlayer = prevPlayer
                    fadingPlayer?.setOnPlaybackPositionListener(null)
                    fadingPlayer?.setOnFinishListener(null)
                    fadingPlayer?.setOnCrossfadeTriggerListener(null)
                    fadingPlayer?.setOnIsPlayingChangedListener(null)
                    
                    // Auto-skip: use the full crossfade duration so the outgoing and incoming
                    // tracks overlap smoothly. Manual skip: quick 300 ms fade so the user's
                    // action feels immediate and responsive.
                    val fadeDuration = if (options.isAutoSkip) {
                        (symphony.settings.fadePlaybackDuration.value * 1000).toInt()
                    } else {
                        RadioPlayer.MANUAL_SKIP_FADE_MS
                    }
                    fadingPlayer?.changeVolume(
                        to = RadioPlayer.MIN_VOLUME,
                        durationMs = fadeDuration,
                        curve = RadioEffects.FadeCurve.EQUAL_POWER,
                    ) {
                        fadingPlayer?.destroy()
                        if (fadingPlayer == prevPlayer) fadingPlayer = null
                    }
                } else {
                    // If it was preparing or paused, destroy it instantly to prevent ghost playback
                    prevPlayer.destroy()
                }
            }

            player = nextPlayer?.takeIf {
                when {
                    it.id == song.id -> true
                    else -> {
                        it.destroy()
                        false
                    }
                }
            } ?: RadioPlayer(symphony, song)
            nextPlayer = null
            
            player!!.setOnPreparedListener {
                options.startPosition?.let {
                    if (it > 0L) {
                        seek(it)
                    }
                }
                setSpeed(persistedSpeed, true)
                setPitch(persistedPitch, true)
                isPauseRequested = false
                if (options.autostart) {
                    // Mirror the fade-in duration to match the fade-out so the crossfade
                    // feels symmetrical. Manual skips always use MANUAL_SKIP_FADE_MS.
                    val fadeInMs = if (options.isAutoSkip && symphony.settings.fadePlayback.value) {
                        (symphony.settings.fadePlaybackDuration.value * 1000).toInt()
                    } else {
                        RadioPlayer.MANUAL_SKIP_FADE_MS
                    }
                    start(fadeInMs)
                }
            }
            player!!.setOnPlaybackPositionListener {
                onPlaybackPositionUpdate.dispatch(it)
            }
            player!!.setOnIsPlayingChangedListener { isPlaying ->
                if (isPlaying) {
                    acquireWakeLock()
                    onUpdate.dispatch(if (!player!!.hasPlayedOnce) Events.Player.Started else Events.Player.Resumed)
                } else {
                    releaseWakeLock()
                    onUpdate.dispatch(Events.Player.Paused)
                }
            }
            player!!.setOnCrossfadeTriggerListener {
                if (!pauseOnCurrentSongEnd) {
                    val (nextSongIndex, autostart) = getNextSong(SongFinishSource.Finish)
                    if (autostart) {
                        // This is an automatic transition: use the full fadePlaybackDuration
                        // so the outgoing track and the incoming track overlap (true crossfade).
                        play(PlayOptions(index = nextSongIndex, autostart = true, isAutoSkip = true))
                    }
                }
            }
            player!!.setOnFinishListener {
                onSongFinish(SongFinishSource.Finish)
            }
            player!!.setOnErrorListener { what, extra ->
                Logger.warn(
                    "Radio",
                    "skipping song ${queue.currentSongId} (${queue.currentSongIndex}) due to $what + $extra"
                )
                val failedIndex = queue.currentSongIndex
                if (queue.hasSongAt(failedIndex)) {
                    queue.remove(failedIndex, forceAutostart = true)
                } else {
                    stopCurrentSong()
                }
            }
            player!!.prepare()
            prepareNextPlayer()
            onUpdate.dispatch(Events.Player.Staged)
        } catch (err: Exception) {
            Logger.warn(
                "Radio",
                "skipping song ${queue.currentSongId} (${queue.currentSongIndex})",
                err,
            )
            val failedIndex = queue.currentSongIndex
            if (queue.hasSongAt(failedIndex)) {
                queue.remove(failedIndex, forceAutostart = options.autostart)
            }
        }
    }

    private fun prepareNextPlayer() {
        if (!symphony.settings.gaplessPlayback.value) {
            return
        }
        val (nextSongIndex) = getNextSong(SongFinishSource.Finish)
        val song = queue.getSongIdAt(nextSongIndex)?.let { symphony.groove.song.get(it) } ?: return
        if (song.id == nextPlayer?.id) {
            return
        }
        try {
            nextPlayer?.destroy()
            nextPlayer = RadioPlayer(symphony, song).also {
                it.prepare()
            }
        } catch (err: Exception) {
            Logger.warn(
                "Radio",
                "unable to prepare next player ${song.id} (${nextSongIndex})",
                err,
            )
        }
    }

    fun resume() {
        isPauseRequested = false
        start()
    }

    private fun start(fadeInMs: Int = RadioPlayer.MANUAL_SKIP_FADE_MS) {
        player?.let {
            val hasFocus = focus.requestFocus()
            if (symphony.settings.requireAudioFocus.value && !hasFocus) {
                return
            }
            acquireWakeLock()
            if (it.fadePlayback) {
                it.changeVolumeInstant(RadioPlayer.MIN_VOLUME)
                it.changeVolume(RadioPlayer.MAX_VOLUME, durationMs = fadeInMs) {}
            } else {
                it.changeVolumeInstant(RadioPlayer.MAX_VOLUME)
            }
            it.start()
        }
    }

    fun pause() = pause {}

    private fun pause(forceFade: Boolean = false, onFinish: () -> Unit) {
        fadingPlayer?.destroy()
        fadingPlayer = null
        
        player?.let {
            if (!it.isPlaying) {
                return@let
            }
            isPauseRequested = true
            
            val duration = if (forceFade) (symphony.settings.fadePlaybackDuration.value * 1000).toInt() else 300
            
            it.changeVolume(
                to = RadioPlayer.MIN_VOLUME,
                durationMs = duration,
                curve = RadioEffects.FadeCurve.LINEAR
            ) { _ ->
                it.pause()
                releaseWakeLock()
                focus.abandonFocus()
                onFinish()
            }
        } ?: onFinish()
    }

    fun pauseInstant() {
        fadingPlayer?.destroy()
        fadingPlayer = null
        
        player?.let {
            isPauseRequested = true
            it.pause()
            releaseWakeLock()
        }
    }

    fun stop(ended: Boolean = true) {
        stopCurrentSong()
        queue.reset()
        clearSleepTimer()
        persistedSpeed = RadioPlayer.DEFAULT_SPEED
        persistedPitch = RadioPlayer.DEFAULT_PITCH
        isPauseRequested = false
        releaseWakeLock()
        if (ended) onUpdate.dispatch(Events.Player.Ended)
    }

    private fun acquireWakeLock() {
        if (!wakeLock.isHeld) {
            wakeLock.acquire(10 * 60 * 1000L /*10 minutes fallback*/)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    fun jumpTo(index: Int) = play(PlayOptions(index = index))
    fun jumpToPrevious() = jumpTo(queue.currentSongIndex - 1)
    fun jumpToNext() = jumpTo(queue.currentSongIndex + 1)
    fun canJumpToPrevious() = queue.hasSongAt(queue.currentSongIndex - 1)
    fun canJumpToNext() = queue.hasSongAt(queue.currentSongIndex + 1)

    fun seek(position: Long) {
        player?.let {
            it.seek(position.toInt())
            onUpdate.dispatch(Events.Player.Seeked)
        }
    }

    fun duck() {
        player?.let {
            it.changeVolume(RadioPlayer.DUCK_VOLUME, durationMs = 300) {}
        }
    }

    fun restoreVolume() {
        player?.let {
            it.changeVolume(RadioPlayer.MAX_VOLUME, durationMs = 300) {}
        }
    }

    fun setSpeed(speed: Float, persist: Boolean) {
        player?.let {
            it.changeSpeed(speed)
            if (persist) {
                persistedSpeed = speed
            }
            onUpdate.dispatch(Events.QueueOption.SpeedChanged)
        }
    }

    fun setPitch(pitch: Float, persist: Boolean) {
        player?.let {
            it.changePitch(pitch)
            if (persist) {
                persistedPitch = pitch
            }
            onUpdate.dispatch(Events.QueueOption.PitchChanged)
        }
    }

    fun setSleepTimer(
        duration: Long,
        quitOnEnd: Boolean,
    ) {
        val endsAt = System.currentTimeMillis() + duration
        val timer = Timer()
        timer.schedule(
            kotlin.concurrent.timerTask {
                val shouldQuit = sleepTimer?.quitOnEnd ?: quitOnEnd
                clearSleepTimer()
                symphony.groove.coroutineScope.launch(Dispatchers.Main) {
                    pause(forceFade = true) {
                        if (shouldQuit) {
                            symphony.closeApp?.invoke()
                        }
                    }
                }
            },
            Date.from(Instant.ofEpochMilli(endsAt)),
        )
        clearSleepTimer()
        sleepTimer = SleepTimer(
            duration = duration,
            endsAt = endsAt,
            timer = timer,
            quitOnEnd = quitOnEnd,
        )
        onUpdate.dispatch(Events.QueueOption.SleepTimerChanged)
    }

    fun clearSleepTimer() {
        sleepTimer?.timer?.cancel()
        sleepTimer = null
        onUpdate.dispatch(Events.QueueOption.SleepTimerChanged)
    }

    @JvmName("setPauseOnCurrentSongEndTo")
    fun setPauseOnCurrentSongEnd(value: Boolean) {
        pauseOnCurrentSongEnd = value
        onUpdate.dispatch(Events.QueueOption.PauseOnCurrentSongEndChanged)
    }

    private fun stopCurrentSong() {
        fadingPlayer?.destroy()
        fadingPlayer = null
        
        player?.let {
            val p = it
            player = null
            p.setOnPlaybackPositionListener(null)
            p.setOnCrossfadeTriggerListener(null)
            p.setOnIsPlayingChangedListener(null)
            p.changeVolume(RadioPlayer.MIN_VOLUME, durationMs = 0) { _ ->
                p.stop()
                onUpdate.dispatch(Events.Player.Stopped)
            }
        }
    }

    private enum class SongFinishSource {
        Finish,
        Exception,
    }

    private fun onSongFinish(source: SongFinishSource) {
        stopCurrentSong()
        if (queue.isEmpty()) {
            queue.currentSongIndex = -1
            return
        }
        var (nextSongIndex, autostart) = getNextSong(source)
        if (pauseOnCurrentSongEnd) {
            autostart = false
            setPauseOnCurrentSongEnd(false)
        }
        // SongFinishSource.Finish = natural end → auto-skip semantics (may fade).
        // SongFinishSource.Exception = error skip → treat as manual (quick 300 ms fade).
        play(PlayOptions(
            index = nextSongIndex,
            autostart = autostart,
            isAutoSkip = source == SongFinishSource.Finish,
        ))
    }

    private fun getNextSong(source: SongFinishSource): Pair<Int, Boolean> {
        if (queue.isEmpty()) {
            return -1 to false
        }
        var autostart: Boolean
        var nextSongIndex: Int
        when (queue.currentLoopMode) {
            RadioQueue.LoopMode.Song -> {
                nextSongIndex = queue.currentSongIndex
                autostart = source == SongFinishSource.Finish
                if (!queue.hasSongAt(nextSongIndex)) {
                    nextSongIndex = 0
                    autostart = false
                }
            }

            else -> {
                nextSongIndex = when (source) {
                    SongFinishSource.Finish -> queue.currentSongIndex + 1
                    SongFinishSource.Exception -> queue.currentSongIndex
                }
                autostart = true
                if (!queue.hasSongAt(nextSongIndex)) {
                    nextSongIndex = 0
                    autostart = queue.currentLoopMode == RadioQueue.LoopMode.Queue
                }
            }
        }
        return nextSongIndex to autostart
    }

    private fun attachGrooveListener() {
        // CRITICAL: launch on Dispatchers.Main so that RadioPlayer() constructs
        // ExoPlayer with the Main Looper. Dispatchers.Default (the groove scope's
        // dispatcher) has no Looper → ExoPlayer.Builder.build() throws
        // IllegalStateException that is silently caught in play(), leaving the
        // player null and the app in a frozen/stale state after queue restore.
        symphony.groove.coroutineScope.launch(Dispatchers.Main) {
            symphony.groove.readyDeferred.await()
            restorePreviousQueue()
        }
    }

    private fun restorePreviousQueue() {
        if (!queue.isEmpty()) {
            return
        }
        symphony.settings.previousSongQueue.value?.let { previous ->
            var currentSongIndex = previous.currentSongIndex
            var playedDuration = previous.playedDuration
            val originalQueue = mutableListOf<String>()
            val currentQueue = mutableListOf<String>()
            previous.originalQueue.forEach { songId ->
                if (symphony.groove.song.get(songId) != null) {
                    originalQueue.add(songId)
                }
            }
            previous.currentQueue.forEachIndexed { i, songId ->
                if (symphony.groove.song.get(songId) != null) {
                    currentQueue.add(songId)
                } else {
                    if (i < currentSongIndex) currentSongIndex--
                }
            }
            if (originalQueue.isEmpty() || hasPlayer) {
                return@let
            }
            if (currentSongIndex >= originalQueue.size) {
                currentSongIndex = 0
                playedDuration = 0
            }
            queue.restore(
                RadioQueue.Serialized(
                    currentSongIndex = currentSongIndex,
                    playedDuration = playedDuration,
                    originalQueue = originalQueue,
                    currentQueue = currentQueue,
                    shuffled = previous.shuffled,
                )
            )
        }
    }

    internal fun watchQueueUpdates(event: Events) {
        if (event !is Events.Queue) {
            return
        }
        prepareNextPlayer()
    }

    override fun onSymphonyReady() {
        ready()
    }

    override fun onSymphonyActivityResume() {
        // The activity is back on screen. If a player is active, force-sync the
        // Observatory StateFlows and rebuild the MediaSession / notification so
        // the UI never shows a stale song or play-state after returning from bg.
        if (!hasPlayer) return
        symphony.groove.coroutineScope.launch(Dispatchers.Main) {
            observatory.syncAll()
            session.forceUpdate()
        }
    }

    override fun onSymphonyDestroy() {
        saveCurrentQueue()
        destroy()
    }

    override fun onSymphonyActivityPause() {
        saveCurrentQueue()
    }

    override fun onSymphonyActivityDestroy() {
        saveCurrentQueue()
    }

    private fun saveCurrentQueue() {
        if (queue.isEmpty()) {
            return
        }
        symphony.settings.previousSongQueue.setValue(
            RadioQueue.Serialized.create(
                queue = queue,
                playbackPosition = currentPlaybackPosition ?: RadioPlayer.PlaybackPosition.zero
            )
        )
    }
}