package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.ui.helpers.TransitionDurations
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.utils.TimedContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

data class TimedContentTextStyle(
    val highlighted: TextStyle,
    val active: TextStyle,
    val inactive: TextStyle,
    val spacing: Dp,
) {
    companion object {
        @Composable
        fun defaultStyle(
            textStyle: TextStyle,
            contentColor: Color,
        ) = textStyle.copy(color = contentColor).let {
            TimedContentTextStyle(
                highlighted = it.copy(fontWeight = FontWeight.SemiBold),
                active = it.copy(fontWeight = FontWeight.Bold),
                inactive = it.copy(fontWeight = FontWeight.Normal),
                spacing = 24.dp, // Overture: Increased spacing for cleaner look
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TimedContentText(
    context: ViewContext,
    content: TimedContent,
    padding: PaddingValues,
    style: TimedContentTextStyle,
    onSeek: (Int) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()
    val visibleRange by remember {
        derivedStateOf {
            val start = scrollState.firstVisibleItemIndex
            val end = start + scrollState.layoutInfo.visibleItemsInfo.size - 1
            start to end
        }
    }
    var activeIndex by remember { mutableIntStateOf(-1) }

    val playbackPosition by context.symphony.radio.observatory.playbackPosition.collectAsState()
    val currentPosition = playbackPosition.played

    LaunchedEffect(content) {
        while (isActive) {
            val pos = context.symphony.radio.currentPlaybackPosition?.played ?: 0L
            if (content.isSynced) {
                val nActiveIndex = content.lines.indexOfLast { it.time <= pos }
                if (nActiveIndex != -1 && activeIndex != nActiveIndex) {
                    activeIndex = nActiveIndex
                    val isLineVisible = activeIndex in visibleRange.first..visibleRange.second
                    if (!isLineVisible || !scrollState.isScrollInProgress) {
                        val scrollIndex = calculateRelaxedScrollIndex(nActiveIndex, visibleRange)
                        scrollState.animateScrollToItem(scrollIndex)
                    }
                }
            }
            delay(50) // Overture: Faster polling for precise word sync
        }
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .padding(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
            )
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(style.spacing),
    ) {
        item {
            Spacer(modifier = Modifier.height(padding.calculateTopPadding() + 64.dp))
        }
        itemsIndexed(content.lines) { i, line ->
            val highlight = !content.isSynced || i < activeIndex
            val active = i == activeIndex

            val scale by animateFloatAsState(
                targetValue = if (active) 1.1f else 0.95f, // Overture: Larger active line
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "LyricsScale",
            )

            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.3f, // Overture: Dimmer inactive lines
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "LyricsAlpha",
            )

            val textStyle by animateTextStyleAsState(
                targetValue = when {
                    active -> style.active
                    highlight -> style.highlighted
                    else -> style.inactive
                },
            )

            val modifier = Modifier
                .animateItem()
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }
                .pointerInput(Unit) {
                    detectTapGestures { _ ->
                        if (!content.isSynced) {
                            return@detectTapGestures
                        }
                        onSeek(i)
                        activeIndex = i
                        coroutineScope.launch {
                            val scrollIndex = calculateRelaxedScrollIndex(i, visibleRange)
                            scrollState.animateScrollToItem(scrollIndex)
                        }
                    }
                }

            // Overture: Premium Karaoke Word-by-Word Rendering
            if (active && line.words.isNotEmpty()) {
                FlowRow(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.Start
                ) {
                    line.words.forEachIndexed { wordIndex, word ->
                        // A word is "active" if the current time is past its start time, 
                        // AND before the next word's start time (or it's the last word).
                        val isWordActive = currentPosition >= word.time && 
                            (wordIndex == line.words.lastIndex || currentPosition < line.words[wordIndex + 1].time)
                        
                        val isWordPassed = currentPosition >= word.time

                        val wordScale by animateFloatAsState(
                            targetValue = if (isWordActive) 1.15f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioHighBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "WordScale"
                        )

                        val wordColor = if (isWordPassed) style.active.color else style.inactive.color

                        Text(
                            text = word.text + if (wordIndex < line.words.size - 1 && !word.text.endsWith(" ")) " " else "",
                            color = wordColor,
                            style = textStyle,
                            modifier = Modifier.graphicsLayer {
                                scaleX = wordScale
                                scaleY = wordScale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                            }
                        )
                    }
                }
            } else {
                Text(
                    text = line.text,
                    modifier = modifier,
                    style = textStyle,
                )
            }

            // Overture: Instrumental Pause Indicator
            if (active && i < content.lines.lastIndex) {
                val nextLine = content.lines[i + 1]
                // If the gap between the end of this line and the start of the next is > 8 seconds
                if (nextLine.time - currentPosition > 8000 && currentPosition > line.time + 2000) {
                    InstrumentalIndicator(color = style.active.color)
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(padding.calculateBottomPadding() + 64.dp))
        }
    }
}

@Composable
fun InstrumentalIndicator(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "InstrumentalDots")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
    ) {
        for (i in 0..2) {
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "DotScale"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = scale
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

private fun calculateRelaxedScrollIndex(target: Int, range: Pair<Int, Int>): Int {
    val relaxLines = (range.second - range.first).floorDiv(3)
    return max(0, target - relaxLines)
}

@Composable
private fun animateTextStyleAsState(targetValue: TextStyle): State<TextStyle> {
    val animation = remember { Animatable(0f) }
    var previousTextStyle by remember { mutableStateOf(targetValue) }
    var nextTextStyle by remember { mutableStateOf(targetValue) }

    val textStyleState = remember(animation.value) {
        derivedStateOf {
            lerp(previousTextStyle, nextTextStyle, animation.value)
        }
    }

    LaunchedEffect(targetValue) {
        previousTextStyle = textStyleState.value
        nextTextStyle = targetValue
        animation.snapTo(0f)
        animation.animateTo(1f, TransitionDurations.Fast.asTween())
    }

    return textStyleState
}