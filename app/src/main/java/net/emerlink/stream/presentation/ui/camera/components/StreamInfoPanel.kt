@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.ui.camera.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.emerlink.stream.data.model.StreamInfo

@Composable
fun StreamInfoPanel(
    streamInfo: StreamInfo,
    isLandscape: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (!isLandscape) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    } else {
                        Modifier
                    }
                ).padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            modifier =
                Modifier
                    .padding(top = if (isLandscape) 16.dp else 48.dp)
                    .then(if (isLandscape) Modifier.width(300.dp) else Modifier)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow(label = "Protocol", value = streamInfo.protocol)
                InfoRow(label = "Resolution", value = streamInfo.resolution)
                InfoRow(label = "Bitrate", value = streamInfo.bitrate)
                InfoRow(label = "FPS", value = streamInfo.fps)
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
