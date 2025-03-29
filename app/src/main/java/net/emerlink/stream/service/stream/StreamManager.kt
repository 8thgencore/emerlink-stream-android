package net.emerlink.stream.service.stream

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.GlInterface
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.core.ErrorHandler
import net.emerlink.stream.data.model.Resolution
import net.emerlink.stream.data.model.StreamType
import net.emerlink.stream.data.repository.SettingsRepository
import net.emerlink.stream.service.camera.CameraInterface

/**
 * Manages streaming video and camera preview functionality
 */
class StreamManager(
    private val context: Context,
    private val connectChecker: ConnectChecker,
    private val errorHandler: ErrorHandler,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        private const val TAG = "StreamManager"
        private const val DELAY_RESTART_PREVIEW_MS = 100L
        private const val DELAY_RESTART_ENCODER_MS = 300L
        private const val FALLBACK_AUDIO_BITRATE = 128 * 1024
        private const val FALLBACK_AUDIO_SAMPLE_RATE = 44100
    }

    private var currentView: OpenGlView? = null
    private var cameraInterface: CameraInterface
    private var streamType: StreamType = StreamType.RTMP
    private val cameraIds = ArrayList<String>()
    private var currentCameraId = 0
    private var lanternEnabled = false

    init {
        cameraInterface = CameraInterface.create(context, connectChecker, streamType)
        getCameraIds()
    }

    /**
     * Gets available camera IDs from the Camera2Source
     */
    private fun getCameraIds() {
        withCamera2Source({ camera2Source ->
            cameraIds.clear()
            cameraIds.addAll(camera2Source.camerasAvailable().toList())
            Log.d(TAG, "Got cameraIds $cameraIds")
        }, Unit)
    }

    /**
     * Executes an action with Camera2Source if it's available
     */
    private fun <T> withCamera2Source(
        action: (Camera2Source) -> T,
        defaultValue: T,
    ): T {
        val videoSource = getVideoSource()
        if (videoSource is Camera2Source) {
            return action(videoSource)
        }
        return defaultValue
    }

    /**
     * Handles resolution change by restarting preview with new settings
     */
    fun handleResolutionChange() {
        currentView?.let { view ->
            Log.d(TAG, "Перезапуск превью с новым разрешением")
            try {
                switchStreamResolution()
                restartPreview(view)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при обновлении разрешения: ${e.message}", e)
            }
        }
    }

    /**
     * Public method to restart preview, delegates to the appropriate logic
     */
    fun restartPreview(view: OpenGlView) {
        Log.d(TAG, "Public restartPreview called")
        
        // Проверка валидности поверхности
        if (view.holder.surface?.isValid != true) {
            Log.w(TAG, "Surface not valid in public restartPreview")
            
            // Сохраняем ссылку на view для будущего использования
            currentView = view
            
            // Если стримим, не пытаемся перезапустить превью, просто вернемся
            if (isStreaming()) {
                Log.d(TAG, "Streaming active, will recover preview when surface becomes valid")
                return
            }
        }
        
        // Если поверхность валидна или мы не стримим, пробуем перезапустить превью
        try {
            restartPreviewInternal(view)
        } catch (e: Exception) {
            Log.e(TAG, "Error in public restartPreview: ${e.message}", e)
            // Если стримим, глотаем исключение
            if (!isStreaming()) {
                throw e
            }
        }
    }

    /**
     * Internal method to restart preview implementation
     */
    private fun restartPreviewInternal(view: OpenGlView) {
        try {
            Log.d(
                TAG,
                "Restarting preview internal. Status: streaming=${isStreaming()}, recording=${isRecording()}, onPreview=${isOnPreview()}"
            )

            // Save current streaming state to know if we need to resume it
            val wasStreaming = isStreaming()

            // Only stop preview, NOT streaming
            if (isOnPreview()) {
                // Don't stop the preview if we're streaming in background and surface is invalid
                if (!wasStreaming || view.holder.surface?.isValid == true) {
                    stopPreview()
                } else {
                    Log.d(TAG, "Skipping stopPreview because streaming is active and surface is invalid")
                    // Just update the reference without stopping
                    currentView = view
                    return
                }
            }

            // Small delay to ensure previous resources are released
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    Log.d(TAG, "Starting new preview during restartPreview")

                    // Replace view only if the surface is valid
                    if (view.holder.surface?.isValid == true) {
                        cameraInterface.replaceView(view)

                        // If stream is not active, we can safely reconfigure video
                        if (!wasStreaming) {
                            prepareVideoWithCurrentSettings()
                        }

                        // Start preview
                        startPreviewInternal(view)
                        
                        Log.d(TAG, "Preview successfully restarted")
                    } else {
                        Log.w(TAG, "Surface not valid, skipping preview restart")
                        // Just keep streaming in background if active
                        currentView = view
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting preview during restartPreview: ${e.message}", e)

                    // If we're not streaming, try one more time with a clean camera interface
                    if (!wasStreaming) {
                        try {
                            // Recovery code
                            Log.d(TAG, "Trying final recovery attempt with new camera interface")
                            cameraInterface = CameraInterface.create(context, connectChecker, streamType)
                            
                            if (view.holder.surface?.isValid == true) {
                                cameraInterface.replaceView(view)
                                prepareVideoWithCurrentSettings()

                                val rotation = CameraHelper.getCameraOrientation(context)
                                cameraInterface.startPreview(CameraHelper.Facing.BACK, rotation)
                                currentView = view
                                Log.d(TAG, "Final recovery successful")
                            } else {
                                Log.w(TAG, "Surface still not valid during recovery")
                                currentView = view
                            }
                        } catch (finalEx: Exception) {
                            Log.e(TAG, "Final recovery attempt failed: ${finalEx.message}", finalEx)
                            throw finalEx
                        }
                    } else {
                        Log.w(TAG, "Keeping stream active in background despite preview error")
                        currentView = view
                        // Don't throw the exception if we're streaming - just continue in background
                    }
                }
            }, DELAY_RESTART_PREVIEW_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error restarting preview: ${e.message}", e)
            currentView = view
            if (!isStreaming()) {
                throw e
            } else {
                Log.w(TAG, "Keeping stream active despite preview error")
            }
        }
    }

    /**
     * Set stream protocol type
     * @param type The stream protocol type
     */
    fun setStreamType(type: StreamType) {
        if (type != streamType) {
            streamType = type
            cameraInterface = CameraInterface.create(context, connectChecker, streamType)
        }
    }

    /**
     * Start streaming video to the specified URL
     */
    fun startStream(
        url: String,
        protocol: String,
        username: String,
        password: String,
        tcp: Boolean,
    ) {
        try {
            Log.d(TAG, "Starting stream with protocol: $protocol, tcp: $tcp")

            if (protocol.startsWith("rtsp")) {
                cameraInterface.setProtocol(tcp)
            }

            if (username.isNotEmpty() &&
                password.isNotEmpty() &&
                (protocol.startsWith("rtmp") || protocol.startsWith("rtsp"))
            ) {
                cameraInterface.setAuthorization(username, password)
            }

            // Важно: подготавливаем видео перед запуском стрима, независимо от состояния превью
            prepareVideoWithCurrentSettings()
            
            cameraInterface.startStream(url)
            Log.d(TAG, "Stream started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting stream: ${e.message}", e)
            errorHandler.handleStreamError(e)
        }
    }

    /**
     * Stop the current stream
     */
    fun stopStream() {
        try {
            if (isStreaming()) {
                cameraInterface.stopStream()
            }
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
        }
    }

    /**
     * Start recording video to the specified file path
     */
    fun startRecord(filePath: String) {
        try {
            cameraInterface.startRecord(filePath)
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
        }
    }

    /**
     * Stop recording video
     */
    fun stopRecord() {
        try {
            if (isRecording()) {
                cameraInterface.stopRecord()
            }
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
        }
    }

    /**
     * Start the camera preview on the specified view
     */
    fun startPreview(view: OpenGlView) {
        try {
            // Проверка валидности поверхности
            if (view.holder.surface?.isValid != true) {
                Log.w(TAG, "Surface not valid in startPreview, only storing reference")
                currentView = view
                return
            }
            
            currentView = view

            // Сохраняем текущий статус стрима
            val wasStreaming = isStreaming()

            // Если превью уже активно, останавливаем его, но не трогаем стрим
            if (isOnPreview()) {
                cameraInterface.stopPreview()
            }

            // Заменяем View
            cameraInterface.replaceView(view)

            // Если стрим не активен, то можно спокойно обновлять параметры видео
            if (!wasStreaming) {
                val videoSettings = settingsRepository.videoSettingsFlow.value
                val bitrate = cameraInterface.bitrate.takeIf { it > 0 } ?: (videoSettings.bitrate * 1000)
                val resolution = Resolution.parseFromSize(videoSettings.resolution)
                prepareVideoWithParams(resolution, videoSettings.fps, videoSettings.keyframeInterval, bitrate)
            }

            startPreviewInternal(view)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при запуске preview: ${e.message}")
            currentView = view
            errorHandler.handleStreamError(e)
        }
    }

    /**
     * Internal helper method to start preview
     */
    private fun startPreviewInternal(view: OpenGlView) {
        try {
            val rotation = CameraHelper.getCameraOrientation(context)

            // Use a try-catch block to handle potential camera errors
            try {
                cameraInterface.startPreview(CameraHelper.Facing.BACK, rotation)
                currentView = view

                val videoSettings = settingsRepository.videoSettingsFlow.value
                val resolution = Resolution.parseFromSize(videoSettings.resolution)
                Log.d(
                    TAG,
                    "Preview successfully started ${resolution.width}x${resolution.height}, rotation=$rotation, streaming=${isStreaming()}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error starting camera preview: ${e.message}")

                // Try to recover by recreating camera interface
                if (!isStreaming()) {
                    Log.d(TAG, "Attempting to recover from preview start failure...")
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            cameraInterface = CameraInterface.create(context, connectChecker, streamType)
                            cameraInterface.replaceView(view)
                            prepareVideoWithCurrentSettings()
                            cameraInterface.startPreview(CameraHelper.Facing.BACK, rotation)
                            currentView = view
                            Log.d(TAG, "Successfully recovered from preview start failure")
                        } catch (recoverEx: Exception) {
                            Log.e(TAG, "Failed to recover from preview start failure: ${recoverEx.message}", recoverEx)
                            throw recoverEx
                        }
                    }, DELAY_RESTART_PREVIEW_MS)
                } else {
                    throw e
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error starting preview: ${e.message}", e)
            throw e
        }
    }

    /**
     * Stop the camera preview without disrupting streaming
     */
    fun stopPreview() {
        try {
            if (isOnPreview() && !isStreaming()) {
                Log.d(TAG, "Stopping Preview")
                cameraInterface.stopPreview()
            } else if (isStreaming()) {
                Log.d(TAG, "Not stopping preview because streaming is active - would cause freeze")
            }
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
        }
    }

    /**
     * Prepare audio for streaming with the current settings
     * @return true if successful, false otherwise
     */
    fun prepareAudio(): Boolean {
        Log.d(TAG, "Подготовка аудио")

        // Получаем актуальные настройки аудио из репозитория
        val audioSettings = settingsRepository.audioSettingsFlow.value

        // Try with current settings first
        try {
            cameraInterface.prepareAudio(
                bitrate = audioSettings.bitrate * 1024,
                sampleRate = audioSettings.sampleRate,
                isStereo = audioSettings.stereo
            )
            cameraInterface.setAudioCodec(AudioCodec.AAC)
            return true
        } catch (_: Exception) {
            // If that fails, try with fallback settings
            return tryFallbackAudioSettings()
        }
    }

    /**
     * Try fallback audio settings when main settings fail
     */
    private fun tryFallbackAudioSettings(): Boolean {
        try {
            cameraInterface.prepareAudio(
                bitrate = FALLBACK_AUDIO_BITRATE,
                sampleRate = FALLBACK_AUDIO_SAMPLE_RATE,
                isStereo = false
            )
            cameraInterface.setAudioCodec(AudioCodec.AAC)
            return true
        } catch (e2: Exception) {
            Log.e(TAG, "Не удалось инициализировать аудио: ${e2.message}", e2)
            return false
        }
    }

    /**
     * Prepare video for streaming with the current settings
     * @return true if successful, false otherwise
     */
    fun prepareVideo(): Boolean {
        try {
            // Принципиально важно всегда подготавливать видео перед стримингом
            prepareVideoWithCurrentSettings()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка подготовки видео: ${e.message}", e)
            return false
        }
    }

    /**
     * Helper method to prepare video with current settings
     */
    private fun prepareVideoWithCurrentSettings() {
        val videoSettings = settingsRepository.videoSettingsFlow.value
        val resolution = Resolution.parseFromSize(videoSettings.resolution)
        prepareVideoWithParams(
            resolution,
            videoSettings.fps,
            videoSettings.keyframeInterval,
            videoSettings.bitrate * 1000
        )
    }

    /**
     * Helper method to prepare video with specific parameters
     */
    private fun prepareVideoWithParams(
        resolution: Resolution,
        fps: Int,
        iFrameInterval: Int,
        bitrate: Int,
    ) {
        val rotation = CameraHelper.getCameraOrientation(context)
        cameraInterface.prepareVideo(
            width = resolution.width,
            height = resolution.height,
            fps = fps,
            bitrate = bitrate,
            iFrameInterval = iFrameInterval,
            rotation = rotation
        )
    }

    /**
     * Check if streaming is active
     */
    fun isStreaming(): Boolean = cameraInterface.isStreaming

    /**
     * Check if recording is active
     */
    fun isRecording(): Boolean = cameraInterface.isRecording

    /**
     * Check if preview is active
     */
    fun isOnPreview(): Boolean = cameraInterface.isOnPreview

    /**
     * Get the video source
     */
    private fun getVideoSource(): Any = cameraInterface

    /**
     * Get the GL interface for rendering
     */
    fun getGlInterface(): GlInterface = cameraInterface.glInterface

    /**
     * Set video bitrate on the fly
     */
    fun setVideoBitrateOnFly(bitrate: Int) = cameraInterface.setVideoBitrateOnFly(bitrate)

    /**
     * Check if stream has congestion
     */
    fun hasCongestion(): Boolean = cameraInterface.hasCongestion()

    /**
     * Switch stream resolution during streaming
     */
    private fun switchStreamResolution() {
        try {
            val videoSettings = settingsRepository.videoSettingsFlow.value
            val resolution = Resolution.parseFromSize(videoSettings.resolution)

            if (isOnPreview()) {
                val rotation = CameraHelper.getCameraOrientation(context)

                cameraInterface.stopPreview()

                prepareVideoWithParams(
                    resolution,
                    videoSettings.fps,
                    videoSettings.keyframeInterval,
                    videoSettings.bitrate * 1000
                )

                currentView?.let { view ->
                    cameraInterface.replaceView(view)
                    cameraInterface.startPreview(CameraHelper.Facing.BACK, rotation)
                }
            }

            Log.d(
                TAG,
                "Установлено новое разрешение стрима: ${resolution.width}x${resolution.height}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при изменении разрешения стрима: ${e.message}")
        }
    }

    /**
     * Enable audio for streaming
     */
    fun enableAudio() = cameraInterface.enableAudio()

    /**
     * Disable audio for streaming
     */
    fun disableAudio() = cameraInterface.disableAudio()

    /**
     * Release all camera resources
     */
    fun releaseCamera() {
        try {
            Log.d(TAG, "Releasing camera resources")

            if (isStreaming()) {
                Log.d(TAG, "Not releasing camera because streaming is active")
                return;  // Раннее возвращение без изменения состояния
            }
            
            if (isOnPreview()) stopPreview()
            if (isRecording()) stopRecord()

            cameraInterface = CameraInterface.create(context, connectChecker, streamType)
            currentView = null

            Log.d(TAG, "Camera resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera: ${e.message}", e)
        }
    }

    /**
     * Switches between available cameras
     * @return true if camera switched successfully, false otherwise
     */
    fun switchCamera(): Boolean {
        try {
            // First try using the Camera2Source approach
            val camera2Result =
                withCamera2Source({ camera2Source ->
                    Log.d(TAG, "Switching camera using Camera2Source")

                    if (cameraIds.isEmpty()) getCameraIds()
                    if (cameraIds.isEmpty()) return@withCamera2Source false

                    // Switch the camera
                    currentCameraId = (currentCameraId + 1) % cameraIds.size

                    Log.d(TAG, "Switching to camera ${cameraIds[currentCameraId]}")
                    camera2Source.openCameraId(cameraIds[currentCameraId])
                    true
                }, false)

            // If Camera2Source approach failed, try using the CameraInterface directly
            if (!camera2Result) {
                Log.d(TAG, "Switching camera using CameraInterface")
                cameraInterface.switchCamera()
                currentCameraId = if (currentCameraId == 0) 1 else 0
                return true
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error switching camera: ${e.message}", e)
            return false
        }
    }

    /**
     * Toggles the flashlight/lantern
     * @return true if lantern is enabled after toggle, false otherwise
     */
    fun toggleLantern(): Boolean {
        try {
            // First try using the Camera2Source approach
            val camera2Result =
                withCamera2Source({ camera2Source ->
                    try {
                        Log.d(TAG, "Toggling lantern using Camera2Source")
                        val wasEnabled = camera2Source.isLanternEnabled()
                        if (wasEnabled) {
                            camera2Source.disableLantern()
                            Log.d(TAG, "Lantern disabled")
                        } else {
                            camera2Source.enableLantern()
                            Log.d(TAG, "Lantern enabled")
                        }
                        camera2Source.isLanternEnabled()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to toggle lantern with Camera2Source: ${e.localizedMessage}", e)
                        false
                    }
                }, false)

            // If Camera2Source approach failed, try using the CameraInterface directly
            if (!camera2Result) {
                Log.d(TAG, "Toggling lantern using CameraInterface")
                lanternEnabled = !lanternEnabled

                if (lanternEnabled) {
                    cameraInterface.enableLantern()
                    Log.d(TAG, "Lantern enabled via CameraInterface")
                } else {
                    cameraInterface.disableLantern()
                    Log.d(TAG, "Lantern disabled via CameraInterface")
                }

                return lanternEnabled
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling lantern, trying fallback: ${e.message}", e)
            return false
            // return fallbackToggleLantern()
        }
    }

    /**
     * Handles zoom gestures
     */
    fun setZoom(motionEvent: MotionEvent) {
        withCamera2Source({ camera2Source ->
            camera2Source.setZoom(motionEvent)
        }, Unit)
    }

    /**
     * Handles tap-to-focus gestures
     */
    fun tapToFocus(motionEvent: MotionEvent) {
        withCamera2Source({ camera2Source ->
            camera2Source.tapToFocus(motionEvent)
        }, Unit)
    }

    /**
     * Gets the current zoom level
     * @return current zoom level or 0 if not available
     */
    fun getZoom(): Float =
        withCamera2Source({ camera2Source ->
            Log.d(TAG, "Zoom is ${camera2Source.getZoom()}")
            val zoomRange = camera2Source.getZoomRange()
            if (camera2Source.getZoom() < zoomRange.lower || camera2Source.getZoom() > zoomRange.upper) {
                zoomRange.lower
            } else {
                camera2Source.getZoom()
            }
        }, 0f)

    /**
     * Gets the current camera ID
     */
    fun getCurrentCameraId(): Int = currentCameraId
}
