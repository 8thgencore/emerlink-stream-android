@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.ui.camera.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.emerlink.stream.service.StreamService

@Composable
fun CameraControls(
    isLandscape: Boolean,
    isStreaming: Boolean,
    isFlashOn: Boolean,
    isMuted: Boolean,
    streamService: StreamService?,
    onStreamingToggle: (Boolean) -> Unit,
    onFlashToggle: (Boolean) -> Unit,
    onMuteToggle: (Boolean) -> Unit,
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
                streamService = streamService,
                onStreamingToggle = onStreamingToggle,
                onFlashToggle = onFlashToggle,
                onMuteToggle = onMuteToggle,
                onSettingsClick = onSettingsClick
            )
        } else {
            PortraitCameraControls(
                isStreaming = isStreaming,
                isFlashOn = isFlashOn,
                isMuted = isMuted,
                streamService = streamService,
                onStreamingToggle = onStreamingToggle,
                onFlashToggle = onFlashToggle,
                onMuteToggle = onMuteToggle,
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
    streamService: StreamService?,
    onStreamingToggle: (Boolean) -> Unit,
    onFlashToggle: (Boolean) -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
) {
    // Right side controls column
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            modifier = Modifier.padding(end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top controls
            SettingsButton(onClick = onSettingsClick)
            MuteButton(
                isMuted = isMuted,
                onClick = {
                    streamService?.toggleMute(!isMuted)
                    onMuteToggle(!isMuted)
                }
            )

            // Record button in the middle
            Spacer(modifier = Modifier.height(8.dp))
            RecordButton(
                isStreaming = isStreaming,
                onClick = {
                    if (isStreaming) {
                        streamService?.stopStream(null, null)
                        onStreamingToggle(false)
                    } else {
                        streamService?.startStream()
                        onStreamingToggle(true)
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Bottom controls
            PhotoButton(
                onClick = {
                    streamService?.takePhoto()
                }
            )
            FlashButton(
                isFlashOn = isFlashOn,
                onClick = {
                    val newState = streamService?.toggleLantern() == true
                    onFlashToggle(newState)
                }
            )
        }
    }

    // Left side - camera switch button
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Box(modifier = Modifier.padding(start = 16.dp)) {
            SwitchCameraButton(onClick = { streamService?.switchCamera() })
        }
    }
}

@Composable
fun PortraitCameraControls(
    isStreaming: Boolean,
    isFlashOn: Boolean,
    isMuted: Boolean,
    streamService: StreamService?,
    onStreamingToggle: (Boolean) -> Unit,
    onFlashToggle: (Boolean) -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
) {
    // Main Controls (Record button in center)
    RecordButton(
        isStreaming = isStreaming,
        onClick = {
            if (isStreaming) {
                streamService?.stopStream(null, null)
                onStreamingToggle(false)
            } else {
                streamService?.startStream()
                onStreamingToggle(true)
            }
        }
    )

    // Left Controls
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
                val newState = streamService?.toggleLantern() == true
                onFlashToggle(newState)
            }
        )
        Spacer(modifier = Modifier.width(16.dp))
        PhotoButton(
            onClick = {
                streamService?.takePhoto()
            }
        )
    }

    // Right Controls
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
                streamService?.toggleMute(!isMuted)
                onMuteToggle(!isMuted)
            }
        )
        Spacer(modifier = Modifier.width(16.dp))
        SettingsButton(onClick = onSettingsClick)
    }

    // Center Left - Camera Switch
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp, start = 0.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        SwitchCameraButton(onClick = { streamService?.switchCamera() })
    }
}
