package io.github.zyrouge.symphony.ui.view.nowPlaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.radio.RadioQueue
import io.github.zyrouge.symphony.ui.components.SongDropdownMenu
import io.github.zyrouge.symphony.ui.helpers.FadeTransition
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.ArtistViewRoute
import io.github.zyrouge.symphony.ui.view.NowPlayingControlsLayout
import io.github.zyrouge.symphony.ui.view.NowPlayingData
import io.github.zyrouge.symphony.utils.DurationUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowPlayingBodyContent(context: ViewContext, data: NowPlayingData) {
    val favoriteSongIds by context.symphony.groove.playlist.favorites.collectAsState()
    val isFavorite by remember(data) {
        derivedStateOf { favoriteSongIds.contains(data.song.id) }
    }
    
    val dynamicColor = data.dominantColor ?: MaterialTheme.colorScheme.primary

    data.run {
        Column {
            Row {
                AnimatedContent(
                    label = "now-playing-body-content",
                    modifier = Modifier.weight(1f),
                    targetState = song,
                    transitionSpec = {
                        FadeTransition.enterTransition()
                            .togetherWith(FadeTransition.exitTransition())
                    },
                ) { targetStateSong ->
                    Column(modifier = Modifier.padding(defaultHorizontalPadding, 0.dp)) {
                        Text(
                            targetStateSong.title,
                            style = MaterialTheme.typography.headlineSmall
                                .copy(fontWeight = FontWeight.Bold),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (targetStateSong.artists.isNotEmpty()) {
                            FlowRow {
                                targetStateSong.artists.forEachIndexed { i, it ->
                                    Text(
                                        it,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.pointerInput(Unit) {
                                            detectTapGestures { _ ->
                                                context.navController.navigate(ArtistViewRoute(it))
                                            }
                                        },
                                    )
                                    if (i != targetStateSong.artists.size - 1) {
                                        Text(", ")
                                    }
                                }
                            }
                        }
                        if (data.showSongAdditionalInfo) {
                            targetStateSong.toSamplingInfoString(context.symphony)?.let {
                                val localContentColor = LocalContentColor.current
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall
                                        .copy(color = localContentColor.copy(alpha = 0.7f)),
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
                Row {
                    IconButton(
                        modifier = Modifier.offset(4.dp),
                        onClick = {
                            context.symphony.groove.playlist.run {
                                when {
                                    isFavorite -> unfavorite(song.id)
                                    else -> favorite(song.id)
                                }
                            }
                        }
                    ) {
                        when {
                            isFavorite -> Icon(
                                Icons.Filled.Favorite,
                                null,
                                tint = dynamicColor,
                            )

                            else -> Icon(Icons.Filled.FavoriteBorder, null)
                        }
                    }

                    var showOptionsMenu by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            showOptionsMenu = !showOptionsMenu
                        }
                    ) {
                        Icon(Icons.Filled.MoreVert, null)
                        SongDropdownMenu(
                            context,
                            song,
                            isFavorite = isFavorite,
                            expanded = showOptionsMenu,
                            onDismissRequest = {
                                showOptionsMenu = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(defaultHorizontalPadding + 8.dp))
            
            // Overture: Redesigned Controls Layouts
            when (controlsLayout) {
                NowPlayingControlsLayout.CompactLeft -> NowPlayingCompactControls(
                    context,
                    data = data,
                    dynamicColor = dynamicColor,
                )

                NowPlayingControlsLayout.CompactRight -> NowPlayingCompactControls(
                    context,
                    data = data,
                    dynamicColor = dynamicColor,
                    modifier = Modifier.align(Alignment.End)
                )

                NowPlayingControlsLayout.Traditional -> NowPlayingTraditionalControls(
                    context,
                    data = data,
                    dynamicColor = dynamicColor,
                )
            }
            Spacer(modifier = Modifier.height(defaultHorizontalPadding + 8.dp))
            NowPlayingSeekBar(context, dynamicColor)
            Spacer(modifier = Modifier.height(defaultHorizontalPadding))
        }
    }
}

@Composable
fun NowPlayingCompactControls(
    context: ViewContext,
    data: NowPlayingData,
    dynamicColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(defaultHorizontalPadding, 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { context.symphony.radio.queue.toggleShuffleMode() }) {
            Icon(
                Icons.Filled.Shuffle, 
                null, 
                tint = if (data.currentShuffleMode) dynamicColor else LocalContentColor.current.copy(alpha = 0.5f)
            )
        }
        
        NowPlayingPlayPauseButton(
            context,
            data = data,
            style = NowPlayingControlButtonStyle(
                color = NowPlayingControlButtonColor.Primary,
                customColor = dynamicColor,
            ),
        )
        NowPlayingSkipPreviousButton(
            context,
            data = data,
            style = NowPlayingControlButtonStyle(
                color = NowPlayingControlButtonColor.Surface,
            ),
        )
        if (data.enableSeekControls) {
            NowPlayingFastRewindButton(
                context,
                data = data,
                style = NowPlayingControlButtonStyle(
                    color = NowPlayingControlButtonColor.Surface,
                ),
            )
            NowPlayingFastForwardButton(
                context,
                data = data,
                style = NowPlayingControlButtonStyle(
                    color = NowPlayingControlButtonColor.Surface,
                ),
            )
        }
        NowPlayingSkipNextButton(
            context,
            data = data,
            style = NowPlayingControlButtonStyle(
                color = NowPlayingControlButtonColor.Surface,
            ),
        )
        
        IconButton(onClick = { context.symphony.radio.queue.toggleLoopMode() }) {
            Icon(
                if (data.currentLoopMode == RadioQueue.LoopMode.Song) Icons.Filled.RepeatOne else Icons.Filled.Repeat, 
                null, 
                tint = if (data.currentLoopMode != RadioQueue.LoopMode.None) dynamicColor else LocalContentColor.current.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun NowPlayingTraditionalControls(context: ViewContext, data: NowPlayingData, dynamicColor: Color) {
    Row(
        modifier = Modifier
            .padding(horizontal = defaultHorizontalPadding)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { context.symphony.radio.queue.toggleShuffleMode() }) {
            Icon(
                Icons.Filled.Shuffle, 
                null, 
                modifier = Modifier.size(28.dp),
                tint = if (data.currentShuffleMode) dynamicColor else LocalContentColor.current.copy(alpha = 0.5f)
            )
        }
        
        NowPlayingSkipPreviousButton(
            context,
            data = data,
            style = NowPlayingControlButtonStyle(
                color = NowPlayingControlButtonColor.Transparent,
                size = NowPlayingControlButtonSize.Large
            ),
        )
        
        if (data.enableSeekControls) {
            NowPlayingFastRewindButton(
                context,
                data = data,
                style = NowPlayingControlButtonStyle(
                    color = NowPlayingControlButtonColor.Transparent,
                ),
            )
        }
        
        NowPlayingPlayPauseButton(
            context,
            data = data,
            style = NowPlayingControlButtonStyle(
                color = NowPlayingControlButtonColor.Primary,
                customColor = dynamicColor,
                size = NowPlayingControlButtonSize.Giant, // Overture: Massive play button for traditional layout
            ),
        )
        
        if (data.enableSeekControls) {
            NowPlayingFastForwardButton(
                context,
                data = data,
                style = NowPlayingControlButtonStyle(
                    color = NowPlayingControlButtonColor.Transparent,
                ),
            )
        }
        
        NowPlayingSkipNextButton(
            context,
            data = data,
            style = NowPlayingControlButtonStyle(
                color = NowPlayingControlButtonColor.Transparent,
                size = NowPlayingControlButtonSize.Large
            ),
        )
        
        IconButton(onClick = { context.symphony.radio.queue.toggleLoopMode() }) {
            Icon(
                if (data.currentLoopMode == RadioQueue.LoopMode.Song) Icons.Filled.RepeatOne else Icons.Filled.Repeat, 
                null, 
                modifier = Modifier.size(28.dp),
                tint = if (data.currentLoopMode != RadioQueue.LoopMode.None) dynamicColor else LocalContentColor.current.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun NowPlayingSeekBar(context: ViewContext, dynamicColor: Color) {
    val playbackPosition by context.symphony.radio.observatory.playbackPosition.collectAsState()

    Row(
        modifier = Modifier.padding(defaultHorizontalPadding, 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var seekRatio by remember { mutableStateOf<Float?>(null) }

        NowPlayingPlaybackPositionText(
            seekRatio?.let { it * playbackPosition.total }?.toLong()
                ?: playbackPosition.played,
            Alignment.CenterStart,
        )
        Box(modifier = Modifier.weight(1f)) {
            NowPlayingSeekBar(
                ratio = playbackPosition.ratio,
                dynamicColor = dynamicColor,
                onSeekStart = {
                    seekRatio = 0f
                },
                onSeek = {
                    seekRatio = it
                },
                onSeekEnd = {
                    context.symphony.radio.seek((it * playbackPosition.total).toLong())
                    seekRatio = null
                },
                onSeekCancel = {
                    seekRatio = null
                },
            )
        }
        NowPlayingPlaybackPositionText(
            playbackPosition.total,
            Alignment.CenterEnd,
        )
    }
}

@Composable
private fun NowPlayingSeekBar(
    ratio: Float,
    dynamicColor: Color,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit,
    onSeekCancel: () -> Unit,
) {
    val sliderHeight = 48.dp // Overture: Increased for better touch target
    
    var dragging by remember { mutableStateOf(false) }
    var dragRatio by remember { mutableFloatStateOf(0f) }
    
    val currentRatio = if (dragging) dragRatio else ratio
    
    // Overture: M3E Expressive Slider logic
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 24.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "TrackHeightAnimation"
    )
    
    val activeColor = dynamicColor
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(sliderHeight),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val tapRatio = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeekEnd(tapRatio)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragRatio = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeekStart()
                        },
                        onDragEnd = {
                            onSeekEnd(dragRatio)
                            dragging = false
                        },
                        onDragCancel = {
                            onSeekCancel()
                            dragging = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragRatio = (change.position.x / size.width).coerceIn(0f, 1f)
                            onSeek(dragRatio)
                        }
                    )
                }
        ) {
            val trackY = size.height / 2f
            val trackH = trackHeight.toPx()
            val cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)

            // 1. Draw Inactive Track
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, trackY - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = cornerRadius
            )

            // 2. Draw Active Track
            val activeWidth = size.width * currentRatio
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(0f, trackY - trackH / 2f),
                size = Size(activeWidth, trackH),
                cornerRadius = cornerRadius
            )
        }
    }
}

@Composable
private fun NowPlayingPlaybackPositionText(
    duration: Long,
    alignment: Alignment,
) {
    val textStyle = MaterialTheme.typography.labelMedium
    val durationFormatted = DurationUtils.formatMs(duration)

    Box(contentAlignment = alignment) {
        Text(
            "0".repeat(durationFormatted.length),
            style = textStyle.copy(color = Color.Transparent),
        )
        Text(
            durationFormatted,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun NowPlayingPlayPauseButton(
    context: ViewContext,
    data: NowPlayingData,
    style: NowPlayingControlButtonStyle,
) {
    data.run {
        NowPlayingControlButton(
            style = style,
            icon = when {
                !isPlaying -> Icons.Filled.PlayArrow
                else -> Icons.Filled.Pause
            },
            onClick = {
                context.symphony.radio.shorty.playPause()
            }
        )
    }
}

@Composable
private fun NowPlayingSkipPreviousButton(
    context: ViewContext,
    data: NowPlayingData,
    style: NowPlayingControlButtonStyle,
) {
    data.run {
        NowPlayingControlButton(
            style = style,
            icon = Icons.Filled.SkipPrevious,
            onClick = {
                context.symphony.radio.shorty.previous()
            }
        )
    }
}

@Composable
private fun NowPlayingSkipNextButton(
    context: ViewContext,
    data: NowPlayingData,
    style: NowPlayingControlButtonStyle,
) {
    data.run {
        NowPlayingControlButton(
            style = style,
            icon = Icons.Filled.SkipNext,
            onClick = {
                context.symphony.radio.shorty.skip()
            }
        )
    }
}

@Composable
private fun NowPlayingFastRewindButton(
    context: ViewContext,
    data: NowPlayingData,
    style: NowPlayingControlButtonStyle,
) {
    data.run {
        NowPlayingControlButton(
            style = style,
            icon = Icons.Filled.FastRewind,
            onClick = {
                context.symphony.radio.shorty
                    .seekFromCurrent(-seekBackDuration)
            }
        )
    }
}

@Composable
private fun NowPlayingFastForwardButton(
    context: ViewContext,
    data: NowPlayingData,
    style: NowPlayingControlButtonStyle,
) {
    data.run {
        NowPlayingControlButton(
            style = style,
            icon = Icons.Filled.FastForward,
            onClick = {
                context.symphony.radio.shorty
                    .seekFromCurrent(seekForwardDuration)
            }
        )
    }
}

private enum class NowPlayingControlButtonColor {
    Primary,
    Surface,
    Transparent,
}

private enum class NowPlayingControlButtonSize {
    Default,
    Large,
    Giant, // Overture: Added Giant size for Traditional layout
}

private data class NowPlayingControlButtonStyle(
    val color: NowPlayingControlButtonColor,
    val customColor: Color? = null,
    val size: NowPlayingControlButtonSize = NowPlayingControlButtonSize.Default,
)

@Composable
private fun NowPlayingControlButton(
    style: NowPlayingControlButtonStyle,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val backgroundColor = when (style.color) {
        NowPlayingControlButtonColor.Primary -> style.customColor ?: MaterialTheme.colorScheme.primary
        NowPlayingControlButtonColor.Surface -> MaterialTheme.colorScheme.surfaceVariant
        NowPlayingControlButtonColor.Transparent -> Color.Transparent
    }
    val contentColor = when (style.color) {
        NowPlayingControlButtonColor.Primary -> MaterialTheme.colorScheme.onPrimary
        else -> LocalContentColor.current
    }
    
    val (buttonSize, iconSize) = when (style.size) {
        NowPlayingControlButtonSize.Default -> 48.dp to 24.dp
        NowPlayingControlButtonSize.Large -> 56.dp to 32.dp
        NowPlayingControlButtonSize.Giant -> 80.dp to 48.dp
    }

    IconButton(
        modifier = Modifier
            .size(buttonSize)
            .background(backgroundColor, CircleShape),
        onClick = onClick,
    ) {
        Icon(
            icon,
            null,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}