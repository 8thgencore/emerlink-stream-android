package net.emerlink.stream.core

import android.content.Context
import android.util.Log
import net.emerlink.stream.R
import net.emerlink.stream.core.notification.NotificationManager

class ErrorHandler(
    private val context: Context,
) {
    @Suppress("ktlint:standard:backing-property-naming")
    private val notificationManager = NotificationManager.getInstance(context)

    fun handleStreamError(e: Exception) {
        Log.e("ErrorHandler", "Handling stream error", e)

        val errorMessage =
            when {
                e.message?.contains("Wrong address") == true ->
                    context.getString(R.string.unknown_host)

                e.message?.contains("Connection timeout") == true ->
                    context.getString(R.string.connection_failed)

                e.message?.contains("Unauthorized") == true ->
                    context.getString(R.string.auth_error)

                e.message?.contains("Permission denied") == true ->
                    context.getString(R.string.permission_error)

                else -> context.getString(R.string.unknown_error) + ": " + e.message
            }

        notificationManager.showErrorSafely(errorMessage)
    }
}
