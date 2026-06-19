package io.github.zyrouge.symphony.ui.helpers

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import coil.request.ImageRequest

enum class ScreenOrientation {
    PORTRAIT,
    LANDSCAPE;

    val isPortrait: Boolean get() = this == PORTRAIT
    val isLandscape: Boolean get() = this == LANDSCAPE

    companion object {
        fun fromConfiguration(configuration: Configuration) = when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> LANDSCAPE
            else -> PORTRAIT
        }

        fun fromConstraints(constraints: BoxWithConstraintsScope) =
            fromDimension(constraints.maxHeight, constraints.maxWidth)

        fun fromDimension(height: Dp, width: Dp) = when {
            width.value > height.value -> LANDSCAPE
            else -> PORTRAIT
        }
    }
}

fun createHandyImageRequest(context: Context, image: Any, fallback: Int) =
    createHandyImageRequest(context, image, fallbackResId = fallback)

private fun createHandyImageRequest(
    context: Context,
    image: Any,
    fallbackResId: Int? = null,
) = ImageRequest.Builder(context).apply {
    data(image)
    fallbackResId?.let {
        placeholder(it)
        fallback(it)
        error(it)
    }
    crossfade(true)
}

// Overture: M3E Tactile Feedback Modifier
fun Modifier.bounceScale(
    interactionSource: InteractionSource,
    scaleDown: Float = 0.95f
) = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "BounceScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}