package net.emerlink.stream.data.model

import android.util.Size

enum class ScreenOrientation {
    PORTRAIT,
    LANDSCAPE,
    ;

    companion object {
        fun fromString(value: String): ScreenOrientation =
            when (value.lowercase()) {
                "portrait" -> PORTRAIT
                "landscape" -> LANDSCAPE
                else -> LANDSCAPE
            }
    }
}

/**
 * Data class for video settings
 */
data class VideoSettings(
    val resolution: Size = Size(1920, 1080),
    val fps: Int = 30,
    val bitrate: Int = 2500,
    val codec: String = "H264",
    val adaptiveBitrate: Boolean = true,
    val recordVideo: Boolean = false,
    val streamVideo: Boolean = true,
    val screenOrientation: ScreenOrientation = ScreenOrientation.LANDSCAPE,
    val keyframeInterval: Int = 10,
    val videoSource: String = "camera",
)
