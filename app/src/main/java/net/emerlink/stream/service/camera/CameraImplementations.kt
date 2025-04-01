package net.emerlink.stream.service.camera

import android.content.Context
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.library.base.recording.RecordController
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.rtmp.RtmpStream
import com.pedro.library.rtsp.RtspCamera2
import com.pedro.library.rtsp.RtspStream
import com.pedro.library.srt.SrtCamera2
import com.pedro.library.srt.SrtStream
import com.pedro.library.udp.UdpCamera2
import com.pedro.library.udp.UdpStream
import com.pedro.library.view.GlInterface
import com.pedro.library.view.OpenGlView
import com.pedro.rtsp.rtsp.Protocol

class RtmpCameraImpl(
    context: Context,
    connectChecker: ConnectChecker,
) : CameraInterface {
    override val camera = RtmpCamera2(context, connectChecker)
    override val stream = RtmpStream(context, connectChecker)

    override val isStreaming: Boolean get() = stream.isStreaming
    override val isRecording: Boolean get() = stream.isRecording
    override val isOnPreview: Boolean get() = stream.isOnPreview
    override val bitrate: Int get() = camera.bitrate
    override val glInterface: GlInterface get() = camera.glInterface

    override fun prepareAudio(
        audioSource: Int,
        bitrate: Int,
        sampleRate: Int,
        isStereo: Boolean,
        echoCanceler: Boolean,
        noiseSuppressor: Boolean,
    ): Boolean =
        stream.prepareAudio(
            sampleRate,
            isStereo,
            bitrate,
            echoCanceler,
            noiseSuppressor
        )

    override fun prepareVideo(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        iFrameInterval: Int,
        rotation: Int,
    ) = stream.prepareVideo(
        width = width,
        height = height,
        fps = fps,
        bitrate = bitrate,
        iFrameInterval = iFrameInterval,
        rotation = rotation
    )

    override fun startStream(url: String) = stream.startStream(url)

    override fun stopStream() = stream.stopStream()

    override fun startRecord(
        filePath: String,
        listener: RecordController.Listener,
    ) = stream.startRecord(filePath, listener)

    override fun stopRecord() = stream.stopRecord()

    override fun startPreview(
        view: OpenGlView,
        autoHandle: Boolean,
    ) = stream.startPreview(view, autoHandle)

    override fun stopPreview() = stream.stopPreview()

    override fun replaceView(view: OpenGlView) = camera.replaceView(view)

    override fun switchCamera() = camera.switchCamera()

    override fun setVideoBitrateOnFly(bitrate: Int) = camera.setVideoBitrateOnFly(bitrate)

    override fun enableAudio() = camera.enableAudio()

    override fun disableAudio() = camera.disableAudio()

    override fun hasCongestion(): Boolean = camera.getStreamClient().hasCongestion()

    override fun enableLantern() = camera.enableLantern()

    override fun disableLantern() = camera.disableLantern()

    override fun setAuthorization(
        username: String,
        password: String,
    ) {
        camera.getStreamClient().setAuthorization(username, password)
    }

    override fun setProtocol(tcp: Boolean) {
        // Not applicable for RTMP
    }

    override fun setAudioCodec(codec: AudioCodec) = camera.setAudioCodec(codec)
}

class RtspCameraImpl(
    context: Context,
    connectChecker: ConnectChecker,
) : CameraInterface {
    override val camera = RtspCamera2(context, connectChecker)
    override val stream = RtspStream(context, connectChecker)
    override val isStreaming: Boolean get() = stream.isStreaming
    override val isRecording: Boolean get() = stream.isRecording
    override val isOnPreview: Boolean get() = stream.isOnPreview
    override val bitrate: Int get() = camera.bitrate
    override val glInterface: GlInterface get() = camera.glInterface

    override fun prepareAudio(
        audioSource: Int,
        bitrate: Int,
        sampleRate: Int,
        isStereo: Boolean,
        echoCanceler: Boolean,
        noiseSuppressor: Boolean,
    ): Boolean =
        camera.prepareAudio(
            audioSource,
            bitrate,
            sampleRate,
            isStereo,
            echoCanceler,
            noiseSuppressor
        )

    override fun prepareVideo(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        iFrameInterval: Int,
        rotation: Int,
    ) = stream.prepareVideo(
        width = width,
        height = height,
        fps = fps,
        bitrate = bitrate,
        iFrameInterval = iFrameInterval,
        rotation = rotation
    )

    override fun startStream(url: String) = stream.startStream(url)

    override fun stopStream() = stream.stopStream()

    override fun startRecord(
        filePath: String,
        listener: RecordController.Listener,
    ) = stream.startRecord(filePath, listener)

    override fun stopRecord() = stream.stopRecord()

    override fun startPreview(
        view: OpenGlView,
        autoHandle: Boolean,
    ) = stream.startPreview(view, autoHandle)

    override fun stopPreview() = stream.stopPreview()

    override fun replaceView(view: OpenGlView) = camera.replaceView(view)

    override fun switchCamera() = camera.switchCamera()

    override fun setVideoBitrateOnFly(bitrate: Int) = camera.setVideoBitrateOnFly(bitrate)

    override fun enableAudio() = camera.enableAudio()

    override fun disableAudio() = camera.disableAudio()

    override fun hasCongestion(): Boolean = camera.getStreamClient().hasCongestion()

    override fun enableLantern() = camera.enableLantern()

    override fun disableLantern() = camera.disableLantern()

    override fun setAuthorization(
        username: String,
        password: String,
    ) {
        camera.getStreamClient().setAuthorization(username, password)
    }

    override fun setProtocol(tcp: Boolean) {
        camera.getStreamClient().setProtocol(if (tcp) Protocol.TCP else Protocol.UDP)
    }

    override fun setAudioCodec(codec: AudioCodec) = camera.setAudioCodec(codec)
}

