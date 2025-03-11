package net.emerlink.stream.service.camera

import android.content.Context
import android.util.Log
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
    private val context: Context,
    private val connectChecker: ConnectChecker
) : CameraInterface {
    companion object {
        private const val TAG = "RtmpCameraImpl"
    }

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
        rotation: Int
    ) =
        camera.prepareVideo(width, height, fps, bitrate, rotation)

    override fun startStream(url: String) = camera.startStream(url)
    override fun stopStream() = camera.stopStream()
    override fun startRecord(filePath: String) = camera.startRecord(filePath)
    override fun stopRecord() = camera.stopRecord()
    override fun startPreview(facing: CameraHelper.Facing, rotation: Int) =
        camera.startPreview(facing, rotation)

    override fun stopPreview() = camera.stopPreview()
    override fun replaceView(view: OpenGlView) = camera.replaceView(view)
    override fun switchCamera() = camera.switchCamera()
    override fun setVideoBitrateOnFly(bitrate: Int) = camera.setVideoBitrateOnFly(bitrate)
    override fun enableAudio() = camera.enableAudio()
    override fun disableAudio() = camera.disableAudio()
    override fun hasCongestion(): Boolean = camera.getStreamClient().hasCongestion()

    override fun enableLantern(): Boolean {
        return try {
            camera.enableLantern()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при включении фонарика: ${e.message}", e)
            false
        }
    }

    override fun disableLantern(): Boolean {
        return try {
            camera.disableLantern()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при выключении фонарика: ${e.message}", e)
            false
        }
    }

    override fun setAuthorization(username: String, password: String) {
        camera.getStreamClient().setAuthorization(username, password)
    }

    override fun setProtocol(tcp: Boolean) {
        // Не применимо для RTMP
    }
}

class RtspCameraImpl(
    private val context: Context,
    private val connectChecker: ConnectChecker
) : CameraInterface {
    companion object {
        private const val TAG = "RtspCameraImpl"
    }

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
        rotation: Int
    ) =
        camera.prepareVideo(width, height, fps, bitrate, rotation)

    override fun startStream(url: String) = camera.startStream(url)
    override fun stopStream() = camera.stopStream()
    override fun startRecord(filePath: String) = camera.startRecord(filePath)
    override fun stopRecord() = camera.stopRecord()
    override fun startPreview(facing: CameraHelper.Facing, rotation: Int) =
        camera.startPreview(facing, rotation)

    override fun stopPreview() = camera.stopPreview()
    override fun replaceView(view: OpenGlView) = camera.replaceView(view)
    override fun switchCamera() = camera.switchCamera()
    override fun setVideoBitrateOnFly(bitrate: Int) = camera.setVideoBitrateOnFly(bitrate)
    override fun enableAudio() = camera.enableAudio()
    override fun disableAudio() = camera.disableAudio()
    override fun hasCongestion(): Boolean = camera.getStreamClient().hasCongestion()

    override fun enableLantern(): Boolean {
        return try {
            camera.enableLantern()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при включении фонарика: ${e.message}", e)
            false
        }
    }

    override fun disableLantern(): Boolean {
        return try {
            camera.disableLantern()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при выключении фонарика: ${e.message}", e)
            false
        }
    }

    override fun setAuthorization(username: String, password: String) {
        camera.getStreamClient().setAuthorization(username, password)
    }

    override fun setProtocol(tcp: Boolean) {
        camera.getStreamClient().setProtocol(if (tcp) Protocol.TCP else Protocol.UDP)
    }
}

class SrtCameraImpl(
    private val context: Context,
    private val connectChecker: ConnectChecker
) : CameraInterface {
    companion object {
        private const val TAG = "SrtCameraImpl"
    }

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
        rotation: Int
    ) =
        camera.prepareVideo(width, height, fps, bitrate, rotation)

    override fun startStream(url: String) = camera.startStream(url)
    override fun stopStream() = camera.stopStream()
    override fun startRecord(filePath: String) = camera.startRecord(filePath)
    override fun stopRecord() = camera.stopRecord()
    override fun startPreview(facing: CameraHelper.Facing, rotation: Int) =
        camera.startPreview(facing, rotation)

    override fun stopPreview() = camera.stopPreview()
    override fun replaceView(view: OpenGlView) = camera.replaceView(view)
    override fun switchCamera() = camera.switchCamera()
    override fun setVideoBitrateOnFly(bitrate: Int) = camera.setVideoBitrateOnFly(bitrate)
    override fun enableAudio() = camera.enableAudio()
    override fun disableAudio() = camera.disableAudio()
    override fun hasCongestion(): Boolean = camera.getStreamClient().hasCongestion()

    override fun enableLantern(): Boolean {
        return try {
            camera.enableLantern()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при включении фонарика: ${e.message}", e)
            false
        }
    }

    override fun disableLantern(): Boolean {
        return try {
            camera.disableLantern()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при выключении фонарика: ${e.message}", e)
            false
        }
    }

    override fun setAuthorization(username: String, password: String) {
        // Не применимо для SRT
    }

    override fun setProtocol(tcp: Boolean) {
        // Не применимо для SRT
    }
}

class UdpCameraImpl(
    private val context: Context,
    private val connectChecker: ConnectChecker
) : CameraInterface {
    companion object {
        private const val TAG = "UdpCameraImpl"
    }

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
        rotation: Int
    ) =
        camera.prepareVideo(width, height, fps, bitrate, rotation)

    override fun startStream(url: String) = camera.startStream(url)
    override fun stopStream() = camera.stopStream()
    override fun startRecord(filePath: String) = camera.startRecord(filePath)
    override fun stopRecord() = camera.stopRecord()
    override fun startPreview(facing: CameraHelper.Facing, rotation: Int) =
        camera.startPreview(facing, rotation)

    override fun stopPreview() = camera.stopPreview()
    override fun replaceView(view: OpenGlView) = camera.replaceView(view)
    override fun switchCamera() = camera.switchCamera()
    override fun setVideoBitrateOnFly(bitrate: Int) = camera.setVideoBitrateOnFly(bitrate)
    override fun enableAudio() = camera.enableAudio()
    override fun disableAudio() = camera.disableAudio()
    override fun hasCongestion(): Boolean = camera.getStreamClient().hasCongestion()

    override fun enableLantern(): Boolean {
        return try {
            camera.enableLantern()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при включении фонарика: ${e.message}", e)
            false
        }
    }

    override fun disableLantern(): Boolean {
        return try {
            camera.disableLantern()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при выключении фонарика: ${e.message}", e)
            false
        }
    }

    override fun setAuthorization(username: String, password: String) {
        // Не применимо для UDP
    }

    override fun setProtocol(tcp: Boolean) {
        // Не применимо для UDP
    }
} 