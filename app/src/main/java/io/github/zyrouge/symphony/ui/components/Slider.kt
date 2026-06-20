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
        
        val animatedRatio by animateFloatAsState(
            targetValue = if (dragging) dragRatio else rawRatio,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "SliderRatioAnimation"
        )

        // Overture: M3E Thick Slider Physics
        val trackHeight by animateDpAsState(
            targetValue = if (dragging) 24.dp else 12.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "TrackHeightAnimation"
        )

        val activeColor = MaterialTheme.colorScheme.primary
        val inactiveColor = MaterialTheme.colorScheme.surfaceVariant

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp) // Accessible touch target
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

            // Inactive Track
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, trackY - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = cornerRadius
            )

            // Active Track
            val activeWidth = size.width * animatedRatio
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(0f, trackY - trackH / 2f),
                size = Size(activeWidth, trackH),
                cornerRadius = cornerRadius
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