@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.ui.camera

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.core.AppIntentActions
import net.emerlink.stream.presentation.ui.camera.components.*
import net.emerlink.stream.presentation.ui.camera.viewmodel.CameraViewModel

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun CameraScreen(
    onSettingsClick: () -> Unit,
    viewModel: CameraViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Service connection
    DisposableEffect(Unit) {
        viewModel.bindService(context)
        onDispose {
            viewModel.unbindService(context)
        }
    }

    // State collection
    val openGlView by viewModel.openGlView.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val isFlashOn by viewModel.isFlashOn.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val showStreamInfo by viewModel.showStreamInfo.collectAsStateWithLifecycle()
    val streamInfo by viewModel.streamInfo.collectAsStateWithLifecycle()
    val screenWasOff by viewModel.screenWasOff.collectAsStateWithLifecycle()
    val isPreviewActive by viewModel.isPreviewActive.collectAsStateWithLifecycle()
    val showPermissionDialog by viewModel.showPermissionDialog.collectAsStateWithLifecycle()
    val permissionType by viewModel.permissionType.collectAsStateWithLifecycle()

    // Permission handling
    val requestCameraPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                checkMicrophonePermission(context, viewModel, openGlView)
            } else {
                viewModel.showPermissionDialog("camera")
            }
        }

    val requestMicrophonePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                openGlView?.let { view -> initializeCamera(viewModel, view) }
            } else {
                viewModel.showPermissionDialog("microphone")
            }
        }

    // Screen state receiver
    DisposableEffect(key1 = Unit) {
        val screenStateReceiver =
            createScreenStateReceiver(
                onScreenOff = {
                    viewModel.setScreenWasOff(true)
                    viewModel.releaseCamera()
                },
                onUserPresent = {
                    if (screenWasOff && openGlView != null) {
                        openGlView?.let { view ->
                            viewModel.restartPreview(view)
                            viewModel.setScreenWasOff(false)
                        }
                    }
                }
            )

        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        context.registerReceiver(screenStateReceiver, filter)

        onDispose {
            try {
                context.unregisterReceiver(screenStateReceiver)
            } catch (e: Exception) {
                Log.e("CameraScreen", "Error unregistering receiver", e)
            }
        }
    }

    // Lifecycle observer
    DisposableEffect(key1 = lifecycleOwner) {
        val observer =
            createLifecycleObserver(
                onPause = { viewModel.stopPreview() },
                onStop = { viewModel.releaseCamera() },
                onResume = {
                    if (!isPreviewActive && openGlView != null) {
                        openGlView?.let { view ->
                            if (view.holder.surface?.isValid == true) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    try {
                                        viewModel.restartPreview(view)
                                        viewModel.setScreenWasOff(false)
                                    } catch (e: Exception) {
                                        Log.e("CameraScreen", "Error restarting preview", e)
                                    }
                                }, 500)
                            }
                        }
                    }
                }
            )

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Stream state broadcast receiver
    DisposableEffect(key1 = isStreaming) {
        val streamStoppedReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.action == AppIntentActions.BROADCAST_STREAM_STOPPED) {
                        viewModel.updateStreamingState(false)
                    }
                }
            }

        val filter = IntentFilter(AppIntentActions.BROADCAST_STREAM_STOPPED)
        LocalBroadcastManager.getInstance(context).registerReceiver(streamStoppedReceiver, filter)

        onDispose {
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(streamStoppedReceiver)
            } catch (e: Exception) {
                Log.e("CameraScreen", "Error unregistering broadcast receiver", e)
            }
        }
    }

    // UI components
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            viewModel = viewModel,
            onOpenGlViewCreated = { view ->
                viewModel.setOpenGlView(view)
                checkCameraPermission(context, viewModel, requestCameraPermissionLauncher)
            }
        )

        StreamStatusIndicator(
            isStreaming = isStreaming,
            isLandscape = isLandscape,
            onInfoClick = { viewModel.toggleStreamInfo() }
        )

        if (showStreamInfo) {
            StreamInfoPanel(
                streamInfo = streamInfo,
                isLandscape = isLandscape
            )
        }

        CameraControls(
            isLandscape = isLandscape,
            isStreaming = isStreaming,
            isFlashOn = isFlashOn,
            isMuted = isMuted,
            viewModel = viewModel,
            onSettingsClick = {
                if (isStreaming) {
                    viewModel.setShowSettingsConfirmDialog(true)
                } else {
                    try {
                        viewModel.stopPreview()
                        viewModel.setOpenGlView(null)
                        onSettingsClick()
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Error stopping preview before settings", e)
                    }
                }
            }
        )
    }

    // Dialogs
    if (viewModel.showSettingsConfirmDialog.value) {
        SettingsConfirmationDialog(
            onDismiss = { viewModel.setShowSettingsConfirmDialog(false) },
            onConfirm = {
                viewModel.setShowSettingsConfirmDialog(false)
                try {
                    viewModel.stopStreaming()
                    viewModel.stopPreview()
                    viewModel.setOpenGlView(null)
                    onSettingsClick()
                } catch (e: Exception) {
                    Log.e("CameraScreen", "Error transitioning to settings", e)
                }
            }
        )
    }

    if (showPermissionDialog) {
        PermissionDialog(
            permissionType = permissionType,
            onDismiss = { viewModel.dismissPermissionDialog() }
        )
    }
}

private fun checkCameraPermission(
    context: Context,
    viewModel: CameraViewModel,
    requestCameraPermissionLauncher: ActivityResultLauncher<String>,
) {
    when {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED -> {
            checkMicrophonePermission(context, viewModel, viewModel.openGlView.value)
        }

        else -> {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

private fun checkMicrophonePermission(
    context: Context,
    viewModel: CameraViewModel,
    openGlView: OpenGlView?,
) {
    when {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED -> {
            openGlView?.let { view ->
                initializeCamera(viewModel, view)
            }
        }

        else -> {
            // This will be called through callback after user responds to permission dialog
        }
    }
}

private fun initializeCamera(
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
