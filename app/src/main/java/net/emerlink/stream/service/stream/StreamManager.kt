package net.emerlink.stream.service.stream

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
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

    init {
        cameraInterface = CameraInterface.create(context, connectChecker, streamType)
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
     * Принудительно перезапускает видеокодер, если стрим активен
     * Используется для восстановления видеопотока после сворачивания приложения
     */
    fun restartVideoEncoder() {
        if (isStreaming()) {
            Log.d(TAG, "Перезапускаем видеокодер для возобновления видеопотока")
            cameraInterface.restartVideoEncoder()
        }
    }

    /**
     * Restarts the camera preview
     * @param view The OpenGlView to display the preview on
     */
    private fun restartPreview(view: OpenGlView) {
        try {
            Log.d(
                TAG,
                "Перезапуск превью. Статус: стриминг=${isStreaming()}, запись=${isRecording()}, наПревью=${isOnPreview()}"
            )

            // Сохраняем текущий статус стрима, чтобы знать, нужно ли его возобновлять
            val wasStreaming = isStreaming()

            // Останавливаем превью, но НЕ останавливаем стрим
            if (isOnPreview()) {
                stopPreview()
            }

            // Небольшая задержка для того, чтобы убедиться, что предыдущие ресурсы освобождены
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    Log.d(TAG, "Запускаем новое превью при restartPreview")

                    // Заменяем отображение
                    cameraInterface.replaceView(view)

                    // Если стрим уже идет, нужно быть осторожными с перенастройкой видео
                    if (!wasStreaming) {
                        prepareVideoWithCurrentSettings()
                    }

                    // Запускаем превью
                    startPreviewInternal(view)

                    // Если стрим был активен, перезапускаем видеоэнкодер чтобы видео возобновилось
                    if (wasStreaming) {
                        scheduleVideoEncoderRestart()
                    }

                    Log.d(TAG, "Превью успешно перезапущено после разворачивания")
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при запуске превью во время restartPreview: ${e.message}", e)
                }
            }, DELAY_RESTART_PREVIEW_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при перезапуске превью: ${e.message}", e)
        }
    }

    /**
     * Schedule restart of video encoder with delay
     */
    private fun scheduleVideoEncoderRestart() {
        Handler(Looper.getMainLooper()).postDelayed({
            restartVideoEncoder()
        }, DELAY_RESTART_ENCODER_MS)
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
            Log.d(
                TAG,
                "Запуск превью. Статус: стриминг=${isStreaming()}, запись=${isRecording()}, наПревью=${isOnPreview()}"
            )
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
            errorHandler.handleStreamError(e)
        }
    }

    /**
     * Internal helper method to start preview
     */
    private fun startPreviewInternal(view: OpenGlView) {
        val rotation = CameraHelper.getCameraOrientation(context)
        cameraInterface.startPreview(CameraHelper.Facing.BACK, rotation)
        currentView = view

        val videoSettings = settingsRepository.videoSettingsFlow.value
        val resolution = Resolution.parseFromSize(videoSettings.resolution)
        Log.d(
            TAG,
            "Превью успешно запущено ${resolution.width}x${resolution.height}, rotation=$rotation, streaming=${isStreaming()}"
        )
    }

    /**
     * Stop the camera preview
     */
    fun stopPreview() {
        try {
            if (isOnPreview()) {
                Log.d(TAG, "Stopping Preview")
                cameraInterface.stopPreview()
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
    fun getVideoSource(): Any = cameraInterface

    /**
     * Get the GL interface for rendering
     */
    fun getGlInterface(): Any = cameraInterface.glInterface

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
            Log.d(TAG, "Освобождение ресурсов камеры")

            if (isOnPreview()) stopPreview()
            if (isStreaming()) stopStream()
            if (isRecording()) stopRecord()

            cameraInterface = CameraInterface.create(context, connectChecker, streamType)
            currentView = null

            Log.d(TAG, "Ресурсы камеры освобождены")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка освобождения камеры: ${e.message}", e)
        }
    }

    /**
     * Gets the camera interface implementation
     * @return The current camera interface
     */
    fun getCameraInterface(): CameraInterface = cameraInterface
}
