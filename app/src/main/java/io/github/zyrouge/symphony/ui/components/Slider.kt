package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.utils.RangeUtils

@Composable
fun Slider(
    modifier: Modifier,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: @Composable (Float) -> Unit,
    onChange: (Float) -> Unit,
) {
    Column(modifier = modifier) {
        var dragging by remember { mutableStateOf(false) }
        var dragRatio by remember { mutableFloatStateOf(0f) }

        val rawRatio = RangeUtils.calculateRatioFromValue(value, range).coerceIn(0f, 1f)
        
        // M3E Spring Animation for smooth playback progression (only animates when not dragging)
        val animatedRatio by animateFloatAsState(
            targetValue = if (dragging) dragRatio else rawRatio,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "SliderRatioAnimation"
        )

        // M3E Physics: Track thickens when interacted with
        val trackHeight by animateDpAsState(
            targetValue = if (dragging) 12.dp else 4.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "TrackHeightAnimation"
        )

        // M3E Physics: Thumb expands when interacted with
        val thumbRadius by animateDpAsState(
            targetValue = if (dragging) 10.dp else 6.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "ThumbRadiusAnimation"
        )

        val activeColor = MaterialTheme.colorScheme.primary
        val inactiveColor = MaterialTheme.colorScheme.surfaceVariant

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp) // Touch target size expanded for better UX
                .padding(horizontal = 20.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val newRatio = (offset.x / size.width).coerceIn(0f, 1f)
                            onChange(RangeUtils.calculateValueFromRatio(newRatio, range))
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragRatio = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragging = false
                            onChange(RangeUtils.calculateValueFromRatio(dragRatio, range))
                        },
                        onDragCancel = {
                            dragging = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragRatio = (change.position.x / size.width).coerceIn(0f, 1f)
                            onChange(RangeUtils.calculateValueFromRatio(dragRatio, range))
                        }
                    )
                }
        ) {
            val trackY = size.height / 2f
            val trackH = trackHeight.toPx()
            val cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)

            // 1. Draw Inactive Track (Background)
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, trackY - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = cornerRadius
            )

            // 2. Draw Active Track (Foreground)
            val activeWidth = size.width * animatedRatio
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(0f, trackY - trackH / 2f),
                size = Size(activeWidth, trackH),
                cornerRadius = cornerRadius
            )

            // 3. Draw Liquid Glow (Only when dragging, zero cost fallback for Android 9)
            if (dragging) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.25f),
                    radius = thumbRadius.toPx() * 2.2f,
                    center = Offset(activeWidth, trackY)
                )
            }

            // 4. Draw Thumb
            drawCircle(
                color = activeColor,
                radius = thumbRadius.toPx(),
                center = Offset(activeWidth, trackY)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val highlightedTextStyle = MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            val markerTextStyle = MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            ProvideTextStyle(markerTextStyle) {
                label(range.start)
            }
            ProvideTextStyle(highlightedTextStyle) {
                label(value)
            }
            ProvideTextStyle(markerTextStyle) {
                label(range.endInclusive)
            }
        }
    }
}
