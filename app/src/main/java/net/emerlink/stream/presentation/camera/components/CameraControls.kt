@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.camera.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.emerlink.stream.presentation.camera.viewmodel.CameraViewModel

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
                        Modifier
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

            Spacer(modifier = Modifier.height(8.dp))
            RecordButton(
                isStreaming = isStreaming,
                onClick = {
                    if (isStreaming) {
                        viewModel.stopStreaming()
                    } else {
                        viewModel.startStreaming()
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

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

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Box(modifier = Modifier.padding(start = 16.dp)) {
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
                viewModel.stopStreaming()
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
        Spacer(modifier = Modifier.width(16.dp))
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
        Spacer(modifier = Modifier.width(16.dp))
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
