@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.ui.camera.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import net.emerlink.stream.R

@Composable
fun SettingsConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.stop_streaming)) },
        text = { Text(context.getString(R.string.stop_streaming_confirmation)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(context.getString(R.string.yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel))
            }
        }
    )
}

@Composable
fun PermissionDialog(
    permissionType: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val title =
        when (permissionType) {
            "camera" -> context.getString(R.string.camera_permission_title)
            "microphone" -> context.getString(R.string.microphone_permission_title)
            else -> context.getString(R.string.unknown_error)
        }

    val message =
        when (permissionType) {
            "camera" -> context.getString(R.string.camera_permission_message)
            "microphone" -> context.getString(R.string.microphone_permission_message)
            else -> context.getString(R.string.unknown_error)
        }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = { onDismiss() }) {
                Text(context.getString(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(context.getString(R.string.cancel))
            }
        }
    )
}
