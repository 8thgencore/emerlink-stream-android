package net.emerlink.stream.service.stream

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.model.Resolution
import net.emerlink.stream.model.StreamSettings
import net.emerlink.stream.model.StreamType
import net.emerlink.stream.service.camera.CameraInterface
import net.emerlink.stream.util.ErrorHandler

class StreamManager(
    private val context: Context,
    private val connectChecker: ConnectChecker,
    private val errorHandler: ErrorHandler,
    private val streamSettings: StreamSettings,
) {
    companion object {
        private const val TAG = "StreamManager"
    }

    private var currentView: OpenGlView? = null

    private var cameraInterface: CameraInterface
    private var streamType: StreamType = StreamType.RTMP

    init {
        cameraInterface = CameraInterface.create(context, connectChecker, streamType)
    }

    fun handleResolutionChange() {
        val currentView = this.currentView
        if (currentView != null) {
            Log.d(TAG, "Перезапуск превью с новым разрешением")
            try {
                switchStreamResolution()
                restartPreview(currentView)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при обновлении разрешения: ${e.message}", e)
            }
        }
    }

    private fun restartPreview(view: OpenGlView) {
        try {
            Log.d(TAG, "Перезапуск превью")
            stopPreview()

            Handler(Looper.getMainLooper())
                .postDelayed(
                    {
                        startPreview(view)
                        Log.d(TAG, "Превью перезапущено успешно")
                    },
                    100
                )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при перезапуске превью: ${e.message}", e)
        }
    }

    fun setStreamType(type: StreamType) {
        if (type != streamType) {
            streamType = type
            cameraInterface = CameraInterface.create(context, connectChecker, streamType)
        }
    }

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

    fun stopStream() {
        try {
            if (isStreaming()) {
                cameraInterface.stopStream()
            }
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
        }
    }

    fun startRecord(filePath: String) {
        try {
            cameraInterface.startRecord(filePath)
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
        }
    }

    fun stopRecord() {
        try {
            if (isRecording()) {
                cameraInterface.stopRecord()
            }
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
        }
    }

    fun startPreview(view: OpenGlView) {
        try {
            Log.d(TAG, "Запуск превью")
            currentView = view

            if (isOnPreview()) {
                cameraInterface.stopPreview()
            }

            cameraInterface.replaceView(view)

            val bitrate = cameraInterface.bitrate.takeIf { it > 0 } ?: (streamSettings.bitrate * 1000)
            val resolution = Resolution.parseFromSize(streamSettings.resolution)
            cameraInterface.prepareVideo(
                width = resolution.width,
                height = resolution.height,
                fps = streamSettings.fps,
                iFrameInterval = streamSettings.iFrameInterval,
                bitrate = bitrate,
                rotation = CameraHelper.getCameraOrientation(context)
            )

            val rotation = CameraHelper.getCameraOrientation(context)
            cameraInterface.startPreview(CameraHelper.Facing.BACK, rotation)

            Log.d(
                TAG,
                "Превью успешно запущено ${resolution.width}x${resolution.height}, rotation=$rotation"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при запуске preview: ${e.message}")
            errorHandler.handleStreamError(e)
        }
    }

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

    fun prepareAudio(): Boolean {
        Log.d(TAG, "Подготовка аудио")

        // Try with known working configuration first
        try {
            cameraInterface.prepareAudio(
                bitrate = streamSettings.audioBitrate * 1024,
                sampleRate = streamSettings.audioSampleRate.toInt(),
                isStereo = streamSettings.audioStereo
            )
            cameraInterface.setAudioCodec(AudioCodec.AAC)
            return true
        } catch (e: Exception) {
            // Try fallback configuration
            try {
                cameraInterface.prepareAudio(
                    bitrate = 128 * 1024,
                    sampleRate = 44100,
                    isStereo = false
                )
                cameraInterface.setAudioCodec(AudioCodec.AAC)
                return true
            } catch (e2: Exception) {
                Log.e(TAG, "Не удалось инициализировать аудио: ${e2.message}", e2)
                return false
            }
            Log.e(TAG, "Ошибка подготовки аудио: ${e.message}", e)
            return false
        }
    }

    fun prepareVideo(): Boolean {
        try {
            val resolution = Resolution.parseFromSize(streamSettings.resolution)
            val rotation = CameraHelper.getCameraOrientation(context)
            cameraInterface.prepareVideo(
                width = resolution.width,
                height = resolution.height,
                fps = streamSettings.fps,
                bitrate = streamSettings.bitrate * 1000,
                iFrameInterval = streamSettings.iFrameInterval,
                rotation = rotation
            )

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка подготовки видео: ${e.message}", e)
            return false
        }
    }

    fun isStreaming(): Boolean = cameraInterface.isStreaming

    fun isRecording(): Boolean = cameraInterface.isRecording

    fun isOnPreview(): Boolean = cameraInterface.isOnPreview

    fun getVideoSource(): Any = cameraInterface

    fun getGlInterface(): Any = cameraInterface.glInterface

    fun setVideoBitrateOnFly(bitrate: Int) = cameraInterface.setVideoBitrateOnFly(bitrate)

    fun hasCongestion(): Boolean = cameraInterface.hasCongestion()

    fun switchStreamResolution() {
        try {
            val resolution = Resolution.parseFromSize(streamSettings.resolution)
            if (isOnPreview()) {
                val rotation = CameraHelper.getCameraOrientation(context)

                cameraInterface.stopPreview()
                cameraInterface.prepareVideo(
                    width = resolution.width,
                    height = resolution.height,
                    fps = streamSettings.fps,
                    bitrate = streamSettings.bitrate * 1000,
                    iFrameInterval = streamSettings.iFrameInterval,
                    rotation = rotation
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

    fun enableAudio() = cameraInterface.enableAudio()

    fun disableAudio() = cameraInterface.disableAudio()

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
