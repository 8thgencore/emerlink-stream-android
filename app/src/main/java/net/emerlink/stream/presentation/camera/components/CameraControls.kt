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
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (!isLandscape) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    } else {
                        Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    }
                ).padding(16.dp),
        contentAlignment = Alignment.BottomCenter
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
}

@Composable
fun LandscapeCameraControls(
    isStreaming: Boolean,
    isFlashOn: Boolean,
    isMuted: Boolean,
    viewModel: CameraViewModel,
    onSettingsClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
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

            AdaptiveSpacer(smallSize = 8, largeSize = 16, isVertical = true)
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
            AdaptiveSpacer(smallSize = 8, largeSize = 16, isVertical = true)

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
                .windowInsetsPadding(WindowInsets.navigationBars),
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

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        FlashButton(
            isFlashOn = isFlashOn,
            onClick = {
                viewModel.toggleFlash()
            }
        )
        AdaptiveSpacer(smallSize = 12, largeSize = 24, isVertical = false)
        PhotoButton(
            onClick = {
                viewModel.takePhoto()
            }
        )
    }

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        MuteButton(
            isMuted = isMuted,
            onClick = {
                viewModel.toggleMute()
            }
        )
        AdaptiveSpacer(smallSize = 12, largeSize = 24, isVertical = false)
        SettingsButton(onClick = onSettingsClick)
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp, start = 0.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        SwitchCameraButton(onClick = { viewModel.switchCamera() })
    }
}
