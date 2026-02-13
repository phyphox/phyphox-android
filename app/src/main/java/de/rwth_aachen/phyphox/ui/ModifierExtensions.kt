package de.rwth_aachen.phyphox.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Extension to add a skeleton loading effect (shimmer) to a Composable.
 */
fun Modifier.skeleton(
    show: Boolean = true,
    radius: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(radius),
): Modifier = if (show) {
    composed {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val translateAnimation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1000,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmer",
        )

        val shimmerColors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.surfaceVariant,
        )

        val brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnimation - 500f, translateAnimation - 500f),
            end = Offset(translateAnimation, translateAnimation),
        )

        background(brush, shape)
    }
} else {
    this
}

/**
 * Marks a view as loading. When [isLoading] is true, the content is hidden and a skeleton
 * shimmer is displayed in its place, preserving the size of the Composable.
 */
fun Modifier.placeholder(
    isLoading: Boolean,
    shape: Shape = RoundedCornerShape(4.dp),
): Modifier = if (isLoading) {
    this
        .drawWithContent {
            // Do not draw the content
        }
        .skeleton(show = true, shape = shape)
} else {
    this
}
