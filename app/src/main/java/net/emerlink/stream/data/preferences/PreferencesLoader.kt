package net.emerlink.stream.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.Size
import net.emerlink.stream.data.repository.ConnectionProfileRepository
import net.emerlink.stream.model.ConnectionSettings
import net.emerlink.stream.model.StreamSettings
import net.emerlink.stream.model.StreamType

/**
 * Class for loading settings from SharedPreferences
 */
class PreferencesLoader(
    private val context: Context,
) {
    companion object {
        private const val TAG = "PreferencesLoader"
    }

    private val profileRepository = ConnectionProfileRepository(context)

    /**
     * Loads all settings from SharedPreferences and returns a settings object
     */
    fun loadPreferences(preferences: SharedPreferences): StreamSettings {
        Log.d(TAG, "Loading preferences")

        // Try to get connection settings from active profile first
        val activeProfile = profileRepository.getActiveProfile()
        val connectionSettings = activeProfile?.settings ?: loadConnectionSettings(preferences)

        return StreamSettings(
            // Connection settings
            connection = connectionSettings,
            // Audio settings
            audioSampleRate =
                preferences
                    .getString(
                        PreferenceKeys.AUDIO_SAMPLE_RATE,
                        PreferenceKeys.AUDIO_SAMPLE_RATE_DEFAULT
                    )?.toIntOrNull() ?: 44100,
            audioStereo = preferences.getBoolean(PreferenceKeys.AUDIO_STEREO, PreferenceKeys.AUDIO_STEREO_DEFAULT),
            audioEchoCancel =
                preferences.getBoolean(
                    PreferenceKeys.AUDIO_ECHO_CANCEL,
                    PreferenceKeys.AUDIO_ECHO_CANCEL_DEFAULT
                ),
            audioNoiseReduction =
                preferences.getBoolean(
                    PreferenceKeys.AUDIO_NOISE_REDUCTION,
                    PreferenceKeys.AUDIO_NOISE_REDUCTION_DEFAULT
                ),
            enableAudio = preferences.getBoolean(PreferenceKeys.ENABLE_AUDIO, PreferenceKeys.ENABLE_AUDIO_DEFAULT),
            audioBitrate =
                preferences
                    .getString(PreferenceKeys.AUDIO_BITRATE, PreferenceKeys.AUDIO_BITRATE_DEFAULT)
                    ?.toIntOrNull() ?: 128,
            audioCodec =
                preferences.getString(PreferenceKeys.AUDIO_CODEC, PreferenceKeys.AUDIO_CODEC_DEFAULT)
                    ?: PreferenceKeys.AUDIO_CODEC_DEFAULT,
            // Video settings
            fps =
                preferences.getString(PreferenceKeys.VIDEO_FPS, PreferenceKeys.VIDEO_FPS_DEFAULT)?.toIntOrNull()
                    ?: 30,
            resolution = getResolutionFromPreferences(preferences),
            adaptiveBitrate =
                preferences.getBoolean(
                    PreferenceKeys.VIDEO_ADAPTIVE_BITRATE,
                    PreferenceKeys.VIDEO_ADAPTIVE_BITRATE_DEFAULT
                ),
            record = preferences.getBoolean(PreferenceKeys.RECORD_VIDEO, PreferenceKeys.RECORD_VIDEO_DEFAULT),
            stream = preferences.getBoolean(PreferenceKeys.STREAM_VIDEO, PreferenceKeys.STREAM_VIDEO_DEFAULT),
            bitrate =
                preferences
                    .getString(PreferenceKeys.VIDEO_BITRATE, PreferenceKeys.VIDEO_BITRATE_DEFAULT)
                    ?.toIntOrNull() ?: 2500,
            codec =
                preferences.getString(PreferenceKeys.VIDEO_CODEC, PreferenceKeys.VIDEO_CODEC_DEFAULT)
                    ?: PreferenceKeys.VIDEO_CODEC_DEFAULT,
            uid = preferences.getString(PreferenceKeys.UID, PreferenceKeys.UID_DEFAULT) ?: PreferenceKeys.UID_DEFAULT,
            // Camera
            videoSource =
                preferences.getString(PreferenceKeys.VIDEO_SOURCE, PreferenceKeys.VIDEO_SOURCE_DEFAULT)
                    ?: PreferenceKeys.VIDEO_SOURCE_DEFAULT,
            // Advanced Video Settings
            iFrameInterval =
                preferences
                    .getString(
                        PreferenceKeys.VIDEO_KEYFRAME_INTERVAL,
                        PreferenceKeys.VIDEO_KEYFRAME_INTERVAL_DEFAULT
                    )?.toIntOrNull() ?: 2
        )
    }

    /**
     * Loads connection settings from SharedPreferences
     */
    private fun loadConnectionSettings(preferences: SharedPreferences): ConnectionSettings {
        // Get port string from preferences and convert to Int
        val portString =
            preferences.getString(PreferenceKeys.STREAM_PORT, PreferenceKeys.STREAM_PORT_DEFAULT)
                ?: PreferenceKeys.STREAM_PORT_DEFAULT
        val portInt = portString.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0

        return ConnectionSettings(
            protocol =
                StreamType.fromString(
                    preferences.getString(PreferenceKeys.STREAM_PROTOCOL, PreferenceKeys.STREAM_PROTOCOL_DEFAULT)
                        ?: PreferenceKeys.STREAM_PROTOCOL_DEFAULT
                ),
            address =
                preferences.getString(PreferenceKeys.STREAM_ADDRESS, PreferenceKeys.STREAM_ADDRESS_DEFAULT)
                    ?: PreferenceKeys.STREAM_ADDRESS_DEFAULT,
            port = portInt,
            path =
                preferences.getString(PreferenceKeys.STREAM_PATH, PreferenceKeys.STREAM_PATH_DEFAULT)
                    ?: PreferenceKeys.STREAM_PATH_DEFAULT,
            streamKey =
                preferences.getString(PreferenceKeys.STREAM_KEY, PreferenceKeys.STREAM_KEY_DEFAULT)
                    ?: PreferenceKeys.STREAM_KEY_DEFAULT,
            tcp = preferences.getBoolean(PreferenceKeys.STREAM_USE_TCP, PreferenceKeys.STREAM_USE_TCP_DEFAULT),
            username =
                preferences.getString(PreferenceKeys.STREAM_USERNAME, PreferenceKeys.STREAM_USERNAME_DEFAULT)
                    ?: PreferenceKeys.STREAM_USERNAME_DEFAULT,
            password =
                preferences.getString(PreferenceKeys.STREAM_PASSWORD, PreferenceKeys.STREAM_PASSWORD_DEFAULT)
                    ?: PreferenceKeys.STREAM_PASSWORD_DEFAULT,
            streamSelfSignedCert =
                preferences.getBoolean(
                    PreferenceKeys.STREAM_SELF_SIGNED_CERT,
                    PreferenceKeys.STREAM_SELF_SIGNED_CERT_DEFAULT
                ),
            certFile =
                preferences.getString(
                    PreferenceKeys.STREAM_CERTIFICATE,
                    PreferenceKeys.STREAM_CERTIFICATE_DEFAULT
                ),
            certPassword =
                preferences.getString(
                    PreferenceKeys.STREAM_CERTIFICATE_PASSWORD,
                    PreferenceKeys.STREAM_CERTIFICATE_PASSWORD_DEFAULT
                ) ?: PreferenceKeys.STREAM_CERTIFICATE_PASSWORD_DEFAULT
        )
    }

    /**
     * Loads resolution from SharedPreferences
     */
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
