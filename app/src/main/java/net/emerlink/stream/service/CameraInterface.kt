package net.emerlink.stream.service

import android.content.Context
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.base.Camera2Base
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.model.StreamType

/**
 * Интерфейс для унификации работы с различными типами камер
 */
interface CameraInterface {
    val camera: Camera2Base

    val isStreaming: Boolean
    val isRecording: Boolean
    val isOnPreview: Boolean
    val bitrate: Int
    val glInterface: Any

    fun prepareAudio(): Boolean
    fun prepareVideo(width: Int, height: Int, fps: Int, bitrate: Int, iFrameInterval: Int): Boolean
    fun startStream(url: String)
    fun stopStream()
    fun startRecord(filePath: String)
    fun stopRecord()
    fun startPreview(facing: CameraHelper.Facing, rotation: Int)
    fun stopPreview()
    fun replaceView(view: OpenGlView)
    fun switchCamera()
    fun setVideoBitrateOnFly(bitrate: Int)
    fun enableAudio()
    fun disableAudio()
    fun hasCongestion(): Boolean
    fun enableLantern(): Boolean
    fun disableLantern(): Boolean

    fun setAuthorization(username: String, password: String)
    fun setProtocol(tcp: Boolean)

    companion object {
        fun create(
            context: Context,
            connectChecker: ConnectChecker,
            streamType: StreamType
        ): CameraInterface {
            return when (streamType) {
                StreamType.RTMP -> RtmpCameraImpl(context, connectChecker)
                StreamType.RTSP -> RtspCameraImpl(context, connectChecker)
                StreamType.SRT -> SrtCameraImpl(context, connectChecker)
                StreamType.UDP -> UdpCameraImpl(context, connectChecker)
            }
        }
    }
} 