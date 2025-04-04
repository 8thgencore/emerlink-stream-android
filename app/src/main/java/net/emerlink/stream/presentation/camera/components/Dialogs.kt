@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.camera.components

import androidx.compose.material3.AlertDialog
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
