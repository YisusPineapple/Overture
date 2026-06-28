package io.github.zyrouge.symphony.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.KeepScreenAwake
import io.github.zyrouge.symphony.ui.components.LyricsText
import io.github.zyrouge.symphony.ui.components.ScaffoldDialog
import io.github.zyrouge.symphony.ui.components.Slider
import io.github.zyrouge.symphony.ui.components.TimedContentTextStyle
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.nowPlaying.NothingPlaying
import io.github.zyrouge.symphony.ui.view.nowPlaying.NowPlayingSeekBar
import io.github.zyrouge.symphony.ui.view.nowPlaying.NowPlayingTraditionalControls
import io.github.zyrouge.symphony.ui.view.nowPlaying.defaultHorizontalPadding
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
object LyricsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsView(context: ViewContext) {
    val keepScreenAwake by context.symphony.settings.lyricsKeepScreenAwake.flow.collectAsState()

    if (keepScreenAwake) {
        KeepScreenAwake()
    }

    NowPlayingObserver(context) { data ->
        var showOffsetDialog by remember { mutableStateOf(false) }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
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
                    title = {
                        TopAppBarMinimalTitle {
                            Text(
                                context.symphony.t.Lyrics +
                                        (data?.song?.title?.let { " - $it" } ?: "")
                            )
                        }
                    },
                    actions = {
                        if (data != null) {
                            IconButton(onClick = { showOffsetDialog = true }) {
                                Icon(Icons.Filled.Timer, contentDescription = "Sync Offset")
                            }
                        } else {
                            IconButtonPlaceholder()
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                )
            },
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
            ) {
                when {
                    data != null -> {
                        val dynamicColor = data.dominantColor ?: MaterialTheme.colorScheme.primary
                        val textColor = data.contentColor ?: MaterialTheme.colorScheme.onSurface
                        
                        Column {
                            Box(modifier = Modifier.weight(1f)) {
                                LyricsText(
                                    context,
                                    style = TimedContentTextStyle(
                                        highlighted = MaterialTheme.typography.titleMedium.copy(
                                            color = LocalContentColor.current,
                                        ),
                                        active = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        ),
                                        inactive = MaterialTheme.typography.titleMedium.copy(
                                            color = LocalContentColor.current.copy(alpha = 0.5f),
                                        ),
                                        spacing = 8.dp,
                                    ),
                                    padding = PaddingValues(
                                        horizontal = defaultHorizontalPadding,
                                        vertical = 12.dp,
                                    ),
                                )
                            }
                            Spacer(modifier = Modifier.height(defaultHorizontalPadding + 8.dp))
                            NowPlayingSeekBar(context, activeColor = dynamicColor, textColor = textColor)
                            Spacer(modifier = Modifier.height(defaultHorizontalPadding + 8.dp))
                            NowPlayingTraditionalControls(context, data = data, activeColor = dynamicColor, textColor = textColor)
                            Spacer(modifier = Modifier.height(defaultHorizontalPadding + 8.dp))
                        }
                    }

                    else -> NothingPlaying(context)
                }
            }
        }

        if (showOffsetDialog && data != null) {
            LyricsOffsetDialog(
                context = context,
                song = data.song,
                onDismissRequest = { showOffsetDialog = false }
            )
        }
    }
}

@Composable
fun LyricsOffsetDialog(
    context: ViewContext,
    song: Song,
    onDismissRequest: () -> Unit
) {
    var offset by remember { mutableFloatStateOf(song.lyricsOffset.toFloat()) }
    
    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Sync Offset") },
        content = {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = "${offset.toLong()} ms",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Slider(
                    modifier = Modifier.fillMaxWidth(),
                    value = offset,
                    range = -5000f..5000f,
                    label = { Text("${it.toLong()} ms") },
                    onChange = { offset = (it / 50).roundToInt() * 50f } // Snap to 50ms increments
                )
            }
        },
        actions = {
            TextButton(onClick = { offset = 0f }) { Text(context.symphony.t.Reset) }
            TextButton(onClick = onDismissRequest) { Text(context.symphony.t.Cancel) }
            TextButton(onClick = {
                context.symphony.groove.song.updateLyricsOffset(song.id, offset.toLong())
                onDismissRequest()
            }) { Text(context.symphony.t.Done) }
        }
    )
}