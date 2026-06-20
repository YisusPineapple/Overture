package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import kotlin.math.floor
import kotlin.math.max

fun Modifier.drawScrollBar(state: LazyGridState, columns: Int): Modifier = composed {
    val scrollPointerColor = MaterialTheme.colorScheme.primary
    val isLastItemVisible by remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.lastOrNull()?.index == state.layoutInfo.totalItemsCount - 1
        }
    }
    val rows by remember {
        derivedStateOf {
            floor(state.layoutInfo.totalItemsCount.toFloat() / columns).toSafeFinite()
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
        label = "c-lazy-grid-scroll-pointer-color",
    )
    var scrollPointerOffsetY by remember { mutableFloatStateOf(0f) }
    val scrollPointerOffsetYAnimated by animateFloatAsState(
        scrollPointerOffsetY,
        animationSpec = tween(durationMillis = if (isDragging) 0 else 150),
        label = "c-lazy-grid-scroll-pointer-offset-y",
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
            val scrollBarHeight =
                size.height - ContentDrawScopeScrollBarDefaults.scrollPointerHeight.toPx()
            scrollPointerOffsetY = when {
                isLastItemVisible && !isDragging -> scrollBarHeight
                else -> (scrollBarHeight / rows) * (state.firstVisibleItemIndex / columns)
            }.toSafeFinite()
            
            drawScrollBar(
                scrollPointerColor = showScrollPointerColorAnimated,
                scrollPointerOffsetY = scrollPointerOffsetYAnimated,
            )
        }
}