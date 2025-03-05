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

    fun startPreview(openGlView: OpenGlView) {
        try {
            if (!isOnPreview()) {
                Log.d(TAG, "Starting Preview")
                when (streamType) {
                    StreamType.RTMP -> {
                        rtmpCamera.replaceView(openGlView)
                        rtmpCamera.startPreview(CameraHelper.Facing.BACK, 0)
                    }
                    StreamType.RTSP -> {
                        rtspCamera.replaceView(openGlView)
                        rtspCamera.startPreview(CameraHelper.Facing.BACK, 0)
                    }
                    StreamType.SRT -> {
                        srtCamera.replaceView(openGlView)
                        srtCamera.startPreview(CameraHelper.Facing.BACK, 0)
                    }
                    StreamType.UDP -> {
                        udpCamera.replaceView(openGlView)
                        udpCamera.startPreview(CameraHelper.Facing.BACK, 0)
                    }
                }
            } else {
                Log.e(TAG, "Already on preview")
            }
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
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

    private fun isOnPreview(): Boolean {
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
}
