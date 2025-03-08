package net.emerlink.stream.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import net.emerlink.stream.R
import net.emerlink.stream.notification.NotificationManager

class ErrorHandler(private val context: Context) {

    private val _errorState = MutableStateFlow<ErrorState>(ErrorState.None)

    private val notificationManager = NotificationManager.getInstance(context)

    fun handleStreamError(e: Exception) {
        Log.e("ErrorHandler", "Handling stream error", e)
        
        val errorMessage = when {
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
        
        notificationManager.showErrorNotification(errorMessage)
    }

    fun clearError() {
        _errorState.value = ErrorState.None
    }
}

sealed class ErrorState(val message: String) {
    data object None : ErrorState("")
}