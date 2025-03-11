package net.emerlink.stream.service.stream

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.model.Resolution
import net.emerlink.stream.model.StreamType
import net.emerlink.stream.service.camera.CameraInterface
import net.emerlink.stream.util.ErrorHandler

class StreamManager(
    private val context: Context, private val connectChecker: ConnectChecker, private val errorHandler: ErrorHandler
) {
    companion object {
        private const val TAG = "StreamManager"
        private const val DEFAULT_FPS = 30
        private const val DEFAULT_BITRATE = 2500000 // 2.5 Mbps
    }

    private var currentView: OpenGlView? = null
    private var currentIsPortrait: Boolean = false

    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

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
                val resolution = Resolution.parseFromPreferences(sharedPreferences)
                switchStreamResolution(resolution.width, resolution.height)
                restartPreview(currentView, currentIsPortrait)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при обновлении разрешения: ${e.message}", e)
            }
        }
    }

    private fun restartPreview(view: OpenGlView, isPortrait: Boolean) {
        try {
            Log.d(TAG, "Перезапуск превью")
            stopPreview()

            Handler(Looper.getMainLooper()).postDelayed({
                startPreview(view, isPortrait)
                Log.d(TAG, "Превью перезапущено успешно")
            }, 100)
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

    fun startStream(url: String, protocol: String, username: String, password: String, tcp: Boolean) {
        try {
            Log.d(TAG, "Starting stream with protocol: $protocol, tcp: $tcp")

            if (protocol.startsWith("rtsp")) {
                cameraInterface.setProtocol(tcp)
            }

            if (username.isNotEmpty() && password.isNotEmpty() && (protocol.startsWith("rtmp") || protocol.startsWith("rtsp"))) {
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

    fun startPreview(view: OpenGlView, isPortrait: Boolean = false) {
        try {
            Log.d(TAG, "Запуск превью")
            currentView = view
            currentIsPortrait = isPortrait


            if (isOnPreview()) {
                cameraInterface.stopPreview()
            }

            cameraInterface.replaceView(view)

            val bitrate = cameraInterface.bitrate.takeIf { it > 0 } ?: DEFAULT_BITRATE

            val resolution = Resolution.parseFromPreferences(sharedPreferences)

            cameraInterface.prepareVideo(resolution.width, resolution.height, DEFAULT_FPS, bitrate)

            val rotation = if (isPortrait) 90 else 0
            cameraInterface.startPreview(CameraHelper.Facing.BACK, rotation)

            Log.d(
                TAG,
                "Превью успешно запущен с разрешением ${resolution.width}x${resolution.height}, isPortrait=$isPortrait, rotation=$rotation"
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
        try {
            Log.d(TAG, "Подготовка аудио")
            cameraInterface.prepareAudio()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка подготовки аудио: ${e.message}", e)
            return false
        }
    }

    fun prepareVideo(): Boolean {
        try {
            val resolution = Resolution.parseFromPreferences(sharedPreferences)
            val fps = sharedPreferences.getString(
                PreferenceKeys.VIDEO_FPS, PreferenceKeys.VIDEO_FPS_DEFAULT
            )?.toIntOrNull() ?: 30
            val videoBitrate = sharedPreferences.getString(
                PreferenceKeys.VIDEO_BITRATE, PreferenceKeys.VIDEO_BITRATE_DEFAULT
            )?.toIntOrNull() ?: 2500

            Log.d(TAG, "Подготовка видео: ${resolution.width}x${resolution.height}, FPS=$fps, битрейт=${videoBitrate}k")

            val rotation = if (currentIsPortrait) 90 else 0
            cameraInterface.prepareVideo(resolution.width, resolution.height, fps, videoBitrate * 1000, rotation)

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

    fun setVideoBitrateOnFly(bitrate: Int) {
        cameraInterface.setVideoBitrateOnFly(bitrate)
    }

    fun hasCongestion(): Boolean = cameraInterface.hasCongestion()

    fun switchStreamResolution(width: Int, height: Int) {
        try {
            val bitrate = cameraInterface.bitrate.takeIf { it > 0 } ?: DEFAULT_BITRATE

            if (isOnPreview()) {
                cameraInterface.stopPreview()
                cameraInterface.prepareVideo(width, height, DEFAULT_FPS, bitrate)

                val rotation = if (currentIsPortrait) 90 else 0
                currentView?.let { view ->
                    cameraInterface.replaceView(view)
                    cameraInterface.startPreview(CameraHelper.Facing.BACK, rotation)
                }
            }

            Log.d(TAG, "Установлено новое разрешение стрима: ${width}x${height}, isPortrait=$currentIsPortrait")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при изменении разрешения стрима: ${e.message}")
        }
    }

    fun enableAudio() {
        cameraInterface.enableAudio()
    }

    fun disableAudio() {
        cameraInterface.disableAudio()
    }

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
