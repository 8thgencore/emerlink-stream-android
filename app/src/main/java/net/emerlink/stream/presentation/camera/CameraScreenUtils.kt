package net.emerlink.stream.presentation.camera

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pedro.library.view.OpenGlView
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
            viewModel.setOpenGlView(null)
            navigateToSettings()
        } catch (e: Exception) {
            Log.e("CameraScreen", "Error stopping preview before settings", e)
        }
    }
}
