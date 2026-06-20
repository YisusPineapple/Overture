package io.github.zyrouge.symphony.services.groove

import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.repositories.AlbumArtistRepository
import io.github.zyrouge.symphony.services.groove.repositories.AlbumRepository
import io.github.zyrouge.symphony.services.groove.repositories.ArtistRepository
import io.github.zyrouge.symphony.services.groove.repositories.GenreRepository
import io.github.zyrouge.symphony.services.groove.repositories.PlaylistRepository
import io.github.zyrouge.symphony.services.groove.repositories.SongRepository
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Groove(private val symphony: Symphony) : Symphony.Hooks {
    enum class Kind {
        SONG,
        ALBUM,
        ARTIST,
        ALBUM_ARTIST,
        GENRE,
        PLAYLIST,
    }

    val coroutineScope = CoroutineScope(Dispatchers.Default)
    var readyDeferred = CompletableDeferred<Boolean>()

    val exposer = MediaExposer(symphony)
    val song = SongRepository(symphony)
    val album = AlbumRepository(symphony)
    val artist = ArtistRepository(symphony)
    val albumArtist = AlbumArtistRepository(symphony)
    val genre = GenreRepository(symphony)
    val playlist = PlaylistRepository(symphony)

    // Overture: Renamed to performFetch to avoid Conflicting Overloads with the public fetch()
    private suspend fun performFetch(options: FetchOptions) {
        val cachedSongsCount = loadCachedLibrary()
        
        if (cachedSongsCount == 0 || options.forceRescan) {
            coroutineScope.launch {
                awaitAll(
                    async { exposer.fetch() },
                    async { playlist.fetch() },
                )
            }.join()
        } else {
            playlist.fetch()
        }
    }

    private suspend fun loadCachedLibrary(): Int {
        return try {
            val cachedSongs = withContext(Dispatchers.IO) {
                symphony.database.songCache.entriesPathMapped().values
            }
            if (cachedSongs.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    cachedSongs.forEach { song ->
                        this@Groove.song.onSong(song)
                        this@Groove.album.onSong(song)
                        this@Groove.artist.onSong(song)
                        this@Groove.genre.onSong(song)
                        this@Groove.albumArtist.onSong(song)
                    }
                    playlist.onScanFinish()
                }
                Logger.warn("Groove", "Loaded ${cachedSongs.size} songs from local DB cache instantly.")
            }
            cachedSongs.size
        } catch (err: Exception) {
            Logger.error("Groove", "Failed to load cached library on start", err)
            0
        }
    }

    private suspend fun reset() {
        coroutineScope.launch {
            awaitAll(
                async { exposer.reset() },
                async { albumArtist.reset() },
                async { album.reset() },
                async { artist.reset() },
                async { genre.reset() },
                async { playlist.reset() },
                async { song.reset() },
            )
        }.join()
    }

    private suspend fun clearCache() {
        symphony.database.songCache.clear()
        symphony.database.artworkCache.clear()
        symphony.database.lyricsCache.clear()
    }

    data class FetchOptions(
        val resetInMemoryCache: Boolean = false,
        val resetPersistentCache: Boolean = false,
        val forceRescan: Boolean = false,
    )

    fun fetch(options: FetchOptions) {
        coroutineScope.launch {
            if (options.resetInMemoryCache) {
                reset()
            }
            if (options.resetPersistentCache) {
                clearCache()
            }
            performFetch(options)
        }
    }

    override fun onSymphonyReady() {
        coroutineScope.launch {
            fetch(FetchOptions(forceRescan = false))
            readyDeferred.complete(true)
        }
    }
}