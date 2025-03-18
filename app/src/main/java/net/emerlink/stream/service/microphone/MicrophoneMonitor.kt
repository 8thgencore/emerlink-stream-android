package net.emerlink.stream.service.microphone

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MicrophoneMonitor {
    private var audioRecord: AudioRecord? = null
    private val minBufferSize =
        AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ) * 2

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startMonitoring() {
        audioRecord =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize
            )
        audioRecord?.startRecording()

        val handler = Handler(Looper.getMainLooper())
        handler.post(
            object : Runnable {
                override fun run() {
                    handler.postDelayed(this, POLL_INTERVAL)
                }
            }
        )
    }

    fun stopMonitoring() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    internal fun getAudioLevel(): Int {
        val buffer = ByteArray(minBufferSize)
        audioRecord?.read(buffer, 0, minBufferSize)

        val shortArray = ShortArray(minBufferSize / 2)
        ByteBuffer
            .wrap(buffer)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(shortArray)

        return shortArray.maxOrNull()?.toInt() ?: 0
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val POLL_INTERVAL = 100L
    }
}
