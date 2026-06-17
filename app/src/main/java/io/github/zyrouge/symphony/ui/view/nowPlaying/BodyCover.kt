package io.github.zyrouge.symphony.ui.view.nowPlaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.components.KeepScreenAwake
import io.github.zyrouge.symphony.ui.components.LyricsText
import io.github.zyrouge.symphony.ui.components.TimedContentTextStyle
import io.github.zyrouge.symphony.ui.components.swipeable
import io.github.zyrouge.symphony.ui.helpers.FadeTransition
import io.github.zyrouge.symphony.ui.helpers.LocalAnimatedContentScope
import io.github.zyrouge.symphony.ui.helpers.LocalSharedTransitionScope
import io.github.zyrouge.symphony.ui.helpers.ScreenOrientation
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.AlbumViewRoute
import io.github.zyrouge.symphony.ui.view.NowPlayingData
import io.github.zyrouge.symphony.ui.view.NowPlayingStates

@Composable
fun NowPlayingBodyCover(
    context: ViewContext,
    data: NowPlayingData,
    states: NowPlayingStates,
    orientation: ScreenOrientation,
) {
    val showLyrics by states.showLyrics.collectAsState()

    Box(modifier = Modifier.padding(defaultHorizontalPadding, 0.dp)) {
        AnimatedContent(
            label = "now-playing-body-cover",
            targetState = showLyrics,
            contentAlignment = Alignment.Center,
            transitionSpec = {
                val from = FadeTransition.enterTransition()
                val to = FadeTransition.exitTransition()
                from togetherWith to
            },
        ) { targetStateShowLyrics ->
            if (targetStateShowLyrics) {
                NowPlayingBodyCoverLyrics(context, orientation)
            } else {
                NowPlayingBodyCoverArtwork(context, data.song, data.isPlaying)
            }
        }
    }
}

@Composable
private fun NowPlayingBodyCoverLyrics(context: ViewContext, orientation: ScreenOrientation) {
    val keepScreenAwake by context.symphony.settings.lyricsKeepScreenAwake.flow.collectAsState()

    if (keepScreenAwake) {
        KeepScreenAwake()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                0.dp,
                if (orientation == ScreenOrientation.LANDSCAPE) 0.dp else 8.dp
            )
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                RoundedCornerShape(24.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        LyricsText(
            context,
            padding = PaddingValues(
                horizontal = 12.dp,
                vertical = 8.dp,
            ),
            style = TimedContentTextStyle.defaultStyle(
                textStyle = LocalTextStyle.current,
                contentColor = LocalContentColor.current,
            ),
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NowPlayingBodyCoverArtwork(context: ViewContext, song: Song, isPlaying: Boolean) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedContentScope.current

    BoxWithConstraints {
        val dimension = min(this@BoxWithConstraints.maxHeight, this@BoxWithConstraints.maxWidth)

        val artworkScale by animateFloatAsState(
            targetValue = if (isPlaying) 1f else 0.85f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "ArtworkBreathingAnimation"
        )

        AnimatedContent(
            label = "now-playing-body-cover-artwork",
            modifier = Modifier.size(dimension),
            targetState = song,
            transitionSpec = {
                FadeTransition.enterTransition()
                    .togetherWith(FadeTransition.exitTransition())
            },
        ) { targetStateSong ->
            AsyncImage(
                model = targetStateSong.createArtworkImageRequest(context.symphony).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    state = rememberSharedContentState(key = "artwork-${targetStateSong.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                            }
                        } else Modifier
                    )
                    .graphicsLayer {
                        scaleX = artworkScale
                        scaleY = artworkScale
                    }
                    .shadow(
                        elevation = if (isPlaying) 24.dp else 8.dp,
                        shape = RoundedCornerShape(32.dp),
                        clip = false
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .swipeable(
                        minimumDragAmount = 100f,
                        onSwipeLeft = {
                            if (context.symphony.radio.canJumpToNext()) {
                                context.symphony.radio.jumpToNext()
                            }
                        },
                        onSwipeRight = {
                            if (context.symphony.radio.canJumpToPrevious()) {
                                context.symphony.radio.jumpToPrevious()
                            }
                        },
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { _ ->
                            context.symphony.groove.album
                                .getIdFromSong(song)
                                ?.let {
                                    context.navController.navigate(AlbumViewRoute(it))
                                }
                        }
                    }
            )
        }
    }
}