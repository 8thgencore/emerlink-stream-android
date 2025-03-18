package net.emerlink.stream.presentation.ui.camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Creates a screen state broadcast receiver
 */
fun createScreenStateReceiver(
    onScreenOff: () -> Unit,
    onUserPresent: () -> Unit,
): BroadcastReceiver =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d("CameraScreen", "Screen turned off")
                    onScreenOff()
                }
                Intent.ACTION_USER_PRESENT -> {
                    Log.d("CameraScreen", "User unlocked screen")
                    onUserPresent()
                }
            }
        }
    }

/**
 * Creates a lifecycle observer for camera management
 */
fun createLifecycleObserver(
    onPause: () -> Unit,
    onStop: () -> Unit,
    onResume: () -> Unit,
): LifecycleEventObserver =
    LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_PAUSE -> onPause()
            Lifecycle.Event.ON_STOP -> onStop()
            Lifecycle.Event.ON_RESUME -> onResume()
            else -> {}
        }
    }
