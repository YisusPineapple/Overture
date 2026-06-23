package io.github.zyrouge.symphony.ui.view

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.components.AdaptiveSnackbar
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholderSize
import io.github.zyrouge.symphony.ui.components.NewPlaylistDialog
import io.github.zyrouge.symphony.ui.components.SongCard
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.theme.ThemeColors
import io.github.zyrouge.symphony.ui.view.nowPlaying.NothingPlayingBody
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object QueueViewRoute

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueView(context: ViewContext) {
    val coroutineScope = rememberCoroutineScope()
    val queue by context.symphony.radio.observatory.queue.collectAsState()
    val queueIndex by context.symphony.radio.observatory.queueIndex.collectAsState()
    val selectedSongIndices = remember { mutableStateListOf<Int>() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = queueIndex.coerceAtLeast(0),
    )
    var showSaveDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(snackbarHostState) {
                AdaptiveSnackbar(it)
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle(
                        modifier = Modifier.padding(start = IconButtonPlaceholderSize)
                    ) {
                        Text(context.symphony.t.Queue)
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            context.navController.popBackStack()
                        }
                    ) {
                        Icon(
                            Icons.Filled.ExpandMore,
                            null,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                actions = {
                    if (selectedSongIndices.isNotEmpty()) {
                        val allSelected = selectedSongIndices.size == queue.size
                        IconButton(
                            onClick = {
                                if (allSelected) {
                                    selectedSongIndices.clear()
                                } else {
                                    selectedSongIndices.clear()
                                    selectedSongIndices.addAll(queue.indices)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.SelectAll, null)
                        }
                        IconButton(
                            onClick = {
                                context.symphony.radio.queue.remove(selectedSongIndices.toList())
                                selectedSongIndices.clear()
                            }
                        ) {
                            Icon(Icons.Filled.Delete, null)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                showSaveDialog = !showSaveDialog
                            }
                        ) {
                            Icon(Icons.Default.Save, null)
                        }
                        IconButton(
                            onClick = {
                                val indicesToRemove = queue.indices.filter { it != queueIndex }
                                if (indicesToRemove.isNotEmpty()) {
                                    context.symphony.radio.queue.remove(indicesToRemove)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.ClearAll, null)
                        }
                    }
                }
            )
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(top = contentPadding.calculateTopPadding())
                    .fillMaxSize()
            ) {
                if (queue.isEmpty()) {
                    NothingPlayingBody(context)
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 180.dp), // Overture: Increased padding to avoid mini-player overlap
                        modifier = Modifier.pointerInput(queue) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        offset.y.toInt() in it.offset..(it.offset + it.size)
                                    }
                                    if (item != null) {
                                        draggingIndex = item.index
                                        targetIndex = item.index
                                        dragOffset = 0f
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                    val cIndex = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                    val cItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == cIndex } ?: return@detectDragGesturesAfterLongPress
                                    
                                    val currentY = cItem.offset + dragOffset + cItem.size / 2
                                    val hoveredItem = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        currentY.toInt() in it.offset..(it.offset + it.size)
                                    }
                                    if (hoveredItem != null) {
                                        targetIndex = hoveredItem.index
                                    }
                                },
                                onDragEnd = {
                                    if (draggingIndex != null && targetIndex != null && draggingIndex != targetIndex) {
                                        context.symphony.radio.queue.move(draggingIndex!!, targetIndex!!)
                                    }
                                    draggingIndex = null
                                    targetIndex = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    targetIndex = null
                                    dragOffset = 0f
                                }
                            )
                        }
                    ) {
                        itemsIndexed(
                            queue,
                            key = { i, id -> "$i-$id" },
                            contentType = { _, _ -> Groove.Kind.SONG },
                        ) { i, songId ->
                            context.symphony.groove.song.get(songId)?.let { song ->
                                
                                val isDragging = i == draggingIndex
                                val isTarget = i == targetIndex && i != draggingIndex
                                val zIndex = if (isDragging) 1f else 0f
                                val translationY = if (isDragging) dragOffset else 0f

                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 16.dp else 0.dp,
                                    label = "dragElevation"
                                )
                                val cardBgColor by animateColorAsState(
                                    targetValue = if (isDragging) MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp) else Color.Transparent,
                                    label = "dragBgColor"
                                )

                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { dismissValue ->
                                        if (dismissValue != SwipeToDismissBoxValue.Settled) {
                                            val removedSongId = queue[i]
                                            context.symphony.radio.queue.remove(i)
                                            
                                            // Overture: Undo Snackbar
                                            coroutineScope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = context.symphony.t.Delete, // Or a specific "Song removed" string
                                                    actionLabel = context.symphony.t.Cancel, // Using Cancel as Undo
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    context.symphony.radio.queue.add(removedSongId, i)
                                                }
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                )

                                Box(
                                    modifier = Modifier
                                        .animateItem() 
                                        .zIndex(zIndex)
                                        .graphicsLayer {
                                            this.translationY = translationY
                                            this.shadowElevation = elevation.toPx()
                                            this.alpha = if (isTarget) 0.3f else 1f
                                        }
                                        .background(cardBgColor, RoundedCornerShape(if (isDragging) 16.dp else 0.dp))
                                        .clip(RoundedCornerShape(if (isDragging) 16.dp else 0.dp))
                                ) {
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = true,
                                        enableDismissFromEndToStart = true,
                                        backgroundContent = {
                                            val isDismissing = dismissState.targetValue != SwipeToDismissBoxValue.Settled
                                            val color by animateColorAsState(
                                                targetValue = if (isDismissing) ThemeColors.Red.copy(alpha = 0.8f) else Color.Transparent,
                                                label = "dismissColor"
                                            )
                                            val scale by animateFloatAsState(
                                                targetValue = if (isDismissing) 1.2f else 0.8f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                ),
                                                label = "dismissScale"
                                            )
                                            val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) 
                                                Alignment.CenterStart else Alignment.CenterEnd

                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .padding(vertical = 4.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(color)
                                                    .padding(horizontal = 24.dp),
                                                contentAlignment = alignment
                                            ) {
                                                if (isDismissing) {
                                                    Icon(
                                                        Icons.Filled.Delete,
                                                        contentDescription = "Remove",
                                                        tint = Color.White,
                                                        modifier = Modifier.scale(scale)
                                                    )
                                                }
                                            }
                                        },
                                        content = {
                                            Box {
                                                SongCard(
                                                    context,
                                                    song,
                                                    autoHighlight = false,
                                                    highlighted = i == queueIndex,
                                                    leading = {
                                                        Checkbox(
                                                            checked = selectedSongIndices.contains(i),
                                                            onCheckedChange = {
                                                                if (selectedSongIndices.contains(i)) {
                                                                    selectedSongIndices.remove(i)
                                                                } else {
                                                                    selectedSongIndices.add(i)
                                                                }
                                                            },
                                                            modifier = Modifier.offset((-4).dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                    },
                                                    thumbnailLabel = {
                                                        Text((i + 1).toString())
                                                    },
                                                    onClick = {
                                                        context.symphony.radio.jumpTo(i)
                                                        coroutineScope.launch {
                                                            listState.animateScrollToItem(i)
                                                        }
                                                    },
                                                )
                                                if (i < queueIndex) {
                                                    Box(
                                                        modifier = Modifier
                                                            .matchParentSize()
                                                            .background(
                                                                MaterialTheme.colorScheme.background.copy(alpha = 0.4f)
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    if (showSaveDialog) {
        NewPlaylistDialog(
            context,
            initialSongIds = queue.toList(),
            onDone = { playlist ->
                showSaveDialog = false
                context.symphony.groove.playlist.add(playlist)
            },
            onDismissRequest = {
                showSaveDialog = false
            }
        )
    }
}