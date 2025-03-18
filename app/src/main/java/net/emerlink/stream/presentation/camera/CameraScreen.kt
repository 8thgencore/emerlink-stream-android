@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.camera

import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.emerlink.stream.presentation.camera.components.*
import net.emerlink.stream.presentation.camera.viewmodel.CameraViewModel

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
    val showSettingsConfirmDialog by viewModel.showSettingsConfirmDialog.collectAsStateWithLifecycle()
    val showStreamInfo by viewModel.showStreamInfo.collectAsStateWithLifecycle()
    val streamInfo by viewModel.streamInfo.collectAsStateWithLifecycle()
    val showPermissionDialog by viewModel.showPermissionDialog.collectAsStateWithLifecycle()
    val permissionType by viewModel.permissionType.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()

    // Permission handling
    lateinit var requestCameraPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
    lateinit var requestMicrophonePermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>

    // Define microphone permission launcher first
    requestMicrophonePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                openGlView?.let { view -> initializeCamera(viewModel, view) }
            } else {
                viewModel.showPermissionDialog("microphone")
                // Request again after showing dialog
                Handler(Looper.getMainLooper()).postDelayed({
                    requestMicrophonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }, 1000)
            }
        }

    // Then define camera permission launcher
    requestCameraPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                requestMicrophonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            } else {
                viewModel.showPermissionDialog("camera")
                // Request again after showing dialog
                Handler(Looper.getMainLooper()).postDelayed({
                    requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }, 1000)
            }
        }

    // Screen state receiver handling
    DisposableEffect(Unit) {
        val screenStateReceiver =
            createScreenStateReceiver(
                onScreenOff = {
                    viewModel.setScreenWasOff(true)
                    // Only release camera if not streaming
                    if (!isStreaming) {
                        viewModel.releaseCamera()
                    }
                },
                onUserPresent = {
                    // Всегда восстанавливать превью после разблокировки экрана
                    openGlView?.let { view ->
                        viewModel.restartPreview(view)
                        viewModel.setScreenWasOff(false)
                    }
                }
            )

        val disposeScreenReceiver =
            registerScreenStateReceiver(
                context = context,
                receiver = screenStateReceiver,
                onDispose = {}
            )

        onDispose(disposeScreenReceiver)
    }

    // Lifecycle observer
    DisposableEffect(lifecycleOwner) {
        val observer =
            createLifecycleObserver(
                onPause = {
                    // Only stop preview but don't stop streaming
                    if (!isStreaming) {
                        viewModel.stopPreview()
                    }
                },
                onStop = {
                    // Only release camera resources if not streaming
                    if (!isStreaming) {
                        viewModel.releaseCamera()
                    }
                },
                onResume = {
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
            )

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Stream status receiver
    DisposableEffect(isStreaming) {
        val streamStatusReceiver =
            createStreamStatusReceiver {
                viewModel.updateStreamingState(false)
            }

        val disposeStreamReceiver =
            registerStreamStatusReceiver(
                context = context,
                receiver = streamStatusReceiver,
                onDispose = {}
            )

        onDispose(disposeStreamReceiver)
    }

    // UI components
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(if (!isLandscape) WindowInsets.safeDrawing else WindowInsets.ime)
    ) {
        CameraPreview(
            viewModel = viewModel,
            onOpenGlViewCreated = { view ->
                viewModel.setOpenGlView(view)
                requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
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
                handleSettingsClick(
                    isStreaming = isStreaming,
                    viewModel = viewModel,
                    navigateToSettings = onSettingsClick
                )
            }
        )

        // Audio level indicator positioned at the bottom left with safe area padding
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.BottomStart
        ) {
            AudioLevelIndicator(
                audioLevel = audioLevel,
                isMuted = isMuted,
                isLandscape = isLandscape
            )
        }
    }

    // Dialogs
    if (showSettingsConfirmDialog) {
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
