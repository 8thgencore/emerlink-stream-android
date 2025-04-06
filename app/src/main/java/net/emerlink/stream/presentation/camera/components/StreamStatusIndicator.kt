@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StreamStatusIndicator(
    isStreaming: Boolean,
    isRecording: Boolean,
    isLandscape: Boolean,
    onInfoClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (!isLandscape) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    } else {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    }
                ).padding(start = 16.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .clickable { onInfoClick() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .background(
                                color =
                                    when {
                                        isStreaming -> MaterialTheme.colorScheme.error
                                        isRecording -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                shape = CircleShape
                            )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        when {
                            isStreaming && isRecording -> "LIVE & RECORDING"
                            isStreaming -> "LIVE"
                            isRecording -> "RECORDING"
                            else -> "OFF"
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Stream Info",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
