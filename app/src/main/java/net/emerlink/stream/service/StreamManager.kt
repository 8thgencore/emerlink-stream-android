package net.emerlink.stream.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.rtsp.RtspCamera2
import com.pedro.library.srt.SrtCamera2
import com.pedro.library.udp.UdpCamera2
import com.pedro.library.view.OpenGlView
import com.pedro.rtsp.rtsp.Protocol
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.model.StreamType
import net.emerlink.stream.util.ErrorHandler
import java.util.Locale

class StreamManager(
    private val context: Context,
    private val connectChecker: ConnectChecker,
    private val errorHandler: ErrorHandler
) : SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        private const val TAG = "StreamManager"
        private const val DEFAULT_FPS = 30
        private const val DEFAULT_I_FRAME_INTERVAL = 2
        private const val DEFAULT_BITRATE = 2500000 // 2.5 Mbps
    }

    private var currentView: OpenGlView? = null
    private var currentIsPortrait: Boolean = false

    private val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private lateinit var rtmpCamera: RtmpCamera2
    private lateinit var rtspCamera: RtspCamera2
    private lateinit var srtCamera: SrtCamera2
    private lateinit var udpCamera: UdpCamera2

    private var streamType: StreamType = StreamType.RTMP

    init {
        initializeClients()
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    fun destroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == PreferenceKeys.VIDEO_RESOLUTION) {
            Log.d(TAG, "Обнаружено изменение разрешения в настройках")
            val currentView = this.currentView
            if (currentView != null) {
                Log.d(TAG, "Перезапуск превью с новым разрешением")
                try {
                    val (videoWidth, videoHeight) = parseResolution(sharedPreferences)

                    switchStreamResolution(videoWidth, videoHeight)
                    restartPreview(currentView, currentIsPortrait)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при обновлении разрешения: ${e.message}", e)
                }
            }
        }
    }

    private fun parseResolution(sharedPreferences: SharedPreferences): Pair<Int, Int> {
        val videoResolution = sharedPreferences.getString(
            PreferenceKeys.VIDEO_RESOLUTION,
            PreferenceKeys.VIDEO_RESOLUTION_DEFAULT
        ) ?: "1920x1080"

        val dimensions = videoResolution.lowercase(Locale.getDefault()).replace("х", "x").split("x")
        val videoWidth = if (dimensions.isNotEmpty()) dimensions[0].toIntOrNull() ?: 1920 else 1920
        val videoHeight = if (dimensions.size >= 2) dimensions[1].toIntOrNull() ?: 1080 else 1080

        return Pair(videoWidth, videoHeight)
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

    private fun initializeClients() {
        rtmpCamera = RtmpCamera2(context, connectChecker)
        rtspCamera = RtspCamera2(context, connectChecker)
        srtCamera = SrtCamera2(context, connectChecker)
        udpCamera = UdpCamera2(context, connectChecker)
    }

    fun setStreamType(type: StreamType) {
        streamType = type
    }

    fun getStream(): Any {
        return when (streamType) {
            StreamType.RTMP -> rtmpCamera
            StreamType.RTSP -> rtspCamera
            StreamType.SRT -> srtCamera
            StreamType.UDP -> udpCamera
        }
    }

    fun startStream(
        url: String,
        protocol: String,
        username: String,
        password: String,
        tcp: Boolean
    ) {
        try {
            when {
                protocol.startsWith("rtmp") -> {
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        rtmpCamera.getStreamClient().setAuthorization(username, password)
                    }
                    rtmpCamera.startStream(url)
                }

                protocol.startsWith("rtsp") -> {
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        rtspCamera.getStreamClient().setAuthorization(username, password)
                    }
                    rtspCamera.getStreamClient().setProtocol(if (tcp) Protocol.TCP else Protocol.UDP)
                    rtspCamera.startStream(url)
                }

                protocol == "srt" -> {
                    srtCamera.startStream(url)
                }

                else -> {
                    udpCamera.startStream(url)
                }
            }
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
        }
    }

    fun stopStream() {
        try {
            if (isStreaming()) {
                when (streamType) {
                    StreamType.RTMP -> rtmpCamera.stopStream()
                    StreamType.RTSP -> rtspCamera.stopStream()
                    StreamType.SRT -> srtCamera.stopStream()
                    StreamType.UDP -> udpCamera.stopStream()
                }
            }
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
        }
    }

    fun startRecord(filePath: String) {
        try {
            when (streamType) {
                StreamType.RTMP -> rtmpCamera.startRecord(filePath)
                StreamType.RTSP -> rtspCamera.startRecord(filePath)
                StreamType.SRT -> srtCamera.startRecord(filePath)
                StreamType.UDP -> udpCamera.startRecord(filePath)
            }
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
        }
    }

    fun stopRecord() {
        try {
            if (isRecording()) {
                when (streamType) {
                    StreamType.RTMP -> rtmpCamera.stopRecord()
                    StreamType.RTSP -> rtspCamera.stopRecord()
                    StreamType.SRT -> srtCamera.stopRecord()
                    StreamType.UDP -> udpCamera.stopRecord()
                }
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

            val (videoWidth, videoHeight) = parseResolution(sharedPreferences)

            updateViewAspectRatio(view, videoWidth, videoHeight, isPortrait)

            val camera = when (streamType) {
                StreamType.RTMP -> rtmpCamera
                StreamType.RTSP -> rtspCamera
                StreamType.SRT -> srtCamera
                StreamType.UDP -> udpCamera
            }

            configureAndStartPreview(camera, view, isPortrait, videoWidth, videoHeight)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при запуске preview: ${e.message}")
            errorHandler.handleStreamError(e)
        }
    }

    private fun <T> configureAndStartPreview(
        camera: T,
        view: OpenGlView,
        isPortrait: Boolean,
        width: Int,
        height: Int
    ) where T : Any {
        try {
            stopCurrentPreview(camera)
            replaceViewForCamera(camera, view)

            val bitrate = getBitrateFromCamera(camera) ?: DEFAULT_BITRATE

            when (camera) {
                is RtmpCamera2 -> camera.prepareVideo(width, height, DEFAULT_FPS, bitrate, DEFAULT_I_FRAME_INTERVAL)
                is RtspCamera2 -> camera.prepareVideo(width, height, DEFAULT_FPS, bitrate, DEFAULT_I_FRAME_INTERVAL)
                is SrtCamera2 -> camera.prepareVideo(width, height, DEFAULT_FPS, bitrate, DEFAULT_I_FRAME_INTERVAL)
                is UdpCamera2 -> camera.prepareVideo(width, height, DEFAULT_FPS, bitrate, DEFAULT_I_FRAME_INTERVAL)
            }

            val rotation = if (isPortrait) 90 else 0
            startCameraPreview(camera, rotation)

            Log.d(TAG, "Превью успешно запущен с разрешением ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при настройке и запуске preview: ${e.message}")
        }
    }

    private fun <T> getBitrateFromCamera(camera: T): Int? where T : Any {
        return when (camera) {
            is RtmpCamera2 -> camera.bitrate
            is RtspCamera2 -> camera.bitrate
            is SrtCamera2 -> camera.bitrate
            is UdpCamera2 -> camera.bitrate
            else -> null
        }
    }

    private fun <T> stopCurrentPreview(camera: T) where T : Any {
        when (camera) {
            is RtmpCamera2 -> if (camera.isOnPreview) camera.stopPreview()
            is RtspCamera2 -> if (camera.isOnPreview) camera.stopPreview()
            is SrtCamera2 -> if (camera.isOnPreview) camera.stopPreview()
            is UdpCamera2 -> if (camera.isOnPreview) camera.stopPreview()
        }
    }

    private fun <T> replaceViewForCamera(camera: T, view: OpenGlView) where T : Any {
        when (camera) {
            is RtmpCamera2 -> camera.replaceView(view)
            is RtspCamera2 -> camera.replaceView(view)
            is SrtCamera2 -> camera.replaceView(view)
            is UdpCamera2 -> camera.replaceView(view)
        }
    }

    private fun <T> startCameraPreview(camera: T, rotation: Int) where T : Any {
        when (camera) {
            is RtmpCamera2 -> camera.startPreview(CameraHelper.Facing.BACK, rotation)
            is RtspCamera2 -> camera.startPreview(CameraHelper.Facing.BACK, rotation)
            is SrtCamera2 -> camera.startPreview(CameraHelper.Facing.BACK, rotation)
            is UdpCamera2 -> camera.startPreview(CameraHelper.Facing.BACK, rotation)
        }
    }

    fun stopPreview() {
        try {
            if (isOnPreview()) {
                Log.d(TAG, "Stopping Preview")
                when (streamType) {
                    StreamType.RTMP -> rtmpCamera.stopPreview()
                    StreamType.RTSP -> rtspCamera.stopPreview()
                    StreamType.SRT -> srtCamera.stopPreview()
                    StreamType.UDP -> udpCamera.stopPreview()
                }
            }
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
        }
    }

    fun prepareAudio(): Boolean {
        return when (streamType) {
            StreamType.RTMP -> rtmpCamera.prepareAudio()
            StreamType.RTSP -> rtspCamera.prepareAudio()
            StreamType.SRT -> srtCamera.prepareAudio()
            StreamType.UDP -> udpCamera.prepareAudio()
        }
    }

    fun prepareVideo(): Boolean {
        return when (streamType) {
            StreamType.RTMP -> rtmpCamera.prepareVideo()
            StreamType.RTSP -> rtspCamera.prepareVideo()
            StreamType.SRT -> srtCamera.prepareVideo()
            StreamType.UDP -> udpCamera.prepareVideo()
        }
    }

    fun isStreaming(): Boolean {
        return when (streamType) {
            StreamType.RTMP -> rtmpCamera.isStreaming
            StreamType.RTSP -> rtspCamera.isStreaming
            StreamType.SRT -> srtCamera.isStreaming
            StreamType.UDP -> udpCamera.isStreaming
        }
    }

    fun isRecording(): Boolean {
        return when (streamType) {
            StreamType.RTMP -> rtmpCamera.isRecording
            StreamType.RTSP -> rtspCamera.isRecording
            StreamType.SRT -> srtCamera.isRecording
            StreamType.UDP -> udpCamera.isRecording
        }
    }

    fun isOnPreview(): Boolean {
        return when (streamType) {
            StreamType.RTMP -> rtmpCamera.isOnPreview
            StreamType.RTSP -> rtspCamera.isOnPreview
            StreamType.SRT -> srtCamera.isOnPreview
            StreamType.UDP -> udpCamera.isOnPreview
        }
    }

    fun getVideoSource(): Any {
        return getStream()
    }

    fun getGlInterface(): Any {
        return when (streamType) {
            StreamType.RTMP -> rtmpCamera.glInterface
            StreamType.RTSP -> rtspCamera.glInterface
            StreamType.SRT -> srtCamera.glInterface
            StreamType.UDP -> udpCamera.glInterface
        }
    }

    fun setVideoBitrateOnFly(bitrate: Int) {
        when (streamType) {
            StreamType.RTMP -> rtmpCamera.setVideoBitrateOnFly(bitrate)
            StreamType.RTSP -> rtspCamera.setVideoBitrateOnFly(bitrate)
            StreamType.SRT -> srtCamera.setVideoBitrateOnFly(bitrate)
            StreamType.UDP -> udpCamera.setVideoBitrateOnFly(bitrate)
        }
    }

    fun hasCongestion(): Boolean {
        return when (streamType) {
            StreamType.RTMP -> rtmpCamera.getStreamClient().hasCongestion()
            StreamType.RTSP -> rtspCamera.getStreamClient().hasCongestion()
            StreamType.SRT -> srtCamera.getStreamClient().hasCongestion()
            StreamType.UDP -> udpCamera.getStreamClient().hasCongestion()
        }
    }

    fun switchStreamResolution(width: Int, height: Int) {
        try {
            val bitrate = when (val camera = getStream()) {
                is RtmpCamera2 -> camera.bitrate
                is RtspCamera2 -> camera.bitrate
                is SrtCamera2 -> camera.bitrate
                is UdpCamera2 -> camera.bitrate
                else -> DEFAULT_BITRATE
            }

            when (streamType) {
                StreamType.RTMP -> {
                    rtmpCamera.stopPreview()
                    rtmpCamera.prepareVideo(width, height, DEFAULT_FPS, bitrate, DEFAULT_I_FRAME_INTERVAL)
                    rtmpCamera.startPreview()
                }

                StreamType.RTSP -> {
                    rtspCamera.stopPreview()
                    rtspCamera.prepareVideo(width, height, DEFAULT_FPS, bitrate, DEFAULT_I_FRAME_INTERVAL)
                    rtspCamera.startPreview()
                }

                StreamType.SRT -> {
                    srtCamera.stopPreview()
                    srtCamera.prepareVideo(width, height, DEFAULT_FPS, bitrate, DEFAULT_I_FRAME_INTERVAL)
                    srtCamera.startPreview()
                }

                StreamType.UDP -> {
                    udpCamera.stopPreview()
                    udpCamera.prepareVideo(width, height, DEFAULT_FPS, bitrate, DEFAULT_I_FRAME_INTERVAL)
                    udpCamera.startPreview()
                }
            }
            Log.d(TAG, "Установлено новое разрешение стрима: ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при изменении разрешения стрима: ${e.message}")
        }
    }

    fun enableAudio() {
        when (streamType) {
            StreamType.RTMP -> rtmpCamera.enableAudio()
            StreamType.RTSP -> rtspCamera.enableAudio()
            StreamType.SRT -> srtCamera.enableAudio()
            StreamType.UDP -> udpCamera.enableAudio()
        }
    }

    fun disableAudio() {
        when (streamType) {
            StreamType.RTMP -> rtmpCamera.disableAudio()
            StreamType.RTSP -> rtspCamera.disableAudio()
            StreamType.SRT -> srtCamera.disableAudio()
            StreamType.UDP -> udpCamera.disableAudio()
        }
    }

    private fun updateViewAspectRatio(view: OpenGlView, width: Int, height: Int, isPortrait: Boolean) {
        try {
            val method = view.javaClass.getMethod("setAspectRatioMode", Int::class.javaPrimitiveType)
            val mode = if (isPortrait) 1 else 0
            method.invoke(view, mode)
            Log.d(TAG, "Соотношение сторон OpenGlView обновлено: режим $mode")
        } catch (e: Exception) {
            Log.d(TAG, "Метод setAspectRatioMode не найден, альтернативное обновление невозможно")
        }
    }

    /**
     * Switches between front and back cameras
     * @return true if camera switched successfully, false otherwise
     */
    fun switchCamera(): Boolean {
        try {
            Log.d(TAG, "Switching camera")
            val result = when (streamType) {
                StreamType.RTMP -> rtmpCamera.switchCamera()
                StreamType.RTSP -> rtspCamera.switchCamera()
                StreamType.SRT -> srtCamera.switchCamera()
                StreamType.UDP -> udpCamera.switchCamera()
            }

            Log.d(TAG, "Camera switched successfully: $result")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error switching camera: ${e.message}", e)
            return false
        }
    }
}
