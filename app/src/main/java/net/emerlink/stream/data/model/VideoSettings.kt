package net.emerlink.stream.data.model

import android.util.Size

/**
 * Data class for video settings
 */
data class VideoSettings(
    val resolution: Size,
    val fps: Int,
    val bitrate: Int,
    val codec: String,
    val adaptiveBitrate: Boolean,
    val recordVideo: Boolean,
    val streamVideo: Boolean,
    val screenOrientation: String,
    val keyframeInterval: Int,
    val videoSource: String
) 