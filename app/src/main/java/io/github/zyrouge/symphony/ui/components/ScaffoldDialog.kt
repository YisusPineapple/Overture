package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Suppress("ConstPropertyName")
object ScaffoldDialogDefaults {
    const val PreferredMaxHeight = 0.8f
}

@Composable
fun ScaffoldDialog(
    title: @Composable () -> Unit,
    titleLeading: (@Composable () -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    topBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    contentHeight: Float? = null,
    removeActionsVerticalPadding: Boolean = false,
    onDismissRequest: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    var showAnimated by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        showAnimated = true
    }

    Dialog(onDismissRequest = onDismissRequest) {
        AnimatedVisibility(
            visible = showAnimated,
            enter = scaleIn(
                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), 
                initialScale = 0.9f
            ) + fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp), // M3E: Softer, larger corners for dialogs
                modifier = Modifier.run {
                    val maxHeight = (configuration.screenHeightDp * 0.9f).dp
                    when {
                        contentHeight != null -> height(maxHeight.times(contentHeight))
                        else -> requiredHeightIn(max = maxHeight)
                    }
                },
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        titleLeading?.invoke()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(20.dp, 0.dp)
                                .weight(1f)
                        ) {
                            ProvideTextStyle(
                                value = MaterialTheme.typography.titleLarge.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                title()
                            }
                        }
                        titleTrailing?.invoke()
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    topBar?.invoke()
                    Box(
                        modifier = Modifier.run {
                            contentHeight?.let { weight(it) } ?: weight(1f, fill = false)
                        }
                    ) {
                        content()
                    }
                    actions?.let {
                        if (!removeActionsVerticalPadding) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp, 8.dp),
                        ) {
                            actions()
                        }
                        if (!removeActionsVerticalPadding) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}