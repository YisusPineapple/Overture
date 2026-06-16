package io.github.zyrouge.symphony.ui.view.nowPlaying

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.ui.helpers.ScreenOrientation
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.NowPlayingData
import io.github.zyrouge.symphony.ui.view.NowPlayingDefaults
import io.github.zyrouge.symphony.ui.view.NowPlayingLyricsLayout
import io.github.zyrouge.symphony.ui.view.NowPlayingStates
import kotlinx.coroutines.flow.MutableStateFlow

internal val defaultHorizontalPadding = 20.dp

@Composable
fun NowPlayingBody(context: ViewContext, data: NowPlayingData) {
    val states = remember {
        NowPlayingStates(
            showLyrics = MutableStateFlow(
                data.lyricsLayout == NowPlayingLyricsLayout.ReplaceArtwork && NowPlayingDefaults.showLyrics
            ),
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val orientation = ScreenOrientation.fromConstraints(this@BoxWithConstraints)
        val glassOverlayColor = MaterialTheme.colorScheme.background.copy(alpha = 0.75f)

        // M3E Dynamic Ambient Background (Liquid Glass)
        Crossfade(
            targetState = data.song,
            animationSpec = tween(1000), // Smooth 1-second crossfade between songs
            label = "AmbientBackground"
        ) { song ->
            AsyncImage(
                model = song.createArtworkImageRequest(context.symphony).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(80.dp) // Hardware accelerated blur (API 31+)
                    .drawWithContent {
                        drawContent()
                        // Liquid Glass overlay: adapts to Light/Dark mode automatically
                        // Zero-cost fallback for Android 9-11
                        drawRect(glassOverlayColor)
                    }
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent, // Crucial: Lets the ambient background shine through
            topBar = {
                if (orientation.isPortrait) {
                    NowPlayingAppBar(context)
                }
            },
            content = { contentPadding ->
                Box(modifier = Modifier.padding(contentPadding)) {
                    when (orientation) {
                        ScreenOrientation.PORTRAIT -> Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                NowPlayingBodyCover(context, data, states, orientation)
                            }
                            Column {
                                NowPlayingBodyContent(context, data)
                                NowPlayingBodyBottomBar(context, data, states)
                            }
                        }

                        ScreenOrientation.LANDSCAPE -> Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(top = 12.dp, bottom = 20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                NowPlayingBodyCover(context, data, states, orientation)
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                Column {
                                    NowPlayingLandscapeAppBar(context)
                                    Box(modifier = Modifier.weight(1f))
                                    NowPlayingBodyContent(context, data)
                                    NowPlayingBodyBottomBar(context, data, states)
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}
