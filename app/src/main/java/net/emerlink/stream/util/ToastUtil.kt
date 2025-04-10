package net.emerlink.stream.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes

object ToastUtil {
    private val mainHandler = Handler(Looper.getMainLooper())
    
    fun showToast(context: Context, message: String, isLongDuration: Boolean = false) {
        val duration = if (isLongDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        mainHandler.post {
            Toast.makeText(context, message, duration).show()
        }
    }
    
    fun showToast(context: Context, @StringRes messageResId: Int, isLongDuration: Boolean = false) {
        showToast(context, context.getString(messageResId), isLongDuration)
    }
} 