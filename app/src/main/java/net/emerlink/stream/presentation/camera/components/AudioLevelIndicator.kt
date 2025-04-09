@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.camera.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A component that shows a vertical audio level indicator
 *
 * @param audioLevel Current audio level (0.0f - 1.0f)
 * @param isMuted Whether audio is muted
 * @param isLandscape Whether device is in landscape orientation
 */
@Composable
fun AudioLevelIndicator(
    audioLevel: Float,
    isMuted: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    // Animate audio level to make it look smoother
    val animatedLevel by animateFloatAsState(
        targetValue = audioLevel,
        animationSpec = tween(durationMillis = 100),
        label = "audioLevel"
    )

    val colorBrush =
        if (isMuted) {
            // Gray gradient when muted
            Brush.verticalGradient(
                colors =
                    listOf(
                        Color.Gray.copy(alpha = 0.5f),
                        Color.Gray.copy(alpha = 0.7f)
                    )
            )
        } else {
            // Colored gradient based on audio level
            Brush.verticalGradient(
                colors =
                    listOf(
                        Color(0xFF4CAF50), // Green
                        Color(0xFFFFEB3B), // Yellow
                        Color(0xFFFF5722) // Orange/Red
                    )
            )
        }

    // Container for the audio level bar
    Box(
        modifier =
            modifier
                .padding(
                    start = if (isLandscape) 12.dp else 26.dp,
                    bottom = if (isLandscape) 8.dp else 88.dp
                ).width(24.dp)
                .height(if (isLandscape) 100.dp else 150.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        // The active audio level indicator bar
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animatedLevel.coerceIn(0f, 1f))
                    .background(colorBrush)
        )
    }
}
