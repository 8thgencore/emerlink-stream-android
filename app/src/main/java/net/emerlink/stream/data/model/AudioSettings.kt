package net.emerlink.stream.data.model

/**
 * Data class for audio settings
 */
data class AudioSettings(
    val enabled: Boolean,
    val bitrate: Int,
    val sampleRate: Int,
    val stereo: Boolean,
    val echoCancel: Boolean,
    val noiseReduction: Boolean,
    val codec: String
) 