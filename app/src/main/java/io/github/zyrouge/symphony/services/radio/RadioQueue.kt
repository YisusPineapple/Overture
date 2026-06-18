package io.github.zyrouge.symphony.services.radio

import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.utils.concurrentListOf

class RadioQueue(private val symphony: Symphony) {
    enum class LoopMode {
        None,
        Queue,
        Song;

        companion object {
            val values = enumValues<LoopMode>()
        }
    }

    val originalQueue = concurrentListOf<String>()
    val currentQueue = concurrentListOf<String>()

    var currentSongIndex = -1
        internal set(value) {
            field = value
            symphony.radio.onUpdate.dispatch(Radio.Events.Queue.IndexChanged)
        }

    var currentShuffleMode = false
        private set(value) {
            field = value
            symphony.radio.onUpdate.dispatch(Radio.Events.QueueOption.ShuffleModeChanged)
        }

    var currentLoopMode = LoopMode.None
        private set(value) {
            field = value
            symphony.radio.onUpdate.dispatch(Radio.Events.QueueOption.LoopModeChanged)
        }

    val currentSongId: String?
        get() = getSongIdAt(currentSongIndex)

    fun hasSongAt(index: Int) = index > -1 && index < currentQueue.size
    fun getSongIdAt(index: Int) = if (hasSongAt(index)) currentQueue[index] else null

    fun reset() {
        originalQueue.clear()
        currentQueue.clear()
        currentSongIndex = -1
        symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Cleared)
    }

    fun add(
        songIds: List<String>,
        index: Int? = null,
        options: Radio.PlayOptions = Radio.PlayOptions(),
    ) {
        index?.let {
            originalQueue.addAll(it, songIds)
            currentQueue.addAll(it, songIds)
            if (it <= currentSongIndex) {
                currentSongIndex += songIds.size
            }
        } ?: run {
            originalQueue.addAll(songIds)
            currentQueue.addAll(songIds)
        }
        afterAdd(options)
    }

    fun add(
        songId: String,
        index: Int? = null,
        options: Radio.PlayOptions = Radio.PlayOptions(),
    ) = add(listOf(songId), index, options)

    private fun afterAdd(options: Radio.PlayOptions) {
        if (!symphony.radio.hasPlayer) {
            symphony.radio.play(options)
        }
        symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
    }

    fun remove(index: Int, forceAutostart: Boolean? = null) {
        if (!hasSongAt(index)) return
        
        originalQueue.removeAt(index)
        currentQueue.removeAt(index)
        symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
        
        if (currentSongIndex == index) {
            if (currentQueue.isEmpty()) {
                symphony.radio.stop()
            } else {
                val nextIndex = if (currentSongIndex >= currentQueue.size) 0 else currentSongIndex
                val autostart = forceAutostart ?: symphony.radio.isPlaying
                symphony.radio.play(Radio.PlayOptions(index = nextIndex, autostart = autostart))
            }
        } else if (index < currentSongIndex) {
            currentSongIndex--
        }
    }

    fun remove(indices: List<Int>) {
        var deflection = 0
        var currentSongRemoved = false
        val sortedIndices = indices.filter { hasSongAt(it) }.sortedDescending()
        
        if (sortedIndices.isEmpty()) return
        
        for (i in sortedIndices) {
            val index = i - deflection
            originalQueue.removeAt(index)
            currentQueue.removeAt(index)
            when {
                i < currentSongIndex -> deflection++
                i == currentSongIndex -> currentSongRemoved = true
            }
        }
        currentSongIndex -= deflection
        symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
        
        if (currentSongRemoved) {
            if (currentQueue.isEmpty()) {
                symphony.radio.stop()
            } else {
                val nextIndex = if (currentSongIndex >= currentQueue.size) 0 else currentSongIndex
                val wasPlaying = symphony.radio.isPlaying
                symphony.radio.play(Radio.PlayOptions(index = nextIndex, autostart = wasPlaying))
            }
        }
    }

    fun move(from: Int, to: Int) {
        if (from == to || !hasSongAt(from) || !hasSongAt(to)) return
        
        val itemOriginal = originalQueue.removeAt(from)
        originalQueue.add(to, itemOriginal)
        
        val itemCurrent = currentQueue.removeAt(from)
        currentQueue.add(to, itemCurrent)

        if (currentSongIndex == from) {
            currentSongIndex = to
        } else if (currentSongIndex in (from + 1)..to) {
            currentSongIndex--
        } else if (currentSongIndex in to until from) {
            currentSongIndex++
        }
        symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
    }

    fun setLoopMode(loopMode: LoopMode) {
        currentLoopMode = loopMode
    }

    fun toggleLoopMode() {
        val next = (currentLoopMode.ordinal + 1) % LoopMode.values.size
        setLoopMode(LoopMode.values[next])
    }

    fun toggleShuffleMode() = setShuffleMode(!currentShuffleMode)

    fun setShuffleMode(to: Boolean) {
        currentShuffleMode = to
        if (currentQueue.isNotEmpty()) {
            val currentSongId = getSongIdAt(currentSongIndex) ?: getSongIdAt(0)!!
            currentSongIndex = if (currentShuffleMode) {
                val newQueue = originalQueue.toMutableList()
                newQueue.remove(currentSongId)
                newQueue.shuffle()
                newQueue.add(0, currentSongId)
                currentQueue.clear()
                currentQueue.addAll(newQueue)
                0
            } else {
                currentQueue.clear()
                currentQueue.addAll(originalQueue)
                originalQueue.indexOfFirst { it == currentSongId }.coerceAtLeast(0)
            }
        }
        symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
    }

    fun isEmpty() = originalQueue.isEmpty()

    data class Serialized(
        val currentSongIndex: Int,
        val playedDuration: Long,
        val originalQueue: List<String>,
        val currentQueue: List<String>,
        val shuffled: Boolean,
    ) {
        fun serialize() =
            listOf(
                currentSongIndex.toString(),
                playedDuration.toString(),
                originalQueue.joinToString(","),
                currentQueue.joinToString(","),
                shuffled.toString(),
            ).joinToString(";")

        companion object {
            fun create(queue: RadioQueue, playbackPosition: RadioPlayer.PlaybackPosition) =
                Serialized(
                    currentSongIndex = queue.currentSongIndex,
                    playedDuration = playbackPosition.played,
                    originalQueue = queue.originalQueue.toList(),
                    currentQueue = queue.currentQueue.toList(),
                    shuffled = queue.currentShuffleMode,
                )

            fun parse(data: String): Serialized? {
                try {
                    val semi = data.split(";")
                    return Serialized(
                        currentSongIndex = semi[0].toInt(),
                        playedDuration = semi[1].toLong(),
                        originalQueue = semi[2].split(","),
                        currentQueue = semi[3].split(","),
                        shuffled = semi[4].toBoolean(),
                    )
                } catch (_: Exception) {
                }
                return null
            }
        }
    }

    fun restore(serialized: Serialized) {
        if (serialized.originalQueue.isNotEmpty()) {
            symphony.radio.stop(ended = false)
            originalQueue.clear()
            originalQueue.addAll(serialized.originalQueue)
            currentQueue.clear()
            currentQueue.addAll(serialized.currentQueue)
            symphony.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
            currentShuffleMode = serialized.shuffled
            afterAdd(
                Radio.PlayOptions(
                    index = serialized.currentSongIndex,
                    autostart = false,
                    startPosition = serialized.playedDuration,
                )
            )
        }
    }
}