@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.camera

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
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

    // State collection
    val openGlView by viewModel.openGlView.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val isFlashOn by viewModel.isFlashOn.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val showSettingsConfirmDialog by viewModel.showSettingsConfirmDialog.collectAsStateWithLifecycle()
    val showStreamInfo by viewModel.showStreamInfo.collectAsStateWithLifecycle()
    val streamInfo by viewModel.streamInfo.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()

    // Existing permission launchers
    lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    lateinit var requestMicrophonePermissionLauncher: ActivityResultLauncher<String>
    lateinit var requestLocationPermissionLauncher: ActivityResultLauncher<String>
    lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>

    @Composable
    fun createPermissionLauncher(
        permission: String,
        onGranted: () -> Unit,
    ): ActivityResultLauncher<String> =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                onGranted()
            } else {
                Log.e("CameraScreen", "Permission $permission denied")
            }
        }

    // Permission handling
    requestCameraPermissionLauncher =
        createPermissionLauncher(
            permission = Manifest.permission.CAMERA,
            onGranted = {
                requestMicrophonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )
    requestMicrophonePermissionLauncher =
        createPermissionLauncher(
            permission = Manifest.permission.RECORD_AUDIO,
            onGranted = {
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        )
    requestLocationPermissionLauncher =
        createPermissionLauncher(
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            onGranted = {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )
    requestNotificationPermissionLauncher =
        createPermissionLauncher(
            permission = Manifest.permission.POST_NOTIFICATIONS,
            onGranted = {
                openGlView?.let { view -> initializeCamera(viewModel, view) }
            }
        )

    // Permission check on mount
    DisposableEffect(Unit) {
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { /* No-op */ }
    }

    // Service connection
    DisposableEffect(Unit) {
        viewModel.bindService(context)
        onDispose {
            viewModel.unbindService(context)
        }
    }

    // Screen state receiver handling
    DisposableEffect(Unit) {
        val screenStateReceiver =
            createScreenStateReceiver(
                onScreenOff = {
                    viewModel.setScreenWasOff(true)
                    viewModel.stopPreview()
                    if (!isStreaming) {
                        viewModel.releaseCamera()
                    }
                },
                onUserPresent = {
                    if (viewModel.screenWasOff.value) {
                        openGlView?.let { view ->
                            if (!viewModel.isPreviewActive.value) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    viewModel.restartPreview(view)
                                    viewModel.setScreenWasOff(false)
                                    Log.d("CameraScreen", "Restored preview after screen unlock")
                                }, 300)
                            }
                        }
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
                    viewModel.stopPreview()
                },
                onStop = {
                    if (!isStreaming) {
                        viewModel.releaseCamera()
                    }
                },
                onResume = {
                    openGlView?.let { view ->
                        if (view.holder.surface?.isValid == true &&
                            !viewModel.isPreviewActive.value
                        ) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    viewModel.restartPreview(view)
                                    Log.d("CameraScreen", "Restored preview after app resumed")
                                } catch (e: Exception) {
                                    Log.e("CameraScreen", "Error restarting preview", e)
                                }
                            }, 300)
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
                // Only set the view and request permissions if we don't already have an active preview
                if (!viewModel.isPreviewActive.value) {
                    viewModel.setOpenGlView(view)
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
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
}
