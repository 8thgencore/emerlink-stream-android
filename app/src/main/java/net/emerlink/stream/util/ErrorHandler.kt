package net.emerlink.stream.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.emerlink.stream.R
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

class ErrorHandler(private val context: Context) {
    
    private val _errorState = MutableStateFlow<ErrorState>(ErrorState.None)
    val errorState: StateFlow<ErrorState> = _errorState.asStateFlow()
    
    fun handleStreamError(throwable: Throwable) {
        Log.e(TAG, "Stream error: ${throwable.message}", throwable)
        
        val errorState = when (throwable) {
            is ConnectException, 
            is SocketException, 
            is SocketTimeoutException -> {
                ErrorState.ConnectionError(context.getString(R.string.connection_failed))
            }
            is UnknownHostException -> {
                ErrorState.ConnectionError(context.getString(R.string.unknown_host))
            }
            is SSLHandshakeException, 
            is CertificateException -> {
                ErrorState.SecurityError(context.getString(R.string.ssl_error))
            }
            is IOException -> {
                ErrorState.IOError(context.getString(R.string.io_error))
            }
            is IllegalArgumentException -> {
                ErrorState.ConfigurationError(context.getString(R.string.configuration_error))
            }
            is SecurityException -> {
                ErrorState.PermissionError(context.getString(R.string.permission_error))
            }
            is OutOfMemoryError -> {
                ErrorState.ResourceError(context.getString(R.string.memory_error))
            }
            else -> {
                ErrorState.UnknownError(throwable.message ?: context.getString(R.string.unknown_error))
            }
        }
        
        _errorState.value = errorState
        
        // Показать сообщение пользователю
        Toast.makeText(context, errorState.message, Toast.LENGTH_LONG).show()
    }
    
    fun clearError() {
        _errorState.value = ErrorState.None
    }
    
    companion object {
        private const val TAG = "ErrorHandler"
    }
}

sealed class ErrorState(val message: String) {
    data object None : ErrorState("")
    class ConnectionError(message: String) : ErrorState(message)
    class SecurityError(message: String) : ErrorState(message)
    class IOError(message: String) : ErrorState(message)
    class ConfigurationError(message: String) : ErrorState(message)
    class PermissionError(message: String) : ErrorState(message)
    class ResourceError(message: String) : ErrorState(message)
    class UnknownError(message: String) : ErrorState(message)
} 