package net.emerlink.stream.data.preferences

import android.content.SharedPreferences
import android.util.Log
import android.util.Size
import net.emerlink.stream.model.ConnectionSettings
import net.emerlink.stream.model.StreamSettings
import net.emerlink.stream.model.StreamType

/**
 * Класс для загрузки настроек из SharedPreferences
 */
class PreferencesLoader {
    companion object {
        private const val TAG = "PreferencesLoader"
    }

    /**
     * Загружает все настройки из SharedPreferences и возвращает объект настроек
     */
    fun loadPreferences(preferences: SharedPreferences): StreamSettings {
        Log.d(TAG, "Загрузка настроек")

        // Get port string from preferences and convert to Int
        val portString = preferences.getString(PreferenceKeys.STREAM_PORT, PreferenceKeys.STREAM_PORT_DEFAULT)
            ?: PreferenceKeys.STREAM_PORT_DEFAULT
        val portInt = portString.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0


        val connection = ConnectionSettings(
            protocol = StreamType.fromString(
                preferences.getString(PreferenceKeys.STREAM_PROTOCOL, PreferenceKeys.STREAM_PROTOCOL_DEFAULT)
                    ?: PreferenceKeys.STREAM_PROTOCOL_DEFAULT
            ),
            address = preferences.getString(PreferenceKeys.STREAM_ADDRESS, PreferenceKeys.STREAM_ADDRESS_DEFAULT)
                ?: PreferenceKeys.STREAM_ADDRESS_DEFAULT,
            port = portInt,
            path = preferences.getString(PreferenceKeys.STREAM_PATH, PreferenceKeys.STREAM_PATH_DEFAULT)
                ?: PreferenceKeys.STREAM_PATH_DEFAULT,
            streamKey = preferences.getString(PreferenceKeys.STREAM_KEY, PreferenceKeys.STREAM_KEY_DEFAULT)
                ?: PreferenceKeys.STREAM_KEY_DEFAULT,
            tcp = preferences.getBoolean(PreferenceKeys.STREAM_USE_TCP, PreferenceKeys.STREAM_USE_TCP_DEFAULT),
            username = preferences.getString(PreferenceKeys.STREAM_USERNAME, PreferenceKeys.STREAM_USERNAME_DEFAULT)
                ?: PreferenceKeys.STREAM_USERNAME_DEFAULT,
            password = preferences.getString(PreferenceKeys.STREAM_PASSWORD, PreferenceKeys.STREAM_PASSWORD_DEFAULT)
                ?: PreferenceKeys.STREAM_PASSWORD_DEFAULT,
            streamSelfSignedCert = preferences.getBoolean(
                PreferenceKeys.STREAM_SELF_SIGNED_CERT, PreferenceKeys.STREAM_SELF_SIGNED_CERT_DEFAULT
            ),
            certFile = preferences.getString(
                PreferenceKeys.STREAM_CERTIFICATE, PreferenceKeys.STREAM_CERTIFICATE_DEFAULT
            ),
            certPassword = preferences.getString(
                PreferenceKeys.STREAM_CERTIFICATE_PASSWORD, PreferenceKeys.STREAM_CERTIFICATE_PASSWORD_DEFAULT
            ) ?: PreferenceKeys.STREAM_CERTIFICATE_PASSWORD_DEFAULT,

            )

        return StreamSettings(
            // Connection settings
            connection = connection,

            // Audio settings
            sampleRate = preferences.getString(
                PreferenceKeys.AUDIO_SAMPLE_RATE, PreferenceKeys.AUDIO_SAMPLE_RATE_DEFAULT
            )?.toIntOrNull() ?: 44100,
            stereo = preferences.getBoolean(PreferenceKeys.AUDIO_STEREO, PreferenceKeys.AUDIO_STEREO_DEFAULT),
            echoCancel = preferences.getBoolean(
                PreferenceKeys.AUDIO_ECHO_CANCEL, PreferenceKeys.AUDIO_ECHO_CANCEL_DEFAULT
            ),
            noiseReduction = preferences.getBoolean(
                PreferenceKeys.AUDIO_NOISE_REDUCTION, PreferenceKeys.AUDIO_NOISE_REDUCTION_DEFAULT
            ),
            enableAudio = preferences.getBoolean(PreferenceKeys.ENABLE_AUDIO, PreferenceKeys.ENABLE_AUDIO_DEFAULT),
            audioBitrate = preferences.getString(PreferenceKeys.AUDIO_BITRATE, PreferenceKeys.AUDIO_BITRATE_DEFAULT)
                ?.toIntOrNull() ?: 128,
            audioCodec = preferences.getString(PreferenceKeys.AUDIO_CODEC, PreferenceKeys.AUDIO_CODEC_DEFAULT)
                ?: PreferenceKeys.AUDIO_CODEC_DEFAULT,

            // Video settings
            fps = preferences.getString(PreferenceKeys.VIDEO_FPS, PreferenceKeys.VIDEO_FPS_DEFAULT)?.toIntOrNull()
                ?: 30,
            resolution = getResolutionFromPreferences(preferences),
            adaptiveBitrate = preferences.getBoolean(
                PreferenceKeys.VIDEO_ADAPTIVE_BITRATE, PreferenceKeys.VIDEO_ADAPTIVE_BITRATE_DEFAULT
            ),
            record = preferences.getBoolean(PreferenceKeys.RECORD_VIDEO, PreferenceKeys.RECORD_VIDEO_DEFAULT),
            stream = preferences.getBoolean(PreferenceKeys.STREAM_VIDEO, PreferenceKeys.STREAM_VIDEO_DEFAULT),
            bitrate = preferences.getString(PreferenceKeys.VIDEO_BITRATE, PreferenceKeys.VIDEO_BITRATE_DEFAULT)
                ?.toIntOrNull() ?: 2500,
            codec = preferences.getString(PreferenceKeys.VIDEO_CODEC, PreferenceKeys.VIDEO_CODEC_DEFAULT)
                ?: PreferenceKeys.VIDEO_CODEC_DEFAULT,
            uid = preferences.getString(PreferenceKeys.UID, PreferenceKeys.UID_DEFAULT) ?: PreferenceKeys.UID_DEFAULT,

            // Camera
            videoSource = preferences.getString(PreferenceKeys.VIDEO_SOURCE, PreferenceKeys.VIDEO_SOURCE_DEFAULT)
                ?: PreferenceKeys.VIDEO_SOURCE_DEFAULT,

            // Advanced Video Settings
            keyframeInterval = preferences.getString(
                PreferenceKeys.VIDEO_KEYFRAME_INTERVAL, PreferenceKeys.VIDEO_KEYFRAME_INTERVAL_DEFAULT
            )?.toIntOrNull() ?: 2,
            videoProfile = preferences.getString(PreferenceKeys.VIDEO_PROFILE, PreferenceKeys.VIDEO_PROFILE_DEFAULT)
                ?: PreferenceKeys.VIDEO_PROFILE_DEFAULT,
            videoLevel = preferences.getString(PreferenceKeys.VIDEO_LEVEL, PreferenceKeys.VIDEO_LEVEL_DEFAULT)
                ?: PreferenceKeys.VIDEO_LEVEL_DEFAULT,
            bitrateMode = preferences.getString(
                PreferenceKeys.VIDEO_BITRATE_MODE, PreferenceKeys.VIDEO_BITRATE_MODE_DEFAULT
            ) ?: PreferenceKeys.VIDEO_BITRATE_MODE_DEFAULT,
            encodingQuality = preferences.getString(PreferenceKeys.VIDEO_QUALITY, PreferenceKeys.VIDEO_QUALITY_DEFAULT)
                ?: PreferenceKeys.VIDEO_QUALITY_DEFAULT,

            // Network Settings
            bufferSize = preferences.getString(
                PreferenceKeys.NETWORK_BUFFER_SIZE, PreferenceKeys.NETWORK_BUFFER_SIZE_DEFAULT
            )?.toIntOrNull() ?: 0,
            connectionTimeout = preferences.getString(
                PreferenceKeys.NETWORK_TIMEOUT, PreferenceKeys.NETWORK_TIMEOUT_DEFAULT
            )?.toIntOrNull() ?: 5000,
            autoReconnect = preferences.getBoolean(
                PreferenceKeys.NETWORK_RECONNECT, PreferenceKeys.NETWORK_RECONNECT_DEFAULT
            ),
            reconnectDelay = preferences.getString(
                PreferenceKeys.NETWORK_RECONNECT_DELAY, PreferenceKeys.NETWORK_RECONNECT_DELAY_DEFAULT
            )?.toIntOrNull() ?: 3000,
            maxReconnectAttempts = preferences.getString(
                PreferenceKeys.NETWORK_MAX_RECONNECT_ATTEMPTS, PreferenceKeys.NETWORK_MAX_RECONNECT_ATTEMPTS_DEFAULT
            )?.toIntOrNull() ?: 5,

            // Stability Settings
            lowLatencyMode = preferences.getBoolean(
                PreferenceKeys.STABILITY_LOW_LATENCY, PreferenceKeys.STABILITY_LOW_LATENCY_DEFAULT
            ),
            hardwareRotation = preferences.getBoolean(
                PreferenceKeys.STABILITY_HARDWARE_ROTATION, PreferenceKeys.STABILITY_HARDWARE_ROTATION_DEFAULT
            ),
            dynamicFps = preferences.getBoolean(
                PreferenceKeys.STABILITY_DYNAMIC_FPS, PreferenceKeys.STABILITY_DYNAMIC_FPS_DEFAULT
            )
        )
    }

    private fun getResolutionFromPreferences(preferences: SharedPreferences): Size {
        val resolutionString =
            preferences.getString(PreferenceKeys.VIDEO_RESOLUTION, PreferenceKeys.VIDEO_RESOLUTION_DEFAULT)
                ?: PreferenceKeys.VIDEO_RESOLUTION_DEFAULT
        val parts = resolutionString.split("x")
        return if (parts.size == 2) {
            try {
                Size(parts[0].toInt(), parts[1].toInt())
            } catch (_: NumberFormatException) {
                Size(1920, 1080) // Default if parsing fails
            }
        } else {
            Size(1920, 1080) // Default if format is incorrect
        }
    }
}