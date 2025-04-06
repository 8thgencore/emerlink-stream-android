@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.camera

import android.Manifest
import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.WindowManager
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isFlashOn by viewModel.isFlashOn.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val showSettingsConfirmDialog by viewModel.showSettingsConfirmDialog.collectAsStateWithLifecycle()
    val showStreamInfo by viewModel.showStreamInfo.collectAsStateWithLifecycle()
    val streamInfo by viewModel.streamInfo.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
    val flashOverlayVisible by viewModel.flashOverlayVisible.collectAsStateWithLifecycle()
    val isSettingsDialogFromSettings by viewModel.isSettingsDialogFromSettings.collectAsStateWithLifecycle()

    // Permission launchers
    lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    lateinit var requestMicrophonePermissionLauncher: ActivityResultLauncher<String>
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
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )
    requestNotificationPermissionLauncher =
        createPermissionLauncher(
            permission = Manifest.permission.POST_NOTIFICATIONS,
            onGranted = {
                openGlView?.let { view -> viewModel.startPreview(view) }
            }
        )

    // Service lifecycle handling
    DisposableEffect(Unit) {
        viewModel.bindService(context)

        // Keep screen on
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            if (!viewModel.isStreaming.value) {
                viewModel.unbindService(context)
            }
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        Log.d("CameraScreen", "onPause")
                    }

                    Lifecycle.Event.ON_STOP -> {
                        Log.d("CameraScreen", "onStop")
                        viewModel.stopPreview()
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        Log.d("CameraScreen", "onResume")
                        viewModel.refreshStreamInfo()
                    }

                    else -> {}
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
            isRecording = isRecording,
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
            isStreaming = isStreaming || isRecording,
            isFlashOn = isFlashOn,
            isMuted = isMuted,
            viewModel = viewModel,
            onSettingsClick = {
                if (isStreaming || isRecording) {
                    viewModel.setShowSettingsConfirmDialog(show = true, fromSettings = true)
                } else {
                    try {
                        viewModel.setOpenGlView(null)
                        onSettingsClick()
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Error stopping preview before settings", e)
                    }
                }
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

        // Screen flash overlay for photo capture
        if (flashOverlayVisible) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.5f))
            )
            viewModel.hideFlashOverlayAfterDelay()
        }

        // Dialogs
        if (showSettingsConfirmDialog) {
            SettingsConfirmationDialog(
                onDismiss = {
                    viewModel.setShowSettingsConfirmDialog(
                        show = false,
                        fromSettings = isSettingsDialogFromSettings
                    )
                },
                onConfirm = {
                    viewModel.setShowSettingsConfirmDialog(show = false, fromSettings = isSettingsDialogFromSettings)
                    try {
                        viewModel.stopStreaming()
                        viewModel.setOpenGlView(null)

                        if (isSettingsDialogFromSettings) {
                            onSettingsClick()
                        }
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Error transitioning to settings", e)
                    }
                }
            )
        }
    }
}
