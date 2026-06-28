package io.github.zyrouge.symphony.ui.view.nowPlaying

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.ui.helpers.ScreenOrientation
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.NowPlayingData
import io.github.zyrouge.symphony.ui.view.NowPlayingDefaults
import io.github.zyrouge.symphony.ui.view.NowPlayingLyricsLayout
import io.github.zyrouge.symphony.ui.view.NowPlayingStates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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

    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeight = remember(configuration, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    
    // Overture: Fluid Swipe-to-Minimize Gesture
    val offsetY = remember { Animatable(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = offsetY.value
                // M3E Depth effect: Scale down slightly as it's dragged down
                val scale = 1f - (offsetY.value / screenHeight) * 0.05f
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            // If dragged down more than 20% of the screen, dismiss
                            if (offsetY.value > screenHeight * 0.2f) {
                                offsetY.animateTo(screenHeight, tween(250))
                                context.navController.popBackStack()
                            } else {
                                // Otherwise, spring back to top
                                offsetY.animateTo(
                                    targetValue = 0f, 
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy, 
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            offsetY.animateTo(
                                targetValue = 0f, 
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy, 
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            // Prevent dragging upwards past the top edge
                            offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f))
                        }
                    }
                )
            }
    ) {
        val orientation = ScreenOrientation.fromConstraints(this@BoxWithConstraints)
        
        val glassOverlayColor = MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
        val colorMatrix = remember { ColorMatrix().apply { setToSaturation(2f) } }

        Crossfade(
            targetState = data.song,
            animationSpec = tween(1000),
            label = "AmbientBackground"
        ) { song ->
            AsyncImage(
                model = song.createArtworkImageRequest(context.symphony).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(colorMatrix),
                modifier = Modifier
                    .fillMaxSize()
                    .blur(100.dp) 
                    .drawWithContent {
                        drawContent()
                        drawRect(glassOverlayColor)
                    }
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
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
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(32.dp), 
                                contentAlignment = Alignment.Center,
                            ) {
                                NowPlayingBodyCover(context, data, states, orientation)
                            }
                            Box(modifier = Modifier.weight(1.2f).fillMaxHeight()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.Center 
                                ) {
                                    NowPlayingLandscapeAppBar(context)
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