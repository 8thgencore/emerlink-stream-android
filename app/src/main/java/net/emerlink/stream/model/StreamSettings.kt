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
    // Camera
    var videoSource: String = "",
    // Advanced Video Settings
    var keyframeInterval: Int = 0,
    var videoProfile: String = "",
    var videoLevel: String = "",
    var bitrateMode: String = "",
    var encodingQuality: String = "",
    // Network Settings
    var bufferSize: Int = 0,
    var connectionTimeout: Int = 0,
    var autoReconnect: Boolean = false,
    var reconnectDelay: Int = 0,
    var maxReconnectAttempts: Int = 0,
    // Stability Settings
    var lowLatencyMode: Boolean = false,
    var hardwareRotation: Boolean = false,
    var dynamicFps: Boolean = false,
)
