package net.emerlink.stream.data.model

import android.util.Size

/**
 * Класс для хранения всех настроек потоковой передачи
 */
data class StreamSettings(
    // Stream connection settings
    var connection: ConnectionSettings = ConnectionSettings(),
    // Audio settings
    var enableAudio: Boolean = false,
    var audioSampleRate: Int = 0,
    var audioStereo: Boolean = false,
    var audioEchoCancel: Boolean = false,
    var audioNoiseReduction: Boolean = false,
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
)
