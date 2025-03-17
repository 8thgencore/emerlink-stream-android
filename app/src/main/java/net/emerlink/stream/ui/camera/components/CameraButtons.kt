@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.ui.camera.components

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun RecordButton(
    isStreaming: Boolean,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Icon(
            imageVector = if (isStreaming) Icons.Default.VideocamOff else Icons.Default.Videocam,
            contentDescription = if (isStreaming) "Stop Streaming" else "Start Streaming"
        )
    }
}

@Composable
fun FlashButton(
    isFlashOn: Boolean,
    onClick: () -> Unit,
) {
    CameraControlButton(
        icon = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
        contentDescription = if (isFlashOn) "Turn Flash Off" else "Turn Flash On",
        onClick = onClick
    )
}

@Composable
fun PhotoButton(onClick: () -> Unit) {
    CameraControlButton(
        icon = Icons.Default.PhotoCamera,
        contentDescription = "Take Photo",
        onClick = {
            Log.d("CameraScreen", "Нажата кнопка фото")
            onClick()
        }
    )
}

@Composable
fun MuteButton(
    isMuted: Boolean,
    onClick: () -> Unit,
) {
    CameraControlButton(
        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
        contentDescription = if (isMuted) "Unmute" else "Mute",
        onClick = onClick
    )
}

@Composable
fun SwitchCameraButton(onClick: () -> Unit) {
    CameraControlButton(
        icon = Icons.Default.Cameraswitch,
        contentDescription = "Switch Camera",
        onClick = onClick
    )
}

@Composable
fun SettingsButton(onClick: () -> Unit) {
    CameraControlButton(
        icon = Icons.Default.Settings,
        contentDescription = "Settings",
        onClick = onClick
    )
}

@Composable
private fun CameraControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
    }
}
