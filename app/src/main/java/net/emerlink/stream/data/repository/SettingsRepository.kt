package net.emerlink.stream.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Size
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.emerlink.stream.data.model.AudioSettings
import net.emerlink.stream.data.model.VideoSettings
import net.emerlink.stream.data.preferences.PreferenceKeys

/**
 * Repository for managing application settings
 */
class SettingsRepository(
    context: Context,
) {
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    // Video settings flow
    private val _videoSettingsFlow = MutableStateFlow(loadVideoSettings())
    val videoSettingsFlow: StateFlow<VideoSettings> = _videoSettingsFlow.asStateFlow()

    // Audio settings flow
    private val _audioSettingsFlow = MutableStateFlow(loadAudioSettings())
    val audioSettingsFlow: StateFlow<AudioSettings> = _audioSettingsFlow.asStateFlow()

    /**
     * Load video settings from SharedPreferences
     */
    private fun loadVideoSettings(): VideoSettings =
        VideoSettings(
            resolution = getResolutionFromPreferences(),
            fps =
                preferences
                    .getString(PreferenceKeys.VIDEO_FPS, PreferenceKeys.VIDEO_FPS_DEFAULT)
                    ?.toIntOrNull() ?: 30,
            bitrate =
                preferences
                    .getString(PreferenceKeys.VIDEO_BITRATE, PreferenceKeys.VIDEO_BITRATE_DEFAULT)
                    ?.toIntOrNull() ?: 2500,
            codec =
                preferences.getString(PreferenceKeys.VIDEO_CODEC, PreferenceKeys.VIDEO_CODEC_DEFAULT)
                    ?: PreferenceKeys.VIDEO_CODEC_DEFAULT,
            adaptiveBitrate =
                preferences.getBoolean(
                    PreferenceKeys.VIDEO_ADAPTIVE_BITRATE,
                    PreferenceKeys.VIDEO_ADAPTIVE_BITRATE_DEFAULT
                ),
            recordVideo =
                preferences.getBoolean(
                    PreferenceKeys.RECORD_VIDEO,
                    PreferenceKeys.RECORD_VIDEO_DEFAULT
                ),
            streamVideo =
                preferences.getBoolean(
                    PreferenceKeys.STREAM_VIDEO,
                    PreferenceKeys.STREAM_VIDEO_DEFAULT
                ),
            screenOrientation =
                preferences.getString(
                    PreferenceKeys.SCREEN_ORIENTATION,
                    PreferenceKeys.SCREEN_ORIENTATION_DEFAULT
                ) ?: PreferenceKeys.SCREEN_ORIENTATION_DEFAULT,
            keyframeInterval =
                preferences
                    .getString(
                        PreferenceKeys.VIDEO_KEYFRAME_INTERVAL,
                        PreferenceKeys.VIDEO_KEYFRAME_INTERVAL_DEFAULT
                    )?.toIntOrNull() ?: 2,
            videoSource =
                preferences.getString(
                    PreferenceKeys.VIDEO_SOURCE,
                    PreferenceKeys.VIDEO_SOURCE_DEFAULT
                ) ?: PreferenceKeys.VIDEO_SOURCE_DEFAULT
        )

    /**
     * Load audio settings from SharedPreferences
     */
    private fun loadAudioSettings(): AudioSettings =
        AudioSettings(
            enabled =
                preferences.getBoolean(
                    PreferenceKeys.ENABLE_AUDIO,
                    PreferenceKeys.ENABLE_AUDIO_DEFAULT
                ),
            bitrate =
                preferences
                    .getString(
                        PreferenceKeys.AUDIO_BITRATE,
                        PreferenceKeys.AUDIO_BITRATE_DEFAULT
                    )?.toIntOrNull() ?: 128,
            sampleRate =
                preferences
                    .getString(
                        PreferenceKeys.AUDIO_SAMPLE_RATE,
                        PreferenceKeys.AUDIO_SAMPLE_RATE_DEFAULT
                    )?.toIntOrNull() ?: 44100,
            stereo =
                preferences.getBoolean(
                    PreferenceKeys.AUDIO_STEREO,
                    PreferenceKeys.AUDIO_STEREO_DEFAULT
                ),
            echoCancel =
                preferences.getBoolean(
                    PreferenceKeys.AUDIO_ECHO_CANCEL,
                    PreferenceKeys.AUDIO_ECHO_CANCEL_DEFAULT
                ),
            noiseReduction =
                preferences.getBoolean(
                    PreferenceKeys.AUDIO_NOISE_REDUCTION,
                    PreferenceKeys.AUDIO_NOISE_REDUCTION_DEFAULT
                ),
            codec =
                preferences.getString(
                    PreferenceKeys.AUDIO_CODEC,
                    PreferenceKeys.AUDIO_CODEC_DEFAULT
                ) ?: PreferenceKeys.AUDIO_CODEC_DEFAULT
        )

    /**
     * Get resolution from SharedPreferences
     */
    private fun getResolutionFromPreferences(): Size {
        val resolutionString =
            preferences.getString(
                PreferenceKeys.VIDEO_RESOLUTION,
                PreferenceKeys.VIDEO_RESOLUTION_DEFAULT
            ) ?: PreferenceKeys.VIDEO_RESOLUTION_DEFAULT

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

    /**
     * Update video resolution
     */
    fun updateVideoResolution(resolution: String) {
        preferences.edit {
            putString(PreferenceKeys.VIDEO_RESOLUTION, resolution)
        }
        _videoSettingsFlow.value = loadVideoSettings()
    }

    /**
     * Update screen orientation
     */
    fun updateScreenOrientation(orientation: String) {
        preferences.edit {
            putString(PreferenceKeys.SCREEN_ORIENTATION, orientation)
        }
        _videoSettingsFlow.value = loadVideoSettings()
    }

    /**
     * Update video FPS
     */
    fun updateVideoFps(fps: String) {
        preferences.edit {
            putString(PreferenceKeys.VIDEO_FPS, fps)
        }
        _videoSettingsFlow.value = loadVideoSettings()
    }

    /**
     * Update video bitrate
     */
    fun updateVideoBitrate(bitrate: String) {
        preferences.edit {
            putString(PreferenceKeys.VIDEO_BITRATE, bitrate)
        }
        _videoSettingsFlow.value = loadVideoSettings()
    }

    /**
     * Update video codec
     */
    fun updateVideoCodec(codec: String) {
        preferences.edit {
            putString(PreferenceKeys.VIDEO_CODEC, codec)
        }
        _videoSettingsFlow.value = loadVideoSettings()
    }

    /**
     * Update adaptive bitrate setting
     */
    fun updateAdaptiveBitrate(enabled: Boolean) {
        preferences.edit {
            putBoolean(PreferenceKeys.VIDEO_ADAPTIVE_BITRATE, enabled)
        }
        _videoSettingsFlow.value = loadVideoSettings()
    }

    /**
     * Update record video setting
     */
    fun updateRecordVideo(enabled: Boolean) {
        preferences.edit {
            putBoolean(PreferenceKeys.RECORD_VIDEO, enabled)
        }
        _videoSettingsFlow.value = loadVideoSettings()
    }

    /**
     * Update stream video setting
     */
    fun updateStreamVideo(enabled: Boolean) {
        preferences.edit {
            putBoolean(PreferenceKeys.STREAM_VIDEO, enabled)
        }
        _videoSettingsFlow.value = loadVideoSettings()
    }

    /**
     * Update keyframe interval
     */
    fun updateKeyframeInterval(interval: String) {
        preferences.edit {
            putString(PreferenceKeys.VIDEO_KEYFRAME_INTERVAL, interval)
        }
        _videoSettingsFlow.value = loadVideoSettings()
    }

    /**
     * Update enable audio setting
     */
    fun updateEnableAudio(enabled: Boolean) {
        preferences.edit {
            putBoolean(PreferenceKeys.ENABLE_AUDIO, enabled)
        }
        _audioSettingsFlow.value = loadAudioSettings()
    }

    /**
     * Update audio bitrate
     */
    fun updateAudioBitrate(bitrate: String) {
        preferences.edit {
            putString(PreferenceKeys.AUDIO_BITRATE, bitrate)
        }
        _audioSettingsFlow.value = loadAudioSettings()
    }

    /**
     * Update audio sample rate
     */
    fun updateAudioSampleRate(sampleRate: String) {
        preferences.edit {
            putString(PreferenceKeys.AUDIO_SAMPLE_RATE, sampleRate)
        }
        _audioSettingsFlow.value = loadAudioSettings()
    }

    /**
     * Update audio stereo setting
     */
    fun updateAudioStereo(enabled: Boolean) {
        preferences.edit {
            putBoolean(PreferenceKeys.AUDIO_STEREO, enabled)
        }
        _audioSettingsFlow.value = loadAudioSettings()
    }

    /**
     * Update audio echo cancellation setting
     */
    fun updateAudioEchoCancel(enabled: Boolean) {
        preferences.edit {
            putBoolean(PreferenceKeys.AUDIO_ECHO_CANCEL, enabled)
        }
        _audioSettingsFlow.value = loadAudioSettings()
    }

    /**
     * Update audio noise reduction setting
     */
    fun updateAudioNoiseReduction(enabled: Boolean) {
        preferences.edit {
            putBoolean(PreferenceKeys.AUDIO_NOISE_REDUCTION, enabled)
        }
        _audioSettingsFlow.value = loadAudioSettings()
    }

    /**
     * Update audio codec
     */
    fun updateAudioCodec(codec: String) {
        preferences.edit {
            putString(PreferenceKeys.AUDIO_CODEC, codec)
        }
        _audioSettingsFlow.value = loadAudioSettings()
    }
}
