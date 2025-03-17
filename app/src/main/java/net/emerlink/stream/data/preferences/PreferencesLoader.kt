package net.emerlink.stream.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import net.emerlink.stream.data.model.ConnectionSettings
import net.emerlink.stream.data.model.StreamSettings
import net.emerlink.stream.data.model.StreamType
import net.emerlink.stream.data.repository.ConnectionProfileRepository
import net.emerlink.stream.data.repository.SettingsRepository
import org.koin.java.KoinJavaComponent.inject

/**
 * Class for loading settings from SharedPreferences
 */
class PreferencesLoader(
    context: Context,
) {
    companion object {
        private const val TAG = "PreferencesLoader"
    }

    private val profileRepository = ConnectionProfileRepository(context)
    private val settingsRepository: SettingsRepository by inject(SettingsRepository::class.java)

    /**
     * Loads all settings from SharedPreferences and returns a settings object
     */
    fun loadPreferences(preferences: SharedPreferences): StreamSettings {
        Log.d(TAG, "Loading preferences")

        // Try to get connection settings from active profile first
        val activeProfile = profileRepository.getActiveProfile()
        val connectionSettings = activeProfile?.settings ?: loadConnectionSettings(preferences)

        // Get video and audio settings from repository
        val videoSettings = settingsRepository.videoSettingsFlow.value
        val audioSettings = settingsRepository.audioSettingsFlow.value

        return StreamSettings(
            // Connection settings
            connection = connectionSettings,
            // Audio settings
            audioSampleRate = audioSettings.sampleRate,
            audioStereo = audioSettings.stereo,
            audioEchoCancel = audioSettings.echoCancel,
            audioNoiseReduction = audioSettings.noiseReduction,
            enableAudio = audioSettings.enabled,
            audioBitrate = audioSettings.bitrate,
            audioCodec = audioSettings.codec,
            // Video settings
            fps = videoSettings.fps,
            resolution = videoSettings.resolution,
            adaptiveBitrate = videoSettings.adaptiveBitrate,
            record = videoSettings.recordVideo,
            stream = videoSettings.streamVideo,
            bitrate = videoSettings.bitrate,
            codec = videoSettings.codec,
            uid = preferences.getString(PreferenceKeys.UID, PreferenceKeys.UID_DEFAULT) ?: PreferenceKeys.UID_DEFAULT,
            iFrameInterval = videoSettings.keyframeInterval
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
}
