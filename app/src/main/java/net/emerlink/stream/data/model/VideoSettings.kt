package net.emerlink.stream.data.model

import android.util.Size

/**
 * Data class for video settings
 */
data class VideoSettings(
    val resolution: Size = Size(1920, 1080),
    val fps: Int = 30,
    val bitrate: Int = 2500,
    val codec: String = "h264",
    val adaptiveBitrate: Boolean = true,
    val recordVideo: Boolean = false,
    val streamVideo: Boolean = true,
    val screenOrientation: String = "landscape",
    val keyframeInterval: Int = 10,
    val videoSource: String = "camera"
) 