@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.camera.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import net.emerlink.stream.presentation.camera.viewmodel.CameraViewModel

/**
 * AdaptiveSpacerComponent that adjusts size based on screen size and orientation
 *
 * @param smallSize spacing size for small screens
 * @param largeSize spacing size for large screens
 * @param isVertical whether the spacer is vertical (height) or horizontal (width)
 */
@Composable
fun AdaptiveSpacer(
    smallSize: Int = 8,
    largeSize: Int = 16,
    isVertical: Boolean = true,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    val isLargeScreen = screenWidth >= 600 || screenHeight >= 600

    val size = if (isLargeScreen) largeSize.dp else smallSize.dp

    if (isVertical) {
        Spacer(modifier = Modifier.height(size))
    } else {
        Spacer(modifier = Modifier.width(size))
    }
}

@Composable
fun CameraControls(
    isLandscape: Boolean,
    isStreaming: Boolean,
    isFlashOn: Boolean,
    isMuted: Boolean,
    viewModel: CameraViewModel,
    onSettingsClick: () -> Unit,
) {
    if (isLandscape) {
        LandscapeCameraControls(
            isStreaming = isStreaming,
            isFlashOn = isFlashOn,
            isMuted = isMuted,
            viewModel = viewModel,
            onSettingsClick = onSettingsClick
        )
    } else {
        PortraitCameraControls(
            isStreaming = isStreaming,
            isFlashOn = isFlashOn,
            isMuted = isMuted,
            viewModel = viewModel,
            onSettingsClick = onSettingsClick
        )
    }
}

@Composable
fun LandscapeCameraControls(
    isStreaming: Boolean,
    isFlashOn: Boolean,
    isMuted: Boolean,
    viewModel: CameraViewModel,
    onSettingsClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier.padding(end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsButton(onClick = onSettingsClick)
            MuteButton(
                isMuted = isMuted,
                onClick = {
                    viewModel.toggleMute()
                }
            )

            AdaptiveSpacer(smallSize = 4, largeSize = 8, isVertical = true)
            RecordButton(
                isStreaming = isStreaming,
                onClick = {
                    if (isStreaming) {
                        viewModel.stopStreamingWithConfirmation()
                    } else {
                        viewModel.startStreaming()
                    }
                }
            )
            AdaptiveSpacer(smallSize = 4, largeSize = 8, isVertical = true)

            PhotoButton(
                onClick = {
                    viewModel.takePhoto()
                }
            )
            FlashButton(
                isFlashOn = isFlashOn,
                onClick = {
                    viewModel.toggleFlash()
                }
            )
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeContent),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(modifier = Modifier) {
            SwitchCameraButton(onClick = { viewModel.switchCamera() })
        }
    }
}

@Composable
fun PortraitCameraControls(
    isStreaming: Boolean,
    isFlashOn: Boolean,
    isMuted: Boolean,
    viewModel: CameraViewModel,
    onSettingsClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 6.dp, start = 16.dp, end = 16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            FlashButton(
                isFlashOn = isFlashOn,
                onClick = {
                    viewModel.toggleFlash()
                }
            )
            PhotoButton(
                onClick = {
                    viewModel.takePhoto()
                }
            )
            AdaptiveSpacer(smallSize = 8, largeSize = 16, isVertical = false)

            RecordButton(
                isStreaming = isStreaming,
                onClick = {
                    if (isStreaming) {
                        viewModel.stopStreamingWithConfirmation()
                    } else {
                        viewModel.startStreaming()
                    }
                }
            )
            AdaptiveSpacer(smallSize = 8, largeSize = 16, isVertical = false)

            MuteButton(
                isMuted = isMuted,
                onClick = {
                    viewModel.toggleMute()
                }
            )
            SettingsButton(onClick = onSettingsClick)
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 0.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            SwitchCameraButton(onClick = { viewModel.switchCamera() })
        }
    }
}
