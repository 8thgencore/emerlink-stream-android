package net.emerlink.stream.model

import android.util.Size

/**
 * Класс для хранения всех настроек потоковой передачи
 */
data class StreamSettings(
    // Stream connection settings
    var connection: ConnectionSettings = ConnectionSettings(),
    // Audio settings
    var sampleRate: Int = 0,
    var stereo: Boolean = false,
    var echoCancel: Boolean = false,
    var noiseReduction: Boolean = false,
    var enableAudio: Boolean = false,
    var audioBitrate: Int = 0,
    var audioCodec: String = "",
    // Video settings
    var fps: Int = 0,
    var resolution: Size? = null,
    var adaptiveBitrate: Boolean = false,
    var record: Boolean = false,
    var stream: Boolean = false,
    var bitrate: Int = 0,
    var codec: String = "",
    var uid: String = "",
    val iFrameInterval: Int = 2,
    // Camera
    var videoSource: String = "",
)
