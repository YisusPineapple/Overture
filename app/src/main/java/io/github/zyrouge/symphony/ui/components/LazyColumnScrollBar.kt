package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.utils.toSafeFinite
import kotlinx.coroutines.launch
import kotlin.math.max

fun Modifier.drawScrollBar(state: LazyListState): Modifier = composed {
    val scrollPointerColor = MaterialTheme.colorScheme.primary
    val isLastItemVisible by remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.lastOrNull()?.index == state.layoutInfo.totalItemsCount - 1
        }
    }
    
    var isDragging by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    val showScrollPointer by remember {
        derivedStateOf {
            isDragging || !(state.firstVisibleItemIndex == 0 && isLastItemVisible)
        }
    }
    
    val showScrollPointerColorAnimated by animateColorAsState(
        scrollPointerColor.copy(alpha = if (showScrollPointer) 1f else 0f),
        animationSpec = tween(durationMillis = 500),
        label = "c-lazy-column-scroll-pointer-color",
    )
    var scrollPointerOffsetY by remember { mutableFloatStateOf(0f) }
    val scrollPointerOffsetYAnimated by animateFloatAsState(
        scrollPointerOffsetY,
        animationSpec = tween(durationMillis = if (isDragging) 0 else 50, easing = EaseInOut),
        label = "c-lazy-column-scroll-pointer-offset-y",
    )

    val density = LocalDensity.current
    val touchTargetWidth = with(density) { 32.dp.toPx() }

    this
        .pointerInput(state.layoutInfo.totalItemsCount) {
            detectVerticalDragGestures(
                onDragStart = { offset ->
                    if (offset.x >= size.width - touchTargetWidth) {
                        isDragging = true
                    }
                },
                onDragEnd = { isDragging = false },
                onDragCancel = { isDragging = false },
                onVerticalDrag = { change, _ ->
                    if (isDragging) {
                        change.consume()
                        val ratio = (change.position.y / size.height).coerceIn(0f, 1f)
                        val targetIndex = (ratio * state.layoutInfo.totalItemsCount).toInt()
                            .coerceIn(0, max(0, state.layoutInfo.totalItemsCount - 1))
                        coroutineScope.launch {
                            state.scrollToItem(targetIndex)
                        }
                    }
                }
            )
        }
        .drawWithContent {
            drawContent()
            scrollPointerOffsetY = when {
                isLastItemVisible && !isDragging -> size.height - ContentDrawScopeScrollBarDefaults.scrollPointerHeight.toPx()
                else -> (size.height / state.layoutInfo.totalItemsCount) * state.firstVisibleItemIndex
            }.toSafeFinite()
            
            drawScrollBar(
                scrollPointerColor = showScrollPointerColorAnimated,
                scrollPointerOffsetY = scrollPointerOffsetYAnimated,
            )
        }
}