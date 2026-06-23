package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseInOutSine
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.LyricsAlignment
import io.github.zyrouge.symphony.services.LyricsAnimationEngine
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
                spacing = 24.dp, 
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
    
    // Overture: Lyrics Settings
    val alignment by context.symphony.settings.lyricsAlignment.flow.collectAsState()
    val engine by context.symphony.settings.lyricsAnimationEngine.flow.collectAsState()
    val unfocusedOpacity by context.symphony.settings.lyricsUnfocusedOpacity.flow.collectAsState()
    val syncOffset by context.symphony.settings.lyricsSyncOffset.flow.collectAsState()

    val currentPosition = playbackPosition.played + syncOffset

    val horizontalAlignment = when (alignment) {
        LyricsAlignment.Left -> Alignment.Start
        LyricsAlignment.Center -> Alignment.CenterHorizontally
        LyricsAlignment.Right -> Alignment.End
    }
    val textAlign = when (alignment) {
        LyricsAlignment.Left -> TextAlign.Start
        LyricsAlignment.Center -> TextAlign.Center
        LyricsAlignment.Right -> TextAlign.End
    }
    
    // Overture: Explicitly typed AnimationSpec<Float> to fix Kotlin type inference errors
    val scaleSpec: AnimationSpec<Float> = if (engine == LyricsAnimationEngine.Expressive) {
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    } else {
        tween(300)
    }
    val alphaSpec: AnimationSpec<Float> = if (engine == LyricsAnimationEngine.Expressive) {
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    } else {
        tween(300)
    }

    LaunchedEffect(content) {
        while (isActive) {
            val pos = (context.symphony.radio.currentPlaybackPosition?.played ?: 0L) + syncOffset
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
            delay(50) 
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
        horizontalAlignment = horizontalAlignment
    ) {
        item {
            Spacer(modifier = Modifier.height(padding.calculateTopPadding() + 64.dp))
        }
        itemsIndexed(content.lines) { i, line ->
            val highlight = !content.isSynced || i < activeIndex
            val active = i == activeIndex

            val scale by animateFloatAsState(
                targetValue = if (active) 1.1f else 0.95f, 
                animationSpec = scaleSpec,
                label = "LyricsScale",
            )

            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else unfocusedOpacity, 
                animationSpec = alphaSpec,
                label = "LyricsAlpha",
            )

            val textStyle by animateTextStyleAsState(
                targetValue = when {
                    active -> style.active.copy(textAlign = textAlign)
                    highlight -> style.highlighted.copy(textAlign = textAlign)
                    else -> style.inactive.copy(textAlign = textAlign)
                },
            )

            val modifier = Modifier
                .animateItem()
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        when (alignment) {
                            LyricsAlignment.Left -> 0f
                            LyricsAlignment.Center -> 0.5f
                            LyricsAlignment.Right -> 1f
                        }, 
                        0.5f
                    )
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

            if (active && line.words.isNotEmpty()) {
                FlowRow(
                    modifier = modifier,
                    horizontalArrangement = when (alignment) {
                        LyricsAlignment.Left -> Arrangement.Start
                        LyricsAlignment.Center -> Arrangement.Center
                        LyricsAlignment.Right -> Arrangement.End
                    }
                ) {
                    line.words.forEachIndexed { wordIndex, word ->
                        val isWordActive = currentPosition >= word.time && 
                            (wordIndex == line.words.lastIndex || currentPosition < line.words[wordIndex + 1].time)
                        
                        val isWordPassed = currentPosition >= word.time

                        val wordScale by animateFloatAsState(
                            targetValue = if (isWordActive) 1.15f else 1f,
                            animationSpec = scaleSpec,
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

            if (active && i < content.lines.lastIndex) {
                val nextLine = content.lines[i + 1]
                if (nextLine.time - currentPosition > 5000 && currentPosition > line.time + 1000) {
                    InstrumentalIndicator(color = style.active.color, alignment = horizontalAlignment)
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(padding.calculateBottomPadding() + 64.dp))
        }
    }
}

@Composable
fun InstrumentalIndicator(color: Color, alignment: Alignment.Horizontal) {
    val infiniteTransition = rememberInfiniteTransition(label = "InstrumentalDots")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment)
    ) {
        for (i in 0..2) {
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, delayMillis = i * 200, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "DotScale"
            )
            
            Box(
                modifier = Modifier
                    .size(10.dp)
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