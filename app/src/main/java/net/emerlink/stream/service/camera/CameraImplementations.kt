package net.emerlink.stream.service.camera

import android.content.Context
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.rtsp.RtspCamera2
import com.pedro.library.srt.SrtCamera2
import com.pedro.library.udp.UdpCamera2
import com.pedro.library.view.GlInterface
import com.pedro.library.view.OpenGlView
import com.pedro.rtsp.rtsp.Protocol

class RtmpCameraImpl(
    context: Context,
    connectChecker: ConnectChecker,
) : CameraInterface {
    override val camera = RtmpCamera2(context, connectChecker)

    override val isStreaming: Boolean get() = camera.isStreaming
    override val isRecording: Boolean get() = camera.isRecording
    override val isOnPreview: Boolean get() = camera.isOnPreview
    override val bitrate: Int get() = camera.bitrate
    override val glInterface: GlInterface get() = camera.glInterface

    override fun prepareAudio() = camera.prepareAudio()

    override fun prepareVideo(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        iFrameInterval: Int,
        rotation: Int,
    ) = camera.prepareVideo(
        width,
        height,
        fps,
        bitrate,
        iFrameInterval,
        rotation
    )

    override fun startStream(url: String) = camera.startStream(url)

    override fun stopStream() = camera.stopStream()

    override fun startRecord(filePath: String) = camera.startRecord(filePath)

    override fun stopRecord() = camera.stopRecord()

    override fun startPreview(
        facing: CameraHelper.Facing,
        rotation: Int,
    ) = camera.startPreview(facing, rotation)

    override fun stopPreview() = camera.stopPreview()

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
}

class RtspCameraImpl(
    context: Context,
    connectChecker: ConnectChecker,
) : CameraInterface {
    override val camera = RtspCamera2(context, connectChecker)

    override val isStreaming: Boolean get() = camera.isStreaming
    override val isRecording: Boolean get() = camera.isRecording
    override val isOnPreview: Boolean get() = camera.isOnPreview
    override val bitrate: Int get() = camera.bitrate
    override val glInterface: GlInterface get() = camera.glInterface

    override fun prepareAudio() = camera.prepareAudio()

    override fun prepareVideo(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        iFrameInterval: Int,
        rotation: Int,
    ) = camera.prepareVideo(
        width,
        height,
        fps,
        bitrate,
        iFrameInterval,
        rotation
    )

    override fun startStream(url: String) = camera.startStream(url)

    override fun stopStream() = camera.stopStream()

    override fun startRecord(filePath: String) = camera.startRecord(filePath)

    override fun stopRecord() = camera.stopRecord()

    override fun startPreview(
        facing: CameraHelper.Facing,
        rotation: Int,
    ) = camera.startPreview(facing, rotation)

    override fun stopPreview() = camera.stopPreview()

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
}

class SrtCameraImpl(
    context: Context,
    connectChecker: ConnectChecker,
) : CameraInterface {
    override val camera = SrtCamera2(context, connectChecker)

    override val isStreaming: Boolean get() = camera.isStreaming
    override val isRecording: Boolean get() = camera.isRecording
    override val isOnPreview: Boolean get() = camera.isOnPreview
    override val bitrate: Int get() = camera.bitrate
    override val glInterface: GlInterface get() = camera.glInterface

    override fun prepareAudio() = camera.prepareAudio()

    override fun prepareVideo(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        iFrameInterval: Int,
        rotation: Int,
    ) = camera.prepareVideo(
        width,
        height,
        fps,
        bitrate,
        iFrameInterval,
        rotation
    )

    override fun startStream(url: String) = camera.startStream(url)

    override fun stopStream() = camera.stopStream()

    override fun startRecord(filePath: String) = camera.startRecord(filePath)

    override fun stopRecord() = camera.stopRecord()

    override fun startPreview(
        facing: CameraHelper.Facing,
        rotation: Int,
    ) = camera.startPreview(facing, rotation)

    override fun stopPreview() = camera.stopPreview()

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
}

class UdpCameraImpl(
    context: Context,
    connectChecker: ConnectChecker,
) : CameraInterface {
    override val camera = UdpCamera2(context, connectChecker)

    override val isStreaming: Boolean get() = camera.isStreaming
    override val isRecording: Boolean get() = camera.isRecording
    override val isOnPreview: Boolean get() = camera.isOnPreview
    override val bitrate: Int get() = camera.bitrate
    override val glInterface: GlInterface get() = camera.glInterface

    override fun prepareAudio() = camera.prepareAudio()

    override fun prepareVideo(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        iFrameInterval: Int,
        rotation: Int,
    ) = camera.prepareVideo(
        width,
        height,
        fps,
        bitrate,
        iFrameInterval,
        rotation
    )

    override fun startStream(url: String) = camera.startStream(url)

    override fun stopStream() = camera.stopStream()

    override fun startRecord(filePath: String) = camera.startRecord(filePath)

    override fun stopRecord() = camera.stopRecord()

    override fun startPreview(
        facing: CameraHelper.Facing,
        rotation: Int,
    ) = camera.startPreview(facing, rotation)

    override fun stopPreview() = camera.stopPreview()

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
}
