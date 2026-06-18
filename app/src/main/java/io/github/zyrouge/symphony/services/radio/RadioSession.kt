package io.github.zyrouge.symphony.services.radio

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.activity.result.contract.ActivityResultContract
import androidx.palette.graphics.Palette
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RadioSession(val symphony: Symphony) {
    data class UpdateRequest(
        val song: Song,
        val artworkUri: Uri,
        val artworkBitmap: Bitmap,
        val playbackPosition: RadioPlayer.PlaybackPosition,
        val isPlaying: Boolean,
        val isFavorite: Boolean,
    )

    internal val mediaSession = MediaSessionCompat(symphony.applicationContext, MEDIA_SESSION_ID)
    private val artworkCacher = RadioArtworkCacher(symphony)
    private val notification = RadioNotification(symphony)

    private var currentSongId: String? = null
    private var receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { action ->
                handleAction(action)
            }
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            symphony.applicationContext.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(ACTION_PLAY_PAUSE)
                    addAction(ACTION_PREVIOUS)
                    addAction(ACTION_NEXT)
                    addAction(ACTION_FAVORITE)
                    addAction(ACTION_STOP)
                },
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            symphony.applicationContext.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(ACTION_PLAY_PAUSE)
                    addAction(ACTION_PREVIOUS)
                    addAction(ACTION_NEXT)
                    addAction(ACTION_FAVORITE)
                    addAction(ACTION_STOP)
                },
            )
        }
        mediaSession.setCallback(
            object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    super.onPlay()
                    handleAction(ACTION_PLAY_PAUSE)
                }

                override fun onPause() {
                    super.onPause()
                    handleAction(ACTION_PLAY_PAUSE)
                }

                override fun onSkipToPrevious() {
                    super.onSkipToPrevious()
                    handleAction(ACTION_PREVIOUS)
                }

                override fun onSkipToNext() {
                    super.onSkipToNext()
                    handleAction(ACTION_NEXT)
                }

                override fun onStop() {
                    super.onStop()
                    handleAction(ACTION_STOP)
                }

                override fun onSeekTo(pos: Long) {
                    super.onSeekTo(pos)
                    symphony.radio.seek(pos)
                }

                override fun onRewind() {
                    super.onRewind()
                    val duration = symphony.settings.seekBackDuration.value
                    symphony.radio.shorty.seekFromCurrent(-duration)
                }

                override fun onFastForward() {
                    super.onFastForward()
                    val duration = symphony.settings.seekForwardDuration.value
                    symphony.radio.shorty.seekFromCurrent(duration)
                }

                override fun onMediaButtonEvent(intent: Intent?): Boolean {
                    val handled = super.onMediaButtonEvent(intent)
                    if (handled) {
                        return true
                    }
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent?.getParcelableExtra(
                            Intent.EXTRA_KEY_EVENT,
                            KeyEvent::class.java,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    return when (keyEvent?.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                            -> {
                            handleAction(ACTION_PREVIOUS)
                            true
                        }

                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            handleAction(ACTION_NEXT)
                            true
                        }

                        KeyEvent.KEYCODE_MEDIA_CLOSE,
                        KeyEvent.KEYCODE_MEDIA_STOP,
                            -> {
                            handleAction(ACTION_STOP)
                            true
                        }

                        else -> false
                    }
                }
            }
        )
        notification.start()
        symphony.radio.onUpdate.subscribe {
            when (it) {
                Radio.Events.Player.Ended -> cancel()
                is Radio.Events.Player -> update()
                else -> {}
            }
        }
    }

    fun handleAction(action: String) {
        when (action) {
            ACTION_PLAY_PAUSE -> symphony.radio.shorty.playPause()
            ACTION_PREVIOUS -> symphony.radio.shorty.previous()
            ACTION_NEXT -> symphony.radio.shorty.skip()
            ACTION_FAVORITE -> {
                val songId = symphony.radio.queue.currentSongId ?: return
                val isFavorite = symphony.groove.playlist.getFavorites()
                    .getSongIds(symphony)
                    .contains(songId)
                
                if (isFavorite) {
                    symphony.groove.playlist.unfavorite(songId)
                } else {
                    symphony.groove.playlist.favorite(songId)
                }
                update()
            }
            ACTION_STOP -> symphony.radio.stop()
        }
    }

    fun cancel() {
        notification.cancel()
        mediaSession.isActive = false
    }

    fun destroy() {
        cancel()
        symphony.applicationContext.unregisterReceiver(receiver)
    }

    fun createEqualizerActivityContract() = object : ActivityResultContract<Unit, Unit>() {
        override fun createIntent(
            context: Context,
            input: Unit,
        ) = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, symphony.applicationContext.packageName)
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, symphony.radio.audioSessionId)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }

        override fun parseResult(
            resultCode: Int,
            intent: Intent?,
        ) {
        }
    }

    private fun update() {
        symphony.groove.coroutineScope.launch {
            updateAsync()
        }
    }

    private suspend fun updateAsync() {
        val song = symphony.radio.queue.currentSongId
            ?.let { symphony.groove.song.get(it) } ?: return
        currentSongId = song.id
        val artworkUri = symphony.groove.song.getArtworkUri(song.id)
        val artworkBitmap = artworkCacher.getArtwork(song)
        
        val safeBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && artworkBitmap.config == Bitmap.Config.HARDWARE) {
            artworkBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            artworkBitmap
        }
        
        val palette = Palette.from(safeBitmap).generate()
        val dominant = palette.getVibrantColor(palette.getDominantColor(0))
        symphony.radio.observatory.setDominantColor(if (dominant != 0) dominant else null)
        
        var playbackPosition = RadioPlayer.PlaybackPosition(played = 0L, total = song.duration)
        var isPlaying = false
        
        withContext(Dispatchers.Main) {
            playbackPosition = symphony.radio.currentPlaybackPosition
                ?: RadioPlayer.PlaybackPosition(played = 0L, total = song.duration)
            isPlaying = symphony.radio.isPlaying
        }
        
        val isFavorite = symphony.groove.playlist.getFavorites()
            .getSongIds(symphony)
            .contains(song.id)

        if (currentSongId != song.id) {
            return
        }
        val req = UpdateRequest(
            song = song,
            artworkUri = artworkUri,
            artworkBitmap = artworkBitmap,
            playbackPosition = playbackPosition,
            isPlaying = isPlaying,
            isFavorite = isFavorite,
        )
        updateSession(req)
        notification.update(req)
    }

    private fun updateSession(req: UpdateRequest) {
        ensureEnabled()
        mediaSession.run {
            setMetadata(
                MediaMetadataCompat.Builder().run {
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, req.song.title)
                    if (req.song.artists.isNotEmpty()) {
                        putString(
                            MediaMetadataCompat.METADATA_KEY_ARTIST,
                            req.song.artists.joinToString()
                        )
                    }
                    putString(MediaMetadataCompat.METADATA_KEY_ALBUM, req.song.album)
                    req.artworkBitmap.let {
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
                    }
                    putLong(
                        MediaMetadataCompat.METADATA_KEY_DURATION,
                        req.playbackPosition.total.toLong()
                    )
                    build()
                }
            )
            
            val favAction = PlaybackStateCompat.CustomAction.Builder(
                ACTION_FAVORITE,
                symphony.t.Favorite,
                if (req.isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart
            ).build()

            val closeAction = PlaybackStateCompat.CustomAction.Builder(
                ACTION_STOP,
                symphony.t.Stop,
                R.drawable.material_icon_close
            ).build()

            setPlaybackState(
                PlaybackStateCompat.Builder().run {
                    setState(
                        when {
                            req.isPlaying -> PlaybackStateCompat.STATE_PLAYING
                            else -> PlaybackStateCompat.STATE_PAUSED
                        },
                        req.playbackPosition.played.toLong(),
                        1f
                    )
                    addCustomAction(favAction)
                    addCustomAction(closeAction)
                    setActions(
                        PlaybackStateCompat.ACTION_PLAY
                                or PlaybackStateCompat.ACTION_PAUSE
                                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                or PlaybackStateCompat.ACTION_STOP
                                or PlaybackStateCompat.ACTION_REWIND
                                or PlaybackStateCompat.ACTION_FAST_FORWARD
                                or PlaybackStateCompat.ACTION_SEEK_TO
                    )
                    build()
                }
            )
        }
    }

    private fun ensureEnabled() {
        if (!mediaSession.isActive) {
            mediaSession.isActive = true
        }
    }

    companion object {
        val MEDIA_SESSION_ID = "${R.string.app_name}_media_session"

        val ACTION_PLAY_PAUSE = "${R.string.app_name}_play_pause"
        val ACTION_PREVIOUS = "${R.string.app_name}_previous"
        val ACTION_NEXT = "${R.string.app_name}_next"
        val ACTION_FAVORITE = "${R.string.app_name}_favorite"
        val ACTION_STOP = "${R.string.app_name}_stop"
    }
}