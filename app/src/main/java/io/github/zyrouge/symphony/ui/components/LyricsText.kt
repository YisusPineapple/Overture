package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    LaunchedEffect(song) {
        snapshotFlow { song }
            .distinctUntilChanged()
            .collect { song ->
                lyricsState = 1
                lyricsSongId = song?.id
                coroutineScope.launch {
                    lyrics = song?.let { song ->
                        context.symphony.groove.song.getLyrics(song)?.let {
                            TimedContent.fromLyrics(it)
                        }
                    }
                    lyricsState = 2
                }
            }
    }

    AnimatedContent(
        label = "lyrics-text",
        targetState = lyricsState to lyrics,
        transitionSpec = {
            FadeTransition.enterTransition()
                .togetherWith(FadeTransition.exitTransition())
        },
    ) { targetState ->
        val targetLyricsState = targetState.first
        val targetLyrics = targetState.second

        when {
            targetLyricsState == 2 && targetLyrics != null -> TimedContentText(
                context = context,
                content = targetLyrics,
                padding = padding,
                style = style,
                onSeek = {
                    targetLyrics.lines.getOrNull(it)?.time?.let { to ->
                        context.symphony.radio.seek(to)
                    }
                }
            )

            else -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (targetLyricsState == 1) context.symphony.t.Loading
                    else context.symphony.t.NoLyrics
                )
            }
        }
    }
}