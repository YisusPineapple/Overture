package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.FadeTransition
import io.github.zyrouge.symphony.ui.helpers.TransitionDurations
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.NowPlayingViewRoute
import io.github.zyrouge.symphony.utils.runIfOrThis
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun AnimatedNowPlayingBottomBar(context: ViewContext, insetPadding: Boolean = true) {
    val visible = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }

    AnimatedVisibility(
        visibleState = visible,
        enter = slideInVertically(
            animationSpec = nowPlayingBottomBarEnterAnimationSpec(),
            initialOffsetY = { it / 2 },
        ) + fadeIn(animationSpec = nowPlayingBottomBarEnterAnimationSpec()),
        exit = fadeOut(),
    ) {
        NowPlayingBottomBar(context, insetPadding)
    }
}

private fun <T> nowPlayingBottomBarEnterAnimationSpec() = TransitionDurations.Normal.asTween<T>(
    delayMillis = TransitionDurations.Fast.milliseconds,
)

@Composable
fun NowPlayingBottomBar(context: ViewContext, insetPadding: Boolean = true) {
    val queue by context.symphony.radio.observatory.queue.collectAsState()
    val queueIndex by context.symphony.radio.observatory.queueIndex.collectAsState()
    val currentPlayingSong by remember(queue, queueIndex) {
        derivedStateOf {
            queue.getOrNull(queueIndex)?.let { context.symphony.groove.song.get(it) }
        }
    }
    val isPlaying by context.symphony.radio.observatory.isPlaying.collectAsState()
    
    val playbackPositionState = context.symphony.radio.observatory.playbackPosition.collectAsState()
    
    val showTrackControls by context.symphony.settings.miniPlayerTrackControls.flow.collectAsState()
    val showSeekControls by context.symphony.settings.miniPlayerSeekControls.flow.collectAsState()
    val seekBackDuration by context.symphony.settings.seekBackDuration.flow.collectAsState()
    val seekForwardDuration by context.symphony.settings.seekForwardDuration.flow.collectAsState()

    val dominantColorInt by context.symphony.radio.observatory.dominantColor.collectAsState()
    val surfaceTint = MaterialTheme.colorScheme.surfaceTint
    val dynamicColor = remember(dominantColorInt) { dominantColorInt?.let { Color(it) } ?: surfaceTint }

    AnimatedContent(
        modifier = Modifier.fillMaxWidth(),
        label = "c-now-playing-container",
        targetState = currentPlayingSong,
        contentKey = { it != null },
        transitionSpec = {
            val from = slideInVertically() + fadeIn()
            val to = slideOutVertically() + fadeOut()
            from togetherWith to
        }
    ) { currentPlayingSongTarget ->
        currentPlayingSongTarget?.let { currentSong ->
            Column {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .background(dynamicColor.copy(alpha = 0.25f), RoundedCornerShape(1.dp))
                        .height(2.dp)
                        .fillMaxWidth()
                        .drawWithContent {
                            drawContent()
                            drawRoundRect(
                                color = dynamicColor,
                                size = Size(size.width * playbackPositionState.value.ratio, size.height),
                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                            )
                        }
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clickable {
                            context.navController.navigate(NowPlayingViewRoute)
                        }
                        .swipeable(
                            onSwipeUp = {
                                context.navController.navigate(NowPlayingViewRoute)
                            },
                            onSwipeDown = {
                                context.symphony.radio.stop()
                            },
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(0.dp, 8.dp),
                    ) {
                        Spacer(modifier = Modifier.width(12.dp))
                        AnimatedContent(
                            label = "c-now-playing-card-image",
                            targetState = currentSong,
                            contentKey = { it.id },
                            transitionSpec = {
                                val from = fadeIn(
                                    animationSpec = TransitionDurations.Normal.asTween(
                                        delayMillis = TransitionDurations.Normal.milliseconds,
                                    ),
                                )
                                val to = fadeOut(TransitionDurations.Normal.asTween())
                                from togetherWith to
                            },
                        ) { song ->
                            AsyncImage(
                                song.createArtworkImageRequest(context.symphony).build(),
                                null,
                                modifier = Modifier
                                    .size(45.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Spacer(modifier = Modifier.width(15.dp))
                        AnimatedContent(
                            label = "c-now-playing-card-content",
                            modifier = Modifier.weight(1f),
                            targetState = currentSong,
                            contentKey = { it.id },
                            transitionSpec = {
                                val from = fadeIn(
                                    animationSpec = TransitionDurations.Normal.asTween(
                                        delayMillis = TransitionDurations.Normal.milliseconds,
                                    ),
                                )
                                val to = fadeOut(TransitionDurations.Normal.asTween())
                                from togetherWith to
                            },
                        ) { song ->
                            NowPlayingBottomBarContent(context, song = song)
                        }
                        Spacer(modifier = Modifier.width(15.dp))
                        if (showTrackControls) {
                            IconButton(
                                onClick = { context.symphony.radio.shorty.previous() }
                            ) {
                                Icon(Icons.Filled.SkipPrevious, null)
                            }
                        }
                        if (showSeekControls) {
                            IconButton(
                                onClick = {
                                    context.symphony.radio.shorty.seekFromCurrent(-seekBackDuration)
                                }
                            ) {
                                Icon(Icons.Filled.FastRewind, null)
                            }
                        }
                        IconButton(
                            onClick = { context.symphony.radio.shorty.playPause() }
                        ) {
                            Icon(
                                when {
                                    !isPlaying -> Icons.Filled.PlayArrow
                                    else -> Icons.Filled.Pause
                                },
                                null,
                                tint = dynamicColor
                            )
                        }
                        if (showSeekControls) {
                            IconButton(
                                onClick = {
                                    context.symphony.radio.shorty.seekFromCurrent(
                                        seekForwardDuration
                                    )
                                }
                            ) {
                                Icon(Icons.Filled.FastForward, null)
                            }
                        }
                        if (showTrackControls) {
                            IconButton(
                                onClick = { context.symphony.radio.shorty.skip() }
                            ) {
                                Icon(Icons.Filled.SkipNext, null)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
                if (insetPadding) {
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        } ?: Box {}
    }
}

@Composable
private fun NowPlayingBottomBarContent(context: ViewContext, song: Song) {
    val coroutineScope = rememberCoroutineScope()
    
    BoxWithConstraints(modifier = Modifier.clipToBounds()) {
        val cardWidthPx = this@BoxWithConstraints.constraints.maxWidth
        
        val offsetX = remember { Animatable(0f) }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = offsetX.value
                    alpha = if (offsetX.value != 0f) 0.7f else 1f
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val thresh = cardWidthPx / 4
                            val affected = when {
                                -offsetX.value > thresh -> context.symphony.radio.shorty.skip()
                                offsetX.value > thresh -> context.symphony.radio.shorty.previous()
                                else -> false
                            }
                            coroutineScope.launch {
                                if (!affected) {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                } else {
                                    offsetX.snapTo(0f)
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                offsetX.snapTo(offsetX.value + (dragAmount * 0.6f))
                            }
                        },
                    )
                },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                NowPlayingBottomBarContentText(
                    context,
                    song.title,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (song.artists.isNotEmpty()) {
                    NowPlayingBottomBarContentText(
                        context,
                        song.artists.joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBottomBarContentText(
    context: ViewContext,
    text: String,
    style: TextStyle,
) {
    val textMarquee by context.symphony.settings.miniPlayerTextMarquee.flow.collectAsState()
    var showOverlay by remember { mutableStateOf(false) }

    Box {
        Text(
            text,
            style = style,
            maxLines = 1,
            overflow = when {
                textMarquee -> TextOverflow.Clip
                else -> TextOverflow.Ellipsis
            },
            modifier = Modifier
                .runIfOrThis<Modifier>(textMarquee) {
                    basicMarquee(iterations = Int.MAX_VALUE)
                }
                .onGloballyPositioned {
                    val offsetX = it.boundsInParent().centerLeft.x
                    showOverlay = offsetX.absoluteValue != 0f
                },
        )
        AnimatedVisibility(
            visible = showOverlay,
            modifier = Modifier.matchParentSize(),
            enter = FadeTransition.enterTransition(),
            exit = FadeTransition.exitTransition(),
        ) {
            val backgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)

            Row {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(backgroundColor, Color.Transparent)
                            )
                        )
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, backgroundColor)
                            )
                        )
                )
            }
        }
    }
}