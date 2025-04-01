package net.emerlink.stream.service.camera

import android.content.Context
import android.media.MediaRecorder
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.base.Camera2Base
import com.pedro.library.base.StreamBase
import com.pedro.library.view.GlInterface
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.data.model.StreamType

/**
 * Interface for camera implementation
 */
interface CameraInterface {
    val camera: Camera2Base
    val stream: StreamBase

    val isStreaming: Boolean
    val isRecording: Boolean
    val isOnPreview: Boolean
    val bitrate: Int
    val glInterface: GlInterface

    fun prepareAudio(
        audioSource: Int = MediaRecorder.AudioSource.DEFAULT,
        bitrate: Int = 128 * 1000,
        sampleRate: Int = 44100,
        isStereo: Boolean = true,
        echoCanceler: Boolean = false,
        noiseSuppressor: Boolean = false,
    ): Boolean

    fun prepareVideo(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        iFrameInterval: Int = 2,
        rotation: Int = 0,
    ): Boolean

    fun startStream(url: String)

    fun stopStream()

    fun startRecord(filePath: String)

    fun stopRecord()

    fun startPreview(
        facing: CameraHelper.Facing,
        rotation: Int,
    )

    fun stopPreview()

    fun replaceView(view: OpenGlView)

    fun switchCamera()

    fun setVideoBitrateOnFly(bitrate: Int)

    fun enableAudio()

    fun disableAudio()

    fun hasCongestion(): Boolean

    fun enableLantern()

    fun disableLantern()

    fun setAuthorization(
        username: String,
        password: String,
    )

    fun setProtocol(tcp: Boolean)

    fun setAudioCodec(codec: AudioCodec)

    companion object {
        fun create(
            context: Context,
            connectChecker: ConnectChecker,
            streamType: StreamType,
        ): CameraInterface =
            when (streamType) {
                StreamType.RTMP -> RtmpCameraImpl(context, connectChecker)
                StreamType.RTMPs -> RtmpCameraImpl(context, connectChecker)
                StreamType.RTSP -> RtspCameraImpl(context, connectChecker)
                StreamType.RTSPs -> RtspCameraImpl(context, connectChecker)
                StreamType.SRT -> SrtCameraImpl(context, connectChecker)
                StreamType.UDP -> UdpCameraImpl(context, connectChecker)
            }
    }
}
