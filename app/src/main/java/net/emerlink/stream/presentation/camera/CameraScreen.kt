@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.emerlink.stream.presentation.camera.components.*
import net.emerlink.stream.presentation.camera.viewmodel.CameraViewModel
import net.emerlink.stream.service.StreamService

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
                openGlView?.let { view -> initializeCamera(viewModel, view) }
            }
        )

    // Service lifecycle handling
    DisposableEffect(Unit) {
        // Start and bind to service
        val intent = Intent(context, StreamService::class.java)
        context.startForegroundService(intent)
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

    // Lifecycle observer - modify preview handling
    DisposableEffect(lifecycleOwner) {
        val observer = createLifecycleObserver(
            onPause = {
                Log.d("CameraScreen", "onPause")
                if (!viewModel.isStreaming.value) {
                    viewModel.stopPreview()
                }
            },
            onStop = {
                Log.d("CameraScreen", "onStop")
                viewModel.stopPreview()
            },
            onResume = {
                Log.d("CameraScreen", "onResume")
                openGlView?.let { view ->
                    if (!viewModel.isStreaming.value && !viewModel.isPreviewActive.value) {
                        viewModel.startPreview(view)
                    }
                }
            },
        )

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
                    viewModel.setOpenGlView(null)
                    onSettingsClick()
                } catch (e: Exception) {
                    Log.e("CameraScreen", "Error transitioning to settings", e)
                }
            }
        )
    }
}
