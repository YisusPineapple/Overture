package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateTopPadding
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.ui.helpers.FadeTransition
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.utils.TimedContent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun LyricsText(
    context: ViewContext,
    padding: PaddingValues,
    style: TimedContentTextStyle,
) {
    val coroutineScope = rememberCoroutineScope()
    val queue by context.symphony.radio.observatory.queue.collectAsState()
    val queueIndex by context.symphony.radio.observatory.queueIndex.collectAsState()
    val song by remember(queue, queueIndex) {
        derivedStateOf {
            queue.getOrNull(queueIndex)?.let { context.symphony.groove.song.get(it) }
        }
    }
    var lyricsState by remember { mutableIntStateOf(0) }
    var lyricsSongId by remember { mutableStateOf<String?>(null) }
    var lyrics by remember { mutableStateOf<TimedContent?>(null) }
    // Per-song offset from the Room entity. Updated alongside lyricsSongId
    // so the AnimatedContent target always carries the correct offset.
    var lyricsSongOffset by remember { mutableLongStateOf(0L) }

    LaunchedEffect(song) {
        snapshotFlow { song }
            .distinctUntilChanged()
            .collect { currentSong ->
                lyricsState = 1
                lyricsSongId = currentSong?.id
                lyricsSongOffset = currentSong?.lyricsOffset ?: 0L
                coroutineScope.launch {
                    lyrics = currentSong?.let { s ->
                        context.symphony.groove.song.getLyrics(s)?.let {
                            TimedContent.fromLyrics(it)
                        }
                    }
                    lyricsState = 2
                }
            }
    }

    AnimatedContent(
        label = "lyrics-text",
        // Include lyricsSongOffset so the composable recomposes when the user
        // adjusts the per-song offset in the settings.
        targetState = Triple(lyricsState, lyrics, lyricsSongOffset),
        transitionSpec = {
            FadeTransition.enterTransition()
                .togetherWith(FadeTransition.exitTransition())
        },
    ) { (targetLyricsState, targetLyrics, targetSongOffset) ->
        when {
            targetLyricsState == 2 && targetLyrics != null -> {
                if (!targetLyrics.isSynced) {
                    // Unsynced (plain-text) lyrics: render as a static scrollable
                    // column at full opacity. TimedContentText is not used here
                    // because it renders everything at unfocusedOpacity, making
                    // plain lyrics nearly invisible and impossible to read.
                    StaticLyricsText(
                        content = targetLyrics,
                        padding = padding,
                        style = style,
                    )
                } else {
                    TimedContentText(
                        context = context,
                        content = targetLyrics,
                        padding = padding,
                        style = style,
                        songOffset = targetSongOffset,
                        onSeek = {
                            targetLyrics.lines.getOrNull(it)?.time?.let { to ->
                                context.symphony.radio.seek(to)
                            }
                        }
                    )
                }
            }

            else -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                IconTextBody(
                    icon = { modifier ->
                        Icon(
                            if (targetLyricsState == 1) Icons.Filled.HourglassEmpty else Icons.Filled.Lyrics,
                            null,
                            modifier = modifier
                        )
                    },
                    content = {
                        Text(
                            if (targetLyricsState == 1) context.symphony.t.Loading
                            else context.symphony.t.NoLyrics
                        )
                    }
                )
            }
        }
    }
}

/**
 * Renders unsynced (plain-text) lyrics as a simple scrollable column.
 * All lines are shown at full opacity with the [style.highlighted] style;
 * there is no auto-scroll, no active-line tracking, and no tap-to-seek.
 */
@Composable
private fun StaticLyricsText(
    content: TimedContent,
    padding: PaddingValues,
    style: TimedContentTextStyle,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            end = padding.calculateEndPadding(layoutDirection),
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(style.spacing),
    ) {
        item { Spacer(modifier = Modifier.height(64.dp)) }
        items(content.lines) { line ->
            if (line.text.isNotBlank()) {
                Text(
                    text = line.text,
                    modifier = Modifier.fillMaxWidth(),
                    style = style.highlighted,
                )
            }
        }
        item { Spacer(modifier = Modifier.height(64.dp)) }
    }
}