class SrtCameraImpl(
    context: Context,
    connectChecker: ConnectChecker,
) : CameraInterface {
    override val camera = SrtCamera2(context, connectChecker)
    override val stream = SrtStream(context, connectChecker)

    override val isStreaming: Boolean get() = stream.isStreaming
    override val isRecording: Boolean get() = stream.isRecording
    override val isOnPreview: Boolean get() = stream.isOnPreview
    override val bitrate: Int get() = camera.bitrate
    override val glInterface: GlInterface get() = camera.glInterface

    override fun prepareAudio(
        audioSource: Int,
        bitrate: Int,
        sampleRate: Int,
        isStereo: Boolean,
        echoCanceler: Boolean,
        noiseSuppressor: Boolean,
    ): Boolean =
        camera.prepareAudio(
            audioSource,
            bitrate,
            sampleRate,
            isStereo,
            echoCanceler,
            noiseSuppressor
        )

    override fun prepareVideo(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        iFrameInterval: Int,
        rotation: Int,
    ) = stream.prepareVideo(
        width = width,
        height = height,
        fps = fps,
        bitrate = bitrate,
        iFrameInterval = iFrameInterval,
        rotation = rotation
    )

    override fun startStream(url: String) = stream.startStream(url)

    override fun stopStream() = stream.stopStream()

    override fun startRecord(
        filePath: String,
        listener: RecordController.Listener,
    ) = stream.startRecord(filePath, listener)

    override fun stopRecord() = stream.stopRecord()

    override fun startPreview(
        view: OpenGlView,
        autoHandle: Boolean,
    ) = stream.startPreview(view, autoHandle)

    override fun stopPreview() = stream.stopPreview()

    override fun replaceView(view: OpenGlView) = camera.replaceView(view)

    override fun switchCamera() = camera.switchCamera()

    override fun setVideoBitrateOnFly(bitrate: Int) = camera.setVideoBitrateOnFly(bitrate)

    override fun enableAudio() = camera.enableAudio()

    override fun disableAudio() = camera.disableAudio()

    override fun hasCongestion(): Boolean = camera.getStreamClient().hasCongestion()

    override fun enableLantern() = camera.enableLantern()

    override fun disableLantern() = camera.disableLantern()

    override fun setAuthorization(
        username: String,
        password: String,
    ) {
        // Not applicable for SRT
    }

    override fun setProtocol(tcp: Boolean) {
        // Not applicable for SRT
    }

    override fun setAudioCodec(codec: AudioCodec) = camera.setAudioCodec(codec)
}

class UdpCameraImpl(
    context: Context,
    connectChecker: ConnectChecker,
) : CameraInterface {
    override val camera = UdpCamera2(context, connectChecker)
    override val stream = UdpStream(context, connectChecker)

    override val isStreaming: Boolean get() = stream.isStreaming
    override val isRecording: Boolean get() = stream.isRecording
    override val isOnPreview: Boolean get() = stream.isOnPreview
    override val bitrate: Int get() = camera.bitrate
    override val glInterface: GlInterface get() = camera.glInterface

    override fun prepareAudio(
        audioSource: Int,
        bitrate: Int,
        sampleRate: Int,
        isStereo: Boolean,
        echoCanceler: Boolean,
        noiseSuppressor: Boolean,
    ): Boolean =
        camera.prepareAudio(
            audioSource,
            bitrate,
            sampleRate,
            isStereo,
            echoCanceler,
            noiseSuppressor
        )

    override fun prepareVideo(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        iFrameInterval: Int,
        rotation: Int,
    ) = stream.prepareVideo(
        width = width,
        height = height,
        fps = fps,
        bitrate = bitrate,
        iFrameInterval = iFrameInterval,
        rotation = rotation
    )

    override fun startStream(url: String) = stream.startStream(url)

    override fun stopStream() = stream.stopStream()

    override fun startRecord(
        filePath: String,
        listener: RecordController.Listener,
    ) = stream.startRecord(filePath, listener)

    override fun stopRecord() = stream.stopRecord()

    override fun startPreview(
        view: OpenGlView,
        autoHandle: Boolean,
    ) = stream.startPreview(view, autoHandle)

    override fun stopPreview() = stream.stopPreview()

    override fun replaceView(view: OpenGlView) = camera.replaceView(view)

    override fun switchCamera() = camera.switchCamera()

    override fun setVideoBitrateOnFly(bitrate: Int) = camera.setVideoBitrateOnFly(bitrate)

    override fun enableAudio() = camera.enableAudio()

    override fun disableAudio() = camera.disableAudio()

    override fun hasCongestion(): Boolean = camera.getStreamClient().hasCongestion()

    override fun enableLantern() = camera.enableLantern()

    override fun disableLantern() = camera.disableLantern()

    override fun setAuthorization(
        username: String,
        password: String,
    ) {
        // Not applicable for UDP
    }

    override fun setProtocol(tcp: Boolean) {
        // Not applicable for UDP
    }

    override fun setAudioCodec(codec: AudioCodec) = camera.setAudioCodec(codec)
}
