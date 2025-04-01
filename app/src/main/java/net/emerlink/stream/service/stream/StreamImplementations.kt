package net.emerlink.stream.service.stream

import android.content.Context
import android.view.MotionEvent
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.library.base.recording.RecordController
import com.pedro.library.rtmp.RtmpStream
import com.pedro.library.rtsp.RtspStream
import com.pedro.library.srt.SrtStream
import com.pedro.library.udp.UdpStream
import com.pedro.library.view.GlInterface
import com.pedro.library.view.OpenGlView
import com.pedro.rtsp.rtsp.Protocol

class RtmpStreamImpl(
    context: Context,
    connectChecker: ConnectChecker,
) : StreamInterface {
    override val stream = RtmpStream(context, connectChecker)

    override val isStreaming: Boolean get() = stream.isStreaming
    override val isRecording: Boolean get() = stream.isRecording
    override val isOnPreview: Boolean get() = stream.isOnPreview

    override val glInterface: GlInterface get() = stream.getGlInterface()

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

    override fun switchCamera() {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.switchCamera()
    }

    override fun setVideoBitrateOnFly(bitrate: Int) = stream.setVideoBitrateOnFly(bitrate)

    override fun enableAudio() = stream.changeAudioSource(MicrophoneSource())

    override fun disableAudio() = stream.changeAudioSource(NoAudioSource())

    override fun hasCongestion(): Boolean = stream.getStreamClient().hasCongestion()

    override fun enableLantern() {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.enableLantern()
    }

    override fun disableLantern() {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.disableLantern()
    }

    override fun setAuthorization(
        username: String,
        password: String,
    ) {
        stream.getStreamClient().setAuthorization(username, password)
    }

    override fun setProtocol(tcp: Boolean) {
        // Not applicable for RTMP
    }

    override fun setAudioCodec(codec: AudioCodec) = stream.setAudioCodec(codec)

    override fun setZoom(motionEvent: MotionEvent) {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.setZoom(motionEvent)
    }

    override fun tapToFocus(motionEvent: MotionEvent) {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.tapToFocus(motionEvent)
    }
}

class RtspStreamImpl(
    context: Context,
    connectChecker: ConnectChecker,
) : StreamInterface {
    override val stream = RtspStream(context, connectChecker)

    override val isStreaming: Boolean get() = stream.isStreaming
    override val isRecording: Boolean get() = stream.isRecording
    override val isOnPreview: Boolean get() = stream.isOnPreview

    override val glInterface: GlInterface get() = stream.getGlInterface()

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

    override fun switchCamera() {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.switchCamera()
    }

    override fun setVideoBitrateOnFly(bitrate: Int) = stream.setVideoBitrateOnFly(bitrate)

    override fun enableAudio() = stream.changeAudioSource(MicrophoneSource())

    override fun disableAudio() = stream.changeAudioSource(NoAudioSource())

    override fun hasCongestion(): Boolean = stream.getStreamClient().hasCongestion()

    override fun enableLantern() {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.enableLantern()
    }

    override fun disableLantern() {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.disableLantern()
    }

    override fun setAuthorization(
        username: String,
        password: String,
    ) {
        stream.getStreamClient().setAuthorization(username, password)
    }

    override fun setProtocol(tcp: Boolean) {
        stream.getStreamClient().setProtocol(if (tcp) Protocol.TCP else Protocol.UDP)
    }

    override fun setAudioCodec(codec: AudioCodec) = stream.setAudioCodec(codec)

    override fun setZoom(motionEvent: MotionEvent) {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.setZoom(motionEvent)
    }

    override fun tapToFocus(motionEvent: MotionEvent) {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.tapToFocus(motionEvent)
    }
}

class SrtStreamImpl(
    context: Context,
    connectChecker: ConnectChecker,
) : StreamInterface {
    override val stream = SrtStream(context, connectChecker)

    override val isStreaming: Boolean get() = stream.isStreaming
    override val isRecording: Boolean get() = stream.isRecording
    override val isOnPreview: Boolean get() = stream.isOnPreview

    override val glInterface: GlInterface get() = stream.getGlInterface()

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

    override fun switchCamera() {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.switchCamera()
    }

    override fun setVideoBitrateOnFly(bitrate: Int) = stream.setVideoBitrateOnFly(bitrate)

    override fun enableAudio() = stream.changeAudioSource(MicrophoneSource())

    override fun disableAudio() = stream.changeAudioSource(NoAudioSource())

    override fun hasCongestion(): Boolean = stream.getStreamClient().hasCongestion()

    override fun enableLantern() {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.enableLantern()
    }

    override fun disableLantern() {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.disableLantern()
    }

    override fun setAuthorization(
        username: String,
        password: String,
    ) {
        // Not applicable for SRT
    }

    override fun setProtocol(tcp: Boolean) {
        // Not applicable for SRT
    }

    override fun setAudioCodec(codec: AudioCodec) = stream.setAudioCodec(codec)

    override fun setZoom(motionEvent: MotionEvent) {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.setZoom(motionEvent)
    }

    override fun tapToFocus(motionEvent: MotionEvent) {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.tapToFocus(motionEvent)
    }
}

class UdpStreamImpl(
    context: Context,
    connectChecker: ConnectChecker,
) : StreamInterface {
    override val stream = UdpStream(context, connectChecker)

    override val isStreaming: Boolean get() = stream.isStreaming
    override val isRecording: Boolean get() = stream.isRecording
    override val isOnPreview: Boolean get() = stream.isOnPreview

    override val glInterface: GlInterface get() = stream.getGlInterface()

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

    override fun switchCamera() {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.switchCamera()
    }

    override fun setVideoBitrateOnFly(bitrate: Int) = stream.setVideoBitrateOnFly(bitrate)

    override fun enableAudio() = stream.changeAudioSource(MicrophoneSource())

    override fun disableAudio() = stream.changeAudioSource(NoAudioSource())

    override fun hasCongestion(): Boolean = stream.getStreamClient().hasCongestion()

    override fun enableLantern() {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.enableLantern()
    }

    override fun disableLantern() {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.disableLantern()
    }

    override fun setAuthorization(
        username: String,
        password: String,
    ) {
        // Not applicable for UDP
    }

    override fun setProtocol(tcp: Boolean) {
        // Not applicable for UDP
    }

    override fun setAudioCodec(codec: AudioCodec) = stream.setAudioCodec(codec)

    override fun setZoom(motionEvent: MotionEvent) {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.setZoom(motionEvent)
    }

    override fun tapToFocus(motionEvent: MotionEvent) {
        val camera2Source = stream.videoSource as Camera2Source
        camera2Source.tapToFocus(motionEvent)
    }
}
