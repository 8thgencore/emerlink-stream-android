package net.emerlink.stream.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.Size
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.model.StreamSettings

/**
 * Класс для загрузки настроек из SharedPreferences
 */
class PreferencesLoader(private val context: Context) {
    
    companion object {
        private const val TAG = "PreferencesLoader"
    }
    
    /**
     * Загружает все настройки из SharedPreferences и возвращает объект настроек
     */
    fun loadPreferences(preferences: SharedPreferences): StreamSettings {
        Log.d(TAG, "Загрузка настроек")
        
        val settings = StreamSettings()
        
        // UID
        settings.uid = preferences.getString(PreferenceKeys.UID, PreferenceKeys.UID_DEFAULT)
            ?: PreferenceKeys.UID_DEFAULT
        
        // Stream protocol
        settings.protocol = preferences.getString(
            PreferenceKeys.STREAM_PROTOCOL,
            PreferenceKeys.STREAM_PROTOCOL_DEFAULT
        ) ?: PreferenceKeys.STREAM_PROTOCOL_DEFAULT
        
        // Stream Preferences
        settings.stream = preferences.getBoolean(
            PreferenceKeys.STREAM_VIDEO,
            PreferenceKeys.STREAM_VIDEO_DEFAULT
        )
        
        settings.address = preferences.getString(
            PreferenceKeys.STREAM_ADDRESS,
            PreferenceKeys.STREAM_ADDRESS_DEFAULT
        ) ?: PreferenceKeys.STREAM_ADDRESS_DEFAULT
        
        settings.port = preferences
            .getString(PreferenceKeys.STREAM_PORT, PreferenceKeys.STREAM_PORT_DEFAULT)
            ?.toInt() ?: PreferenceKeys.STREAM_PORT_DEFAULT.toInt()
        
        settings.path = preferences.getString(
            PreferenceKeys.STREAM_PATH,
            PreferenceKeys.STREAM_PATH_DEFAULT
        ) ?: PreferenceKeys.STREAM_PATH_DEFAULT
        
        settings.tcp = preferences.getBoolean(
            PreferenceKeys.STREAM_USE_TCP,
            PreferenceKeys.STREAM_USE_TCP_DEFAULT
        )
        
        settings.username = preferences.getString(
            PreferenceKeys.STREAM_USERNAME,
            PreferenceKeys.STREAM_USERNAME_DEFAULT
        ) ?: PreferenceKeys.STREAM_USERNAME_DEFAULT
        
        settings.password = preferences.getString(
            PreferenceKeys.STREAM_PASSWORD,
            PreferenceKeys.STREAM_PASSWORD_DEFAULT
        ) ?: PreferenceKeys.STREAM_PASSWORD_DEFAULT
        
        settings.streamSelfSignedCert = preferences.getBoolean(
            PreferenceKeys.STREAM_SELF_SIGNED_CERT,
            PreferenceKeys.STREAM_SELF_SIGNED_CERT_DEFAULT
        )
        
        settings.certFile = preferences.getString(
            PreferenceKeys.STREAM_CERTIFICATE,
            PreferenceKeys.STREAM_CERTIFICATE_DEFAULT
        )
        
        settings.certPassword = preferences.getString(
            PreferenceKeys.STREAM_CERTIFICATE_PASSWORD,
            PreferenceKeys.STREAM_CERTIFICATE_PASSWORD_DEFAULT
        ) ?: PreferenceKeys.STREAM_CERTIFICATE_PASSWORD_DEFAULT

        // Audio Preferences
        settings.enableAudio = preferences.getBoolean(
            PreferenceKeys.ENABLE_AUDIO,
            PreferenceKeys.ENABLE_AUDIO_DEFAULT
        )
        
        settings.echoCancel = preferences.getBoolean(
            PreferenceKeys.AUDIO_ECHO_CANCEL,
            PreferenceKeys.AUDIO_ECHO_CANCEL_DEFAULT
        )
        
        settings.noiseReduction = preferences.getBoolean(
            PreferenceKeys.AUDIO_NOISE_REDUCTION,
            PreferenceKeys.AUDIO_NOISE_REDUCTION_DEFAULT
        )
        
        settings.sampleRate = preferences
            .getString(
                PreferenceKeys.AUDIO_SAMPLE_RATE,
                PreferenceKeys.AUDIO_SAMPLE_RATE_DEFAULT
            )
            ?.toInt() ?: PreferenceKeys.AUDIO_SAMPLE_RATE_DEFAULT.toInt()
        
        settings.stereo = preferences.getBoolean(
            PreferenceKeys.AUDIO_STEREO,
            PreferenceKeys.AUDIO_STEREO_DEFAULT
        )
        
        settings.audioBitrate = preferences
            .getString(
                PreferenceKeys.AUDIO_BITRATE,
                PreferenceKeys.AUDIO_BITRATE_DEFAULT
            )
            ?.toInt() ?: PreferenceKeys.AUDIO_BITRATE_DEFAULT.toInt()
        
        settings.audioCodec = preferences.getString(
            PreferenceKeys.AUDIO_CODEC,
            PreferenceKeys.AUDIO_CODEC_DEFAULT
        ) ?: PreferenceKeys.AUDIO_CODEC_DEFAULT

        // Video Preferences
        settings.fps = preferences
            .getString(PreferenceKeys.VIDEO_FPS, PreferenceKeys.VIDEO_FPS_DEFAULT)
            ?.toInt() ?: PreferenceKeys.VIDEO_FPS_DEFAULT.toInt()
        
        settings.record = preferences.getBoolean(
            PreferenceKeys.RECORD_VIDEO,
            PreferenceKeys.RECORD_VIDEO_DEFAULT
        )
        
        settings.codec = preferences.getString(
            PreferenceKeys.VIDEO_CODEC,
            PreferenceKeys.VIDEO_CODEC_DEFAULT
        ) ?: PreferenceKeys.VIDEO_CODEC_DEFAULT
        
        settings.bitrate = preferences
            .getString(
                PreferenceKeys.VIDEO_BITRATE,
                PreferenceKeys.VIDEO_BITRATE_DEFAULT
            )
            ?.toInt() ?: PreferenceKeys.VIDEO_BITRATE_DEFAULT.toInt()
        
        settings.adaptiveBitrate = preferences.getBoolean(
            PreferenceKeys.VIDEO_ADAPTIVE_BITRATE,
            PreferenceKeys.VIDEO_ADAPTIVE_BITRATE_DEFAULT
        )

        // Camera Preferences
        settings.videoSource = preferences.getString(
            PreferenceKeys.VIDEO_SOURCE,
            PreferenceKeys.VIDEO_SOURCE_DEFAULT
        ) ?: PreferenceKeys.VIDEO_SOURCE_DEFAULT

        // Resolution
        val resolutionString = preferences.getString(
            PreferenceKeys.VIDEO_RESOLUTION,
            PreferenceKeys.VIDEO_RESOLUTION_DEFAULT
        ) ?: PreferenceKeys.VIDEO_RESOLUTION_DEFAULT

        try {
            val split = resolutionString.split("x")
            if (split.size == 2) {
                val width = split[0].trim().toInt()
                val height = split[1].trim().toInt()
                settings.resolution = Size(width, height)
            } else {
                settings.resolution = Size(1280, 720)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при разборе разрешения: $resolutionString", e)
            settings.resolution = Size(1280, 720)
        }

        // Advanced Video Settings
        settings.keyframeInterval = preferences
            .getString(
                PreferenceKeys.VIDEO_KEYFRAME_INTERVAL,
                PreferenceKeys.VIDEO_KEYFRAME_INTERVAL_DEFAULT
            )
            ?.toInt() ?: PreferenceKeys.VIDEO_KEYFRAME_INTERVAL_DEFAULT.toInt()

        settings.videoProfile = preferences.getString(
            PreferenceKeys.VIDEO_PROFILE,
            PreferenceKeys.VIDEO_PROFILE_DEFAULT
        ) ?: PreferenceKeys.VIDEO_PROFILE_DEFAULT

        settings.videoLevel = preferences.getString(
            PreferenceKeys.VIDEO_LEVEL,
            PreferenceKeys.VIDEO_LEVEL_DEFAULT
        ) ?: PreferenceKeys.VIDEO_LEVEL_DEFAULT

        settings.bitrateMode = preferences.getString(
            PreferenceKeys.VIDEO_BITRATE_MODE,
            PreferenceKeys.VIDEO_BITRATE_MODE_DEFAULT
        ) ?: PreferenceKeys.VIDEO_BITRATE_MODE_DEFAULT

        settings.encodingQuality = preferences.getString(
            PreferenceKeys.VIDEO_QUALITY,
            PreferenceKeys.VIDEO_QUALITY_DEFAULT
        ) ?: PreferenceKeys.VIDEO_QUALITY_DEFAULT

        // Network Settings
        settings.bufferSize = preferences
            .getString(
                PreferenceKeys.NETWORK_BUFFER_SIZE,
                PreferenceKeys.NETWORK_BUFFER_SIZE_DEFAULT
            )
            ?.toInt() ?: PreferenceKeys.NETWORK_BUFFER_SIZE_DEFAULT.toInt()

        settings.connectionTimeout = preferences
            .getString(
                PreferenceKeys.NETWORK_TIMEOUT,
                PreferenceKeys.NETWORK_TIMEOUT_DEFAULT
            )
            ?.toInt() ?: PreferenceKeys.NETWORK_TIMEOUT_DEFAULT.toInt()

        settings.autoReconnect = preferences.getBoolean(
            PreferenceKeys.NETWORK_RECONNECT,
            PreferenceKeys.NETWORK_RECONNECT_DEFAULT
        )

        settings.reconnectDelay = preferences
            .getString(
                PreferenceKeys.NETWORK_RECONNECT_DELAY,
                PreferenceKeys.NETWORK_RECONNECT_DELAY_DEFAULT
            )
            ?.toInt() ?: PreferenceKeys.NETWORK_RECONNECT_DELAY_DEFAULT.toInt()

        settings.maxReconnectAttempts = preferences
            .getString(
                PreferenceKeys.NETWORK_MAX_RECONNECT_ATTEMPTS,
                PreferenceKeys.NETWORK_MAX_RECONNECT_ATTEMPTS_DEFAULT
            )
            ?.toInt() ?: PreferenceKeys.NETWORK_MAX_RECONNECT_ATTEMPTS_DEFAULT.toInt()

        // Stability Settings
        settings.lowLatencyMode = preferences.getBoolean(
            PreferenceKeys.STABILITY_LOW_LATENCY,
            PreferenceKeys.STABILITY_LOW_LATENCY_DEFAULT
        )

        settings.hardwareRotation = preferences.getBoolean(
            PreferenceKeys.STABILITY_HARDWARE_ROTATION,
            PreferenceKeys.STABILITY_HARDWARE_ROTATION_DEFAULT
        )

        settings.dynamicFps = preferences.getBoolean(
            PreferenceKeys.STABILITY_DYNAMIC_FPS,
            PreferenceKeys.STABILITY_DYNAMIC_FPS_DEFAULT
        )
        
        return settings
    }
} 