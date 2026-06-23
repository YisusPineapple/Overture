package io.github.zyrouge.symphony.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Wysiwyg
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.LyricsAlignment
import io.github.zyrouge.symphony.services.LyricsAnimationEngine
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.components.settings.ConsiderContributingTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsFloatInputTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsOptionTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSideHeading
import io.github.zyrouge.symphony.ui.components.settings.SettingsSliderTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSwitchTile
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.NowPlayingControlsLayout
import io.github.zyrouge.symphony.ui.view.NowPlayingLyricsLayout
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
object NowPlayingSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSettingsView(context: ViewContext) {
    val scrollState = rememberScrollState()
    val nowPlayingControlsLayout by context.symphony.settings.nowPlayingControlsLayout.flow.collectAsState()
    val nowPlayingAdditionalInfo by context.symphony.settings.nowPlayingAdditionalInfo.flow.collectAsState()
    val nowPlayingSeekControls by context.symphony.settings.nowPlayingSeekControls.flow.collectAsState()
    val nowPlayingLyricsLayout by context.symphony.settings.nowPlayingLyricsLayout.flow.collectAsState()
    val lyricsKeepScreenAwake by context.symphony.settings.lyricsKeepScreenAwake.flow.collectAsState()
    
    val lyricsAlignment by context.symphony.settings.lyricsAlignment.flow.collectAsState()
    val lyricsAnimationEngine by context.symphony.settings.lyricsAnimationEngine.flow.collectAsState()
    val lyricsUnfocusedOpacity by context.symphony.settings.lyricsUnfocusedOpacity.flow.collectAsState()
    val lyricsSyncOffset by context.symphony.settings.lyricsSyncOffset.flow.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${context.symphony.t.Settings} - ${context.symphony.t.NowPlaying}")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            context.navController.popBackStack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButtonPlaceholder()
                },
            )
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    ConsiderContributingTile(context)
                    SettingsSideHeading(context.symphony.t.NowPlaying)
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.Dashboard, null)
                        },
                        title = {
                            Text(context.symphony.t.ControlsLayout)
                        },
                        value = nowPlayingControlsLayout,
                        values = NowPlayingControlsLayout.entries
                            .associateWith { it.label(context) },
                        onChange = { value ->
                            context.symphony.settings.nowPlayingControlsLayout.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.AutoMirrored.Filled.Wysiwyg, null)
                        },
                        title = {
                            Text(context.symphony.t.ShowAudioInformation)
                        },
                        value = nowPlayingAdditionalInfo,
                        onChange = { value ->
                            context.symphony.settings.nowPlayingAdditionalInfo.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.Forward30, null)
                        },
                        title = {
                            Text(context.symphony.t.ShowSeekControls)
                        },
                        value = nowPlayingSeekControls,
                        onChange = { value ->
                            context.symphony.settings.nowPlayingSeekControls.setValue(value)
                        }
                    )
                    
                    // Overture: Lyrics Settings Section
                    SettingsSideHeading(context.symphony.t.Lyrics)
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.AutoMirrored.Outlined.Article, null)
                        },
                        title = {
                            Text(context.symphony.t.LyricsLayout)
                        },
                        value = nowPlayingLyricsLayout,
                        values = NowPlayingLyricsLayout.entries
                            .associateWith { it.label(context) },
                        onChange = { value ->
                            context.symphony.settings.nowPlayingLyricsLayout.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.FormatAlignLeft, null)
                        },
                        title = {
                            Text("Text Alignment") // Hardcoded until added to i18n
                        },
                        value = lyricsAlignment,
                        values = mapOf(
                            LyricsAlignment.Left to "Left",
                            LyricsAlignment.Center to "Center",
                            LyricsAlignment.Right to "Right"
                        ),
                        onChange = { value ->
                            context.symphony.settings.lyricsAlignment.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.Animation, null)
                        },
                        title = {
                            Text("Animation Engine")
                        },
                        value = lyricsAnimationEngine,
                        values = mapOf(
                            LyricsAnimationEngine.Normal to "Normal (Optimized)",
                            LyricsAnimationEngine.Expressive to "Expressive (M3E Springs)"
                        ),
                        onChange = { value ->
                            context.symphony.settings.lyricsAnimationEngine.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsSliderTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.Opacity, null)
                        },
                        title = {
                            Text("Unfocused Opacity")
                        },
                        label = { value ->
                            Text("${(value * 100).toInt()}%")
                        },
                        range = 0.1f..1f,
                        initialValue = lyricsUnfocusedOpacity,
                        onChange = { value ->
                            context.symphony.settings.lyricsUnfocusedOpacity.setValue(value)
                        },
                        onReset = {
                            context.symphony.settings.lyricsUnfocusedOpacity.setValue(0.3f)
                        },
                    )
                    HorizontalDivider()
                    SettingsFloatInputTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.Timer, null)
                        },
                        title = {
                            Text("Sync Offset (ms)")
                        },
                        value = lyricsSyncOffset.toFloat(),
                        presets = listOf(-1000f, -500f, 0f, 500f, 1000f),
                        labelText = { "${it.toInt()} ms" },
                        onReset = {
                            context.symphony.settings.lyricsSyncOffset.setValue(0)
                        },
                        onChange = { value ->
                            context.symphony.settings.lyricsSyncOffset.setValue(value.toInt())
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.Lyrics, null)
                        },
                        title = {
                            Text(context.symphony.t.KeepScreenAwakeOnLyrics)
                        },
                        value = lyricsKeepScreenAwake,
                        onChange = { value ->
                            context.symphony.settings.lyricsKeepScreenAwake.setValue(value)
                        }
                    )
                }
            }
        }
    )
}

fun NowPlayingControlsLayout.label(context: ViewContext) = when (this) {
    NowPlayingControlsLayout.CompactLeft -> context.symphony.t.CompactLeft
    NowPlayingControlsLayout.CompactRight -> context.symphony.t.CompactRight
    NowPlayingControlsLayout.Traditional -> context.symphony.t.Traditional
}

fun NowPlayingLyricsLayout.label(context: ViewContext) = when (this) {
    NowPlayingLyricsLayout.ReplaceArtwork -> context.symphony.t.ReplaceArtwork
    NowPlayingLyricsLayout.SeparatePage -> context.symphony.t.SeparatePage
}