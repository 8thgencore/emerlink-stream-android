package net.emerlink.stream.presentation.ui.settings.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.emerlink.stream.data.model.AudioSettings
import net.emerlink.stream.data.model.VideoSettings
import net.emerlink.stream.data.repository.SettingsRepository
import org.koin.java.KoinJavaComponent.inject

/**
 * ViewModel for managing application settings
 */
class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository: SettingsRepository by inject(SettingsRepository::class.java)

    // Video settings flow
    val videoSettings: StateFlow<VideoSettings> =
        repository.videoSettingsFlow
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                repository.videoSettingsFlow.value
            )

    // Audio settings flow
    val audioSettings: StateFlow<AudioSettings> =
        repository.audioSettingsFlow
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                repository.audioSettingsFlow.value
            )

    // Available camera resolutions
    private val _availableResolutions = MutableStateFlow<List<String>>(emptyList())
    val availableResolutions: StateFlow<List<String>> = _availableResolutions.asStateFlow()

    init {
        loadAvailableCameraResolutions()
    }

    /**
     * Load available camera resolutions
     */
    private fun loadAvailableCameraResolutions() {
        viewModelScope.launch {
            val resolutions = getAvailableCameraResolutions(getApplication())
            _availableResolutions.value = resolutions
        }
    }

    /**
     * Get available camera resolutions from the device
     */
    private fun getAvailableCameraResolutions(context: Context): List<String> {
        val resolutions = mutableListOf<String>()

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId =
                cameraManager.cameraIdList.firstOrNull { id ->
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    facing == CameraCharacteristics.LENS_FACING_BACK
                }

            cameraId?.let { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

                configs?.let { config ->
                    val sizes = config.getOutputSizes(android.graphics.ImageFormat.JPEG)
                    sizes?.let {
                        // Sort resolutions by area (width * height) in descending order
                        val sortedSizes = sizes.sortedByDescending { it.width * it.height }

                        // Add resolutions to the list
                        sortedSizes.forEach { size ->
                            resolutions.add("${size.width}x${size.height}")
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to default resolutions if there's an error
        }

        // If no resolutions were found, use default ones
        if (resolutions.isEmpty()) {
            resolutions.addAll(listOf("1920x1080", "1280x720", "854x480", "640x360"))
        }

        return resolutions
    }

    // Video settings methods

    fun updateVideoResolution(resolution: String) {
        repository.updateVideoResolution(resolution)
    }

    fun updateScreenOrientation(orientation: String) {
        repository.updateScreenOrientation(orientation)
    }

    fun updateVideoFps(fps: String) {
        repository.updateVideoFps(fps)
    }

    fun updateVideoBitrate(bitrate: String) {
        repository.updateVideoBitrate(bitrate)
    }

    fun updateVideoCodec(codec: String) {
        repository.updateVideoCodec(codec)
    }

    fun updateAdaptiveBitrate(enabled: Boolean) {
        repository.updateAdaptiveBitrate(enabled)
    }

    fun updateRecordVideo(enabled: Boolean) {
        repository.updateRecordVideo(enabled)
    }

    fun updateStreamVideo(enabled: Boolean) {
        repository.updateStreamVideo(enabled)
    }

    fun updateKeyframeInterval(interval: String) {
        repository.updateKeyframeInterval(interval)
    }

    // Audio settings methods

    fun updateEnableAudio(enabled: Boolean) {
        repository.updateEnableAudio(enabled)
    }

    fun updateAudioBitrate(bitrate: String) {
        repository.updateAudioBitrate(bitrate)
    }

    fun updateAudioSampleRate(sampleRate: String) {
        repository.updateAudioSampleRate(sampleRate)
    }

    fun updateAudioStereo(enabled: Boolean) {
        repository.updateAudioStereo(enabled)
    }

    fun updateAudioEchoCancel(enabled: Boolean) {
        repository.updateAudioEchoCancel(enabled)
    }

    fun updateAudioNoiseReduction(enabled: Boolean) {
        repository.updateAudioNoiseReduction(enabled)
    }

    fun updateAudioCodec(codec: String) {
        repository.updateAudioCodec(codec)
    }
}
