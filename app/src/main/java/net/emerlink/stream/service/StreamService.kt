@file:Suppress("ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import android.view.MotionEvent
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.util.BitrateAdapter
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.R
import net.emerlink.stream.core.AppIntentActions
import net.emerlink.stream.core.ErrorHandler
import net.emerlink.stream.core.notification.AppNotificationManager
import net.emerlink.stream.data.model.ConnectionSettings
import net.emerlink.stream.data.model.Resolution
import net.emerlink.stream.data.model.StreamInfo
import net.emerlink.stream.data.model.StreamType
import net.emerlink.stream.data.repository.ConnectionProfileRepository
import net.emerlink.stream.data.repository.SettingsRepository
import net.emerlink.stream.service.media.MediaManager
import net.emerlink.stream.service.media.RecordingListener
import net.emerlink.stream.service.microphone.MicrophoneMonitor
import net.emerlink.stream.service.stream.StreamInterface
import org.koin.java.KoinJavaComponent.inject
import kotlin.math.log10

class StreamService :
    Service(),
    ConnectChecker,
    SensorEventListener {
    companion object {
        private const val TAG = "StreamService"
        private const val AUDIO_LEVEL_UPDATE_INTERVAL = 100L

        val observer = MutableLiveData<StreamService?>()
    }

    private val settingsRepository: SettingsRepository by inject(SettingsRepository::class.java)
    private val connectionRepository: ConnectionProfileRepository by inject(ConnectionProfileRepository::class.java)

    private lateinit var notificationManager: AppNotificationManager
    private lateinit var errorHandler: ErrorHandler
    private lateinit var mediaManager: MediaManager
    private lateinit var connectionSettings: ConnectionSettings
    private lateinit var sensorManager: SensorManager
    private lateinit var magnetometer: android.hardware.Sensor
    private lateinit var accelerometer: android.hardware.Sensor
    private lateinit var microphoneMonitor: MicrophoneMonitor
    private lateinit var streamInterface: StreamInterface

    private var bitrateAdapter: BitrateAdapter? = null
    private var exiting = false
    private var currentCameraId = 0
    private var openGlView: OpenGlView? = null
    private var isPreviewActive = false
    private var audioLevelUpdateHandler: Handler? = null
    private var audioLevelRunnable: Runnable? = null
    private var isRunningAudioLevelUpdates = false
    private var streamType: StreamType = StreamType.RTMP
    private val cameraIds = ArrayList<String>()
    private var lanternEnabled = false

    private val binder = LocalBinder()
    private val gravityData = FloatArray(3)
    private val geomagneticData = FloatArray(3)

    private var hasGravityData = false
    private var hasGeomagneticData = false
    private var rotationInDegrees: Double = 0.0

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val action = intent.action
                if (action != null) {
                    when (action) {
                        AppIntentActions.START_STREAM -> startStream()
                        AppIntentActions.STOP_STREAM -> stopStream(null)

                        AppIntentActions.EXIT_APP -> {
                            exiting = true
                            stopStream(null)
                            LocalBroadcastManager
                                .getInstance(context)
                                .sendBroadcast(Intent(AppIntentActions.FINISH_ACTIVITY))
                            stopSelf()
                        }
                    }
                }
            }
        }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        observer.postValue(this)

        // Initialize notification manager first
        notificationManager = AppNotificationManager.getInstance(this)

        // Start foreground immediately with basic notification
        startForegroundService()

        initDependencies()
        initSensors()

        // Register broadcast receiver for commands
        val filter =
            IntentFilter().apply {
                addAction(AppIntentActions.START_STREAM)
                addAction(AppIntentActions.STOP_STREAM)
                addAction(AppIntentActions.EXIT_APP)
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        initAudioLevelUpdates()
    }

    private fun startForegroundService() {
        try {
            val notification =
                notificationManager.createNotification(
                    getString(R.string.ready_to_stream),
                    true
                )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    AppNotificationManager.START_STREAM_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(
                    AppNotificationManager.START_STREAM_NOTIFICATION_ID,
                    notification
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            val fallbackNotification =
                notificationManager.createNotification(
                    getString(R.string.ready_to_stream),
                    true
                )
            startForeground(AppNotificationManager.START_STREAM_NOTIFICATION_ID, fallbackNotification)
        }
    }

    private fun initDependencies() {
        errorHandler = ErrorHandler(this)
        connectionSettings = connectionRepository.activeProfileFlow.value?.settings ?: ConnectionSettings()
        streamType = connectionSettings.protocol
        streamInterface = StreamInterface.create(this, this, streamType)
        mediaManager = MediaManager(this, this, notificationManager)
        microphoneMonitor = MicrophoneMonitor()
        getCameraIds()
    }

    private fun initSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        magnetometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)!!
        accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)!!
    }

    private fun initAudioLevelUpdates() {
        audioLevelUpdateHandler = Handler(Looper.getMainLooper())
        audioLevelRunnable =
            object : Runnable {
                override fun run() {
                    if (isPreviewActive) {
                        val audioLevel = getAudioLevel()
                        broadcastAudioLevel(audioLevel)
                    }

                    if (isRunningAudioLevelUpdates) {
                        audioLevelUpdateHandler?.postDelayed(this, AUDIO_LEVEL_UPDATE_INTERVAL)
                    }
                }
            }
    }

    private fun getAudioLevel(): Float {
        try {
            val maxAmplitude = microphoneMonitor.getAudioLevel()

            val normalizedLevel =
                if (maxAmplitude > 0) {
                    val logLevel = log10(maxAmplitude.toDouble() / 32767.0) + 1
                    val level = logLevel.coerceIn(0.0, 1.0).toFloat()
                    level
                } else {
                    0.0f
                }

            return normalizedLevel
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio level", e)
            return 0f
        }
    }

    private fun broadcastAudioLevel(audioLevel: Float) {
        val intent = Intent(AppIntentActions.BROADCAST_AUDIO_LEVEL)
        intent.putExtra(AppIntentActions.EXTRA_AUDIO_LEVEL, audioLevel)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun startAudioLevelUpdates() {
        if (!isRunningAudioLevelUpdates) {
            isRunningAudioLevelUpdates = true
            audioLevelRunnable?.let {
                audioLevelUpdateHandler?.post(it)
            }
        }
    }

    private fun stopAudioLevelUpdates() {
        isRunningAudioLevelUpdates = false
        audioLevelRunnable?.let {
            audioLevelUpdateHandler?.removeCallbacks(it)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int = START_STICKY

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        observer.postValue(null)
        stopPreview()

        microphoneMonitor.stopMonitoring()
        stopAudioLevelUpdates()

        unregisterReceiver(receiver)

        super.onDestroy()
    }

    // ConnectChecker implementation
    override fun onAuthError() {
        stopStream(getString(R.string.auth_error))
    }

    override fun onAuthSuccess() {
        Log.d(TAG, "Auth success")
    }

    override fun onConnectionFailed(reason: String) {
        Log.e(TAG, "Connection failed: $reason")
        try {
            stopStream(getString(R.string.connection_failed))
        } catch (e: Exception) {
            Log.e(TAG, "Error handling connection failure", e)
            try {
                stopStream()
                notificationManager.showErrorSafely(this, "Critical error: ${e.message}")
            } catch (e2: Exception) {
                Log.e(TAG, "Double error", e2)
                notificationManager.showErrorSafely(this, "Critical error occurred")
                notifyStreamStopped()
            }
        }
    }

    private fun notifyStreamStarted() {
        Log.d(TAG, "Broadcasting stream started")
        LocalBroadcastManager
            .getInstance(this)
            .sendBroadcast(Intent(AppIntentActions.BROADCAST_STREAM_STARTED).setPackage(packageName))
    }

    private fun notifyStreamStopped() {
        Log.d(TAG, "Broadcasting stream stopped (Local)")
        LocalBroadcastManager
            .getInstance(this)
            .sendBroadcast(Intent(AppIntentActions.BROADCAST_STREAM_STOPPED).setPackage(packageName))
    }

    override fun onConnectionStarted(url: String) {}

    override fun onConnectionSuccess() {
        Log.d(TAG, "Connection success - notifying stream started")
        notifyStreamStarted()

        val videoSettings = settingsRepository.videoSettingsFlow.value
        if (videoSettings.adaptiveBitrate) {
            bitrateAdapter =
                BitrateAdapter { bitrate ->
                    streamInterface.setVideoBitrateOnFly(bitrate)
                }
            bitrateAdapter?.setMaxBitrate(videoSettings.bitrate * 1024)
        }
    }

    override fun onDisconnect() {}

    override fun onNewBitrate(bitrate: Long) {
        bitrateAdapter?.adaptBitrate(bitrate, streamInterface.hasCongestion())
        val intent = Intent(AppIntentActions.NEW_BITRATE)
        intent.putExtra(AppIntentActions.EXTRA_NEW_BITRATE, bitrate)
        applicationContext.sendBroadcast(intent)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            android.hardware.Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityData, 0, 3)
                hasGravityData = true
            }

            android.hardware.Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagneticData, 0, 3)
                hasGeomagneticData = true
            }

            else -> return
        }

        if (hasGravityData && hasGeomagneticData) {
            val identityMatrix = FloatArray(9)
            val rotationMatrix = FloatArray(9)
            val success =
                SensorManager.getRotationMatrix(
                    rotationMatrix,
                    identityMatrix,
                    gravityData,
                    geomagneticData
                )

            if (success) {
                val orientationMatrix = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientationMatrix)
                val rotationInRadians = orientationMatrix[0]
                rotationInDegrees = Math.toDegrees(rotationInRadians.toDouble())

                val screenOrientation = 0
                rotationInDegrees += screenOrientation
                if (rotationInDegrees < 0.0) {
                    rotationInDegrees += 360.0
                }
            }
        }
    }

    override fun onAccuracyChanged(
        sensor: android.hardware.Sensor?,
        accuracy: Int,
    ) {
    }

    fun startPreview(view: OpenGlView) {
        if (isPreviewActive) {
            return
        }

        try {
            refreshSettings()
            openGlView = view

            if (!isStreaming()) {
                prepareVideo()
                prepareAudio()
                streamInterface.startPreview(view, true)
            }

            isPreviewActive = true

            startAudioLevelUpdates()
            microphoneMonitor.startMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting preview", e)
        }
    }

    fun stopPreview() {
        if (!isPreviewActive) {
            return
        }

        try {
            if (isOnPreview() && !isStreaming()) {
                streamInterface.stopPreview()
            } else if (isStreaming()) {
                Log.d(TAG, "Not stopping preview because streaming is active")
            }
            isPreviewActive = false
            if (!isStreaming()) {
                stopAudioLevelUpdates()
            }
            microphoneMonitor.stopMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping preview", e)
        }
    }

    /**
     * Prepare audio for streaming with the current settings
     * @return true if successful, false otherwise
     */
    fun prepareAudio() {
        Log.d(TAG, "Подготовка аудио")

        val audioSettings = settingsRepository.audioSettingsFlow.value
        streamInterface.prepareAudio(
            bitrate = audioSettings.bitrate * 1024,
            sampleRate = audioSettings.sampleRate,
            isStereo = audioSettings.stereo
        )
        streamInterface.setAudioCodec(AudioCodec.AAC)
    }

    /**
     * Prepare video for streaming with the current settings
     * @return true if successful, false otherwise
     */
    fun prepareVideo() {
        Log.d(TAG, "Подготовка видео")

        val videoSettings = settingsRepository.videoSettingsFlow.value
        val resolution = Resolution.parseFromSize(videoSettings.resolution)
        val rotation = CameraHelper.getCameraOrientation(this)

        streamInterface.prepareVideo(
            width = resolution.width,
            height = resolution.height,
            fps = videoSettings.fps,
            bitrate = videoSettings.bitrate * 1000,
            iFrameInterval = videoSettings.keyframeInterval,
            rotation = rotation
        )
    }

    private fun switchStreamResolution() {
        try {
            if (isOnPreview()) {
                streamInterface.stopPreview()
                prepareVideo()
                openGlView?.let { view -> streamInterface.startPreview(view, true) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при изменении разрешения стрима: ${e.message}")
        }
    }

    fun isStreaming(): Boolean = streamInterface.isStreaming

    fun isRecording(): Boolean = streamInterface.isRecording

    fun isOnPreview(): Boolean = streamInterface.isOnPreview

    fun switchCamera(): Boolean {
        try {
            streamInterface.switchCamera()
            currentCameraId = if (currentCameraId == 0) 1 else 0
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error switching camera: ${e.message}", e)
            return false
        }
    }

    fun toggleLantern(): Boolean {
        try {
            lanternEnabled = !lanternEnabled

            if (lanternEnabled) {
                streamInterface.enableLantern()
            } else {
                streamInterface.disableLantern()
            }

            return lanternEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling lantern, trying fallback: ${e.message}", e)
            return false
        }
    }

    fun setZoom(motionEvent: MotionEvent) = streamInterface.setZoom(motionEvent)

    fun tapToFocus(motionEvent: MotionEvent) = streamInterface.tapToFocus(motionEvent)

    fun startRecord(filePath: String) {
        try {
            streamInterface.startRecord(filePath, RecordingListener())
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
        }
    }

    fun stopRecord() {
        if (isRecording()) {
            streamInterface.stopRecord()
        }
    }

    private fun refreshSettings() {
        updateStreamType()
        if (isPreviewActive && openGlView != null) {
            handleResolutionChange()
        }
    }

    private fun getCameraIds() {
        cameraIds.clear()
        cameraIds.addAll(streamInterface.getCameraIds())
    }

    fun handleResolutionChange() {
        openGlView?.let { view ->
            Log.d(TAG, "Перезапуск превью с новым разрешением")
            try {
                switchStreamResolution()
                startPreview(view)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при обновлении разрешения: ${e.message}", e)
            }
        }
    }

    fun isPreviewRunning(): Boolean = openGlView != null

    fun startStream() {
        if (isStreaming() || isRecording()) {
            Log.w(TAG, "Start stream called but already streaming or recording.")
            return
        }

        Log.d(TAG, "startStream called")
        val videoSettings = settingsRepository.videoSettingsFlow.value
        var streamAttempted = false
        var recordAttempted = false

        if (videoSettings.streamVideo) {
            Log.d(TAG, "Attempting to start streaming...")
            startStreaming()
            streamAttempted = true
        }
        if (videoSettings.recordVideo) {
            Log.d(TAG, "Attempting to start recording...")
            mediaManager.startRecording()
            recordAttempted = true
        }

        if (streamAttempted || recordAttempted) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)

            notificationManager.showNotification(getString(R.string.streaming), true)
        } else {
            Log.w(TAG, "Start stream called, but neither streamVideo nor recordVideo is enabled.")
        }
    }

    private fun startStreaming() {
        try {
            val streamUrl = connectionSettings.buildStreamUrl()
            Log.d(TAG, "Stream URL: ${streamUrl.replace(Regex(":[^:/]*:[^:/]*"), ":****:****")}")

            if (connectionSettings.protocol.toString().startsWith("rtsp")) {
                streamInterface.setProtocol(connectionSettings.tcp)
            }

            if (connectionSettings.username.isNotEmpty() &&
                connectionSettings.password.isNotEmpty() &&
                (
                    connectionSettings.protocol.toString().startsWith("rtmp") ||
                        connectionSettings.protocol.toString().startsWith("rtsp")
                )
            ) {
                streamInterface.setAuthorization(connectionSettings.username, connectionSettings.password)
            }

            streamInterface.startStream(streamUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting streaming", e)
            errorHandler.handleStreamError(e)
        }
    }

    fun stopStream(error: String? = null) {
        Log.d(TAG, "stopStream called with error: $error")

        var streamWasStopped = false
        var recordingWasStopped = false

        try {
            if (isStreaming()) {
                Log.d(TAG, "Stopping stream interface...")
                streamInterface.stopStream()
                streamWasStopped = true
            }

            if (isRecording()) {
                Log.d(TAG, "Stopping media manager recording...")
                mediaManager.stopRecording()
                recordingWasStopped = true
            }

            if (streamWasStopped || recordingWasStopped) {
                notifyStreamStopped()
            }

            try {
                sensorManager.unregisterListener(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering sensor listener", e)
            }

            if (error != null) {
                notificationManager.showErrorNotification(error)
                notificationManager.showNotification(getString(R.string.ready_to_stream), true)
            } else {
                notificationManager.showNotification(getString(R.string.ready_to_stream), true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopStream", e)
            notifyStreamStopped()

            val errorMsg = error?.let { "$it (${e.message})" } ?: e.message ?: "Unknown error during stop"
            notificationManager.showErrorSafely(this, errorMsg)
            notificationManager.showNotification(getString(R.string.ready_to_stream), true)
            errorHandler.handleStreamError(e)
        }
    }

    fun toggleMute(muted: Boolean) {
        try {
            if (muted) streamInterface.disableAudio() else streamInterface.enableAudio()
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mute state", e)
        }
    }

    fun getStreamInfo(): StreamInfo {
        connectionSettings = connectionRepository.activeProfileFlow.value?.settings ?: ConnectionSettings()
        val streamSettings = settingsRepository.videoSettingsFlow.value
        return StreamInfo(
            protocol = connectionSettings.protocol.toString(), 
            resolution = streamSettings.resolution.toString(),
            bitrate = "${streamSettings.bitrate} kbps",
            fps = "${streamSettings.fps} fps"
        )
    }

    private fun updateStreamType() {
        connectionSettings = connectionRepository.activeProfileFlow.value?.settings ?: ConnectionSettings()
        if (connectionSettings.protocol != streamType) {
            streamType = connectionSettings.protocol
            streamInterface = StreamInterface.create(this, this, streamType)
            getCameraIds() // Refresh camera IDs when changing interface
        }
    }

    fun getGlInterface() = streamInterface.glInterface

    fun takePhoto() = mediaManager.takePhoto()

    inner class LocalBinder : Binder() {
        fun getService(): StreamService = this@StreamService
    }
}
