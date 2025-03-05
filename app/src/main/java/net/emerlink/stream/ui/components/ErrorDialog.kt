package net.emerlink.stream.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.emerlink.stream.R
import net.emerlink.stream.util.ErrorState

@Composable
fun ErrorDialog(
    errorState: ErrorState,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    if (errorState !is ErrorState.None) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(id = R.string.error)) },
            text = { Text(text = errorState.message) },
            confirmButton = {
                if (onRetry != null) {
                    Button(onClick = {
                        onDismiss()
                        onRetry()
                    }) {
                        Text(text = stringResource(id = R.string.retry))
                    }
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text(text = stringResource(id = R.string.dismiss))
                }
            }
        )
    }
} 