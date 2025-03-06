package net.emerlink.stream.service

import android.content.Context
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.rtsp.RtspCamera2
import com.pedro.library.srt.SrtCamera2
import com.pedro.library.udp.UdpCamera2
import com.pedro.library.view.OpenGlView
import com.pedro.rtsp.rtsp.Protocol
import net.emerlink.stream.model.StreamType
import net.emerlink.stream.util.ErrorHandler

class StreamManager(
    private val context: Context,
    private val connectChecker: ConnectChecker,
    private val errorHandler: ErrorHandler
) {
    companion object {
        private const val TAG = "StreamManager"
    }

    private lateinit var rtmpCamera: RtmpCamera2
    private lateinit var rtspCamera: RtspCamera2
    private lateinit var srtCamera: SrtCamera2
    private lateinit var udpCamera: UdpCamera2

    private var streamType: StreamType = StreamType.RTMP

    init {
        initializeClients()
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
                    if (tcp) {
                        rtspCamera.getStreamClient().setProtocol(Protocol.TCP)
                    } else {
                        rtspCamera.getStreamClient().setProtocol(Protocol.UDP)
                    }
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

    fun startPreview(view: OpenGlView, isPortrait: Boolean) {
        try {
            when (streamType) {
                StreamType.RTMP -> {
                    val camera = rtmpCamera
                    configureAndStartPreview(camera, view, isPortrait)
                }
                StreamType.RTSP -> {
                    val camera = rtspCamera
                    configureAndStartPreview(camera, view, isPortrait)
                }
                StreamType.SRT -> {
                    val camera = srtCamera
                    configureAndStartPreview(camera, view, isPortrait)
                }
                StreamType.UDP -> {
                    val camera = udpCamera
                    configureAndStartPreview(camera, view, isPortrait)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при запуске preview: ${e.message}")
            errorHandler.handleStreamError(e)
        }
    }

    private fun <T> configureAndStartPreview(camera: T, view: OpenGlView, isPortrait: Boolean) where T : Any {
        try {
            // Единый блок для остановки preview
            stopCurrentPreview(camera)
            
            // Единый блок для замены view
            replaceViewForCamera(camera, view)
            
            // Получаем интерфейс GL и настраиваем поворот
            if (isPortrait) {
                configurePortraitMode(camera)
            }
            
            // Запускаем preview с правильной ориентацией
            val rotation = if (isPortrait) 90 else 0
            startCameraPreview(camera, rotation)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при настройке и запуске preview: ${e.message}")
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

    private fun <T> configurePortraitMode(camera: T) where T : Any {
        try {
            when (camera) {
                is RtmpCamera2 -> camera.glInterface?.let { it.setStreamRotation(90) }
                is RtspCamera2 -> camera.glInterface?.let { it.setStreamRotation(90) }
                is SrtCamera2 -> camera.glInterface?.let { it.setStreamRotation(90) }
                is UdpCamera2 -> camera.glInterface?.let { it.setStreamRotation(90) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось установить ориентацию потока: ${e.message}")
        }
    }

    private fun <T> getGlInterfaceFromCamera(camera: T): Any? where T : Any {
        return when (camera) {
            is RtmpCamera2 -> camera.glInterface
            is RtspCamera2 -> camera.glInterface
            is SrtCamera2 -> camera.glInterface
            is UdpCamera2 -> camera.glInterface
            else -> null
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
        return when (streamType) {
            StreamType.RTMP -> rtmpCamera
            StreamType.RTSP -> rtspCamera
            StreamType.SRT -> srtCamera
            StreamType.UDP -> udpCamera
        }
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

    /**
     * Устанавливает ориентацию исходящего видеопотока
     */
    fun setStreamOrientation(isPortrait: Boolean) {
        val rotation = if (isPortrait) 90 else 0
        
        try {
            when (streamType) {
                StreamType.RTMP -> {
                    rtmpCamera.glInterface?.setStreamRotation(rotation)
                }
                StreamType.RTSP -> {
                    rtspCamera.glInterface?.setStreamRotation(rotation)
                }
                StreamType.SRT -> {
                    srtCamera.glInterface?.setStreamRotation(rotation)
                }
                StreamType.UDP -> {
                    udpCamera.glInterface?.setStreamRotation(rotation)
                }
            }
            Log.d(TAG, "Установлена ориентация потока: $rotation градусов")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при установке ориентации потока: ${e.message}")
        }
    }

    /**
     * Изменяет разрешение стрима (ширина x высота)
     */
    fun switchStreamResolution(width: Int, height: Int) {
        try {
            val defaultFps = 30
            val iFrameInterval = 2 // Стандартный интервал между ключевыми кадрами
            
            when (streamType) {
                StreamType.RTMP -> {
                    rtmpCamera.stopPreview()
                    // Получаем текущий битрейт
                    val bitrate = rtmpCamera.bitrate
                    // Применяем новые настройки с исправленными параметрами
                    rtmpCamera.prepareVideo(width, height, defaultFps, bitrate, iFrameInterval)
                    rtmpCamera.startPreview()
                }
                StreamType.RTSP -> {
                    rtspCamera.stopPreview()
                    val bitrate = rtspCamera.bitrate
                    rtspCamera.prepareVideo(width, height, defaultFps, bitrate, iFrameInterval)
                    rtspCamera.startPreview()
                }
                StreamType.SRT -> {
                    srtCamera.stopPreview()
                    val bitrate = srtCamera.bitrate
                    srtCamera.prepareVideo(width, height, defaultFps, bitrate, iFrameInterval)
                    srtCamera.startPreview()
                }
                StreamType.UDP -> {
                    udpCamera.stopPreview()
                    val bitrate = udpCamera.bitrate
                    udpCamera.prepareVideo(width, height, defaultFps, bitrate, iFrameInterval)
                    udpCamera.startPreview()
                }
            }
            Log.d(TAG, "Установлено новое разрешение стрима: ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при изменении разрешения стрима: ${e.message}")
        }
    }

    /**
     * Общий метод для доступа к аудио-функциям стримеров
     */
    fun enableAudio() {
        when (streamType) {
            StreamType.RTMP -> rtmpCamera.enableAudio()
            StreamType.RTSP -> rtspCamera.enableAudio()
            StreamType.SRT -> srtCamera.enableAudio()
            StreamType.UDP -> udpCamera.enableAudio()
        }
    }

    /**
     * Общий метод для отключения аудио
     */
    fun disableAudio() {
        when (streamType) {
            StreamType.RTMP -> rtmpCamera.disableAudio()
            StreamType.RTSP -> rtspCamera.disableAudio()
            StreamType.SRT -> srtCamera.disableAudio()
            StreamType.UDP -> udpCamera.disableAudio()
        }
    }
}
