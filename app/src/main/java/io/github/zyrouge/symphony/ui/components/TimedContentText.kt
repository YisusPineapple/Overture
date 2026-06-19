package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                spacing = 16.dp, 
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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

    // Overture: Decoupled lyrics sync from UI recomposition
    // This loop runs in the background and only triggers a recomposition when the active line changes.
    LaunchedEffect(content) {
        while (isActive) {
            val currentPosition = context.symphony.radio.currentPlaybackPosition?.played ?: 0L
            if (content.isSynced) {
                val nActiveIndex = content.pairs.indexOfLast { it.first <= currentPosition }
                if (nActiveIndex != -1 && activeIndex != nActiveIndex) {
                    activeIndex = nActiveIndex
                    val isLineVisible = activeIndex in visibleRange.first..visibleRange.second
                    if (!isLineVisible || !scrollState.isScrollInProgress) {
                        val scrollIndex = calculateRelaxedScrollIndex(nActiveIndex, visibleRange)
                        scrollState.animateScrollToItem(scrollIndex)
                    }
                }
            }
            delay(100)
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
        itemsIndexed(content.pairs) { i, x ->
            val highlight = !content.isSynced || i < activeIndex
            val active = i == activeIndex

            val scale by animateFloatAsState(
                targetValue = if (active) 1.05f else 0.95f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "LyricsScale"
            )
            
            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else if (highlight) 0.6f else 0.3f,
                animationSpec = tween(300),
                label = "LyricsAlpha"
            )

            val textStyle by animateTextStyleAsState(
                targetValue = when {
                    active -> style.active
                    highlight -> style.highlighted
                    else -> style.inactive
                },
            )

            Text(
                text = x.second,
                modifier = Modifier
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
                    },
                style = textStyle,
            )
        }
        item {
            Spacer(modifier = Modifier.height(padding.calculateBottomPadding() + 64.dp))
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