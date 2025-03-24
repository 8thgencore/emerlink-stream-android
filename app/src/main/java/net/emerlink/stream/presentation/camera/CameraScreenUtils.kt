package net.emerlink.stream.presentation.camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.core.AppIntentActions
import net.emerlink.stream.presentation.camera.viewmodel.CameraViewModel

/**
 * Initialize camera with the provided OpenGlView
 */
fun initializeCamera(
    viewModel: CameraViewModel,
    view: OpenGlView,
) {
    if (!viewModel.isPreviewActive.value && viewModel.isServiceConnected.value) {
        try {
            viewModel.startPreview(view)
        } catch (e: Exception) {
            Log.e("CameraScreen", "Error starting preview", e)
        }
    }
}

/**
 * Creates a screen state broadcast receiver
 */
fun createScreenStateReceiver(
    onScreenOff: () -> Unit,
    onUserPresent: () -> Unit,
): BroadcastReceiver =
    object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
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

/**
 * Creates a stream status broadcast receiver
 */
fun createStreamStatusReceiver(onStreamStopped: () -> Unit): BroadcastReceiver =
    object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            if (intent.action == AppIntentActions.BROADCAST_STREAM_STOPPED) {
                onStreamStopped()
            }
        }
    }

/**
 * Registers a broadcast receiver for stream status events
 */
fun registerStreamStatusReceiver(
    context: Context,
    receiver: BroadcastReceiver,
    onDispose: () -> Unit,
): () -> Unit {
    val filter = IntentFilter(AppIntentActions.BROADCAST_STREAM_STOPPED)
    LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter)

    return {
        try {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e("CameraScreen", "Error unregistering broadcast receiver", e)
        }
        onDispose()
    }
}

/**
 * Registers a screen state receiver
 */
fun registerScreenStateReceiver(
    context: Context,
    receiver: BroadcastReceiver,
    onDispose: () -> Unit,
): () -> Unit {
    val filter =
        IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
    context.registerReceiver(receiver, filter)

    return {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e("CameraScreen", "Error unregistering receiver", e)
        }
        onDispose()
    }
}

/**
 * Handle settings button click
 */
fun handleSettingsClick(
    isStreaming: Boolean,
    viewModel: CameraViewModel,
    navigateToSettings: () -> Unit,
) {
    if (isStreaming) {
        viewModel.setShowSettingsConfirmDialog(true)
    } else {
        try {
            viewModel.stopPreview()
            viewModel.setOpenGlView(null)
            navigateToSettings()
        } catch (e: Exception) {
            Log.e("CameraScreen", "Error stopping preview before settings", e)
        }
    }
}
