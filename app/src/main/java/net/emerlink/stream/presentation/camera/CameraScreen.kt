@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.camera

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // State collection
    val openGlView by viewModel.openGlView.collectAsStateWithLifecycle()
    val isServiceBound by viewModel.isServiceBound.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isFlashOn by viewModel.isFlashOn.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val showSettingsConfirmDialog by viewModel.showSettingsConfirmDialog.collectAsStateWithLifecycle()
    val showStreamInfo by viewModel.showStreamInfo.collectAsStateWithLifecycle()
    val streamInfo by viewModel.streamInfo.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
    val flashOverlayVisible by viewModel.flashOverlayVisible.collectAsStateWithLifecycle()
    val isPreviewActive by viewModel.isPreviewActive.collectAsStateWithLifecycle()

    // Service lifecycle handling
    DisposableEffect(Unit) {
        viewModel.bindService(context)
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            viewModel.unbindService(context)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> Log.d("CameraScreen", "onPause")

                    Lifecycle.Event.ON_STOP -> viewModel.stopPreview()

                    Lifecycle.Event.ON_RESUME -> viewModel.refreshStreamInfo()

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(openGlView, isServiceBound) {
        if (openGlView != null && isServiceBound) {
            if (!isPreviewActive) {
                viewModel.startPreview(openGlView!!)
            }
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
            onOpenGlViewCreated = { view -> viewModel.setOpenGlView(view) }
        )

        StreamStatusIndicator(
            isStreaming = isStreaming,
            isRecording = isRecording,
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
                    viewModel.requestConfirmation(
                        show = true,
                        actionOnConfirm = {
                            viewModel.stopStreaming()
                            onSettingsClick()
                        }
                    )
                } else {
                    onSettingsClick()
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
                onDismiss = { viewModel.requestConfirmation(false) },
                onConfirm = { viewModel.confirmRequestedAction() }
            )
        }
    }
}
