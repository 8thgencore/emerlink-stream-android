package net.emerlink.stream.service.stream

import android.content.Context
import android.media.MediaRecorder
import android.view.MotionEvent
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.library.base.StreamBase
import com.pedro.library.base.recording.RecordController
import com.pedro.library.view.GlInterface
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.data.model.StreamType

/**
 * Interface for camera implementation
 */
interface StreamInterface {
    val stream: StreamBase

    val isStreaming: Boolean
    val isRecording: Boolean
    val isOnPreview: Boolean

    val glInterface: GlInterface

    fun getCameraIds(): List<String>

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

    fun stopStream(): Boolean

    fun startRecord(
        filePath: String,
        listener: RecordController.Listener,
    )

    fun stopRecord(): Boolean

    fun startPreview(
        view: OpenGlView,
        autoHandle: Boolean,
    )

    fun stopPreview()

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

    fun setZoom(motionEvent: MotionEvent)

    fun tapToFocus(motionEvent: MotionEvent)

    companion object {
        fun create(
            context: Context,
            connectChecker: ConnectChecker,
            streamType: StreamType,
        ): StreamInterface =
            when (streamType) {
                StreamType.RTMP -> RtmpStreamImpl(context, connectChecker)
                StreamType.RTMPs -> RtmpStreamImpl(context, connectChecker)
                StreamType.RTSP -> RtspStreamImpl(context, connectChecker)
                StreamType.RTSPs -> RtspStreamImpl(context, connectChecker)
                StreamType.SRT -> SrtStreamImpl(context, connectChecker)
                StreamType.UDP -> UdpStreamImpl(context, connectChecker)
            }
    }
}
