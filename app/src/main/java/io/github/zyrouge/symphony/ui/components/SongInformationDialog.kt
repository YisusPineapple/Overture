package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.AlbumArtistViewRoute
import io.github.zyrouge.symphony.ui.view.AlbumViewRoute
import io.github.zyrouge.symphony.ui.view.ArtistViewRoute
import io.github.zyrouge.symphony.ui.view.GenreViewRoute
import io.github.zyrouge.symphony.utils.ActivityUtils
import io.github.zyrouge.symphony.utils.DurationUtils
import io.github.zyrouge.symphony.utils.SimplePath
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.round

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongInformationDialog(context: ViewContext, song: Song, onDismissRequest: () -> Unit) {
    val fileExtension = SimplePath(song.path).extension.uppercase()

    InformationDialog(
        context,
        content = {
            // Overture: Redesigned Audio Quality Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AudioFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = fileExtension,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        song.bitrateK?.let { AudioBadge(context.symphony.t.XKbps(it.toString())) }
                        song.samplingRateK?.let { AudioBadge(context.symphony.t.XKHz(it.toString())) }
                        song.channels?.let { AudioBadge(context.symphony.t.AudioChannels + ": $it") }
                        AudioBadge("${round((song.size / 1024 / 1024).toDouble())} MB")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata Section
            InformationKeyValue(context.symphony.t.TrackName) {
                LongPressCopyableText(context, song.title)
            }
            if (song.artists.isNotEmpty()) {
                InformationKeyValue(context.symphony.t.Artist) {
                    LongPressCopyableAndTappableText(context, song.artists) {
                        onDismissRequest()
                        context.navController.navigate(ArtistViewRoute(it))
                    }
                }
            }
            if (song.albumArtists.isNotEmpty()) {
                InformationKeyValue(context.symphony.t.AlbumArtist) {
                    LongPressCopyableAndTappableText(context, song.albumArtists) {
                        onDismissRequest()
                        context.navController.navigate(AlbumArtistViewRoute(it))
                    }
                }
            }
            context.symphony.groove.album.getIdFromSong(song)?.let { albumId ->
                InformationKeyValue(context.symphony.t.Album) {
                    LongPressCopyableAndTappableText(context, setOf(song.album!!)) {
                        onDismissRequest()
                        context.navController.navigate(AlbumViewRoute(albumId))
                    }
                }
            }
            if (song.genres.isNotEmpty()) {
                InformationKeyValue(context.symphony.t.Genre) {
                    LongPressCopyableAndTappableText(context, song.genres) {
                        onDismissRequest()
                        context.navController.navigate(GenreViewRoute(it))
                    }
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                song.year?.let {
                    InformationKeyValue(context.symphony.t.Year) {
                        LongPressCopyableText(context, it.toString())
                    }
                }
                song.trackNumber?.let {
                    InformationKeyValue(context.symphony.t.TrackNumber) {
                        LongPressCopyableText(context, "$it" + (song.trackTotal?.let { t -> "/$t" } ?: ""))
                    }
                }
                song.discNumber?.let {
                    InformationKeyValue(context.symphony.t.DiscNumber) {
                        LongPressCopyableText(context, "$it" + (song.discTotal?.let { t -> "/$t" } ?: ""))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // File Section
            InformationKeyValue(context.symphony.t.Path) {
                LongPressCopyableText(context, song.path)
            }
            InformationKeyValue(context.symphony.t.LastModified) {
                LongPressCopyableText(
                    context,
                    SimpleDateFormat.getInstance().format(Date(song.dateModified * 1000)),
                )
            }
        },
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun AudioBadge(text: String) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text, 
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), 
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LongPressCopyableAndTappableText(
    context: ViewContext,
    values: Set<String>,
    onTap: (String) -> Unit,
) {
    val textStyle = LocalTextStyle.current.copy(
        textDecoration = TextDecoration.Underline,
        color = MaterialTheme.colorScheme.primary
    )

    FlowRow {
        values.forEachIndexed { i, it ->
            Text(
                it,
                style = textStyle,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { _ ->
                            ActivityUtils.copyToClipboardAndNotify(context.symphony, it)
                        },
                        onTap = { _ ->
                            onTap(it)
                        },
                    )
                },
            )
            if (i != values.size - 1) {
                Text(", ", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}