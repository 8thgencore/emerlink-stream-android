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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pedro.common.ConnectChecker
import com.pedro.library.util.BitrateAdapter
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.R
import net.emerlink.stream.core.AppIntentActions
import net.emerlink.stream.core.ErrorHandler
import net.emerlink.stream.core.notification.NotificationManager
import net.emerlink.stream.data.model.ConnectionSettings
import net.emerlink.stream.data.model.StreamInfo
import net.emerlink.stream.data.repository.ConnectionProfileRepository
import net.emerlink.stream.data.repository.SettingsRepository
import net.emerlink.stream.service.media.MediaManager
import net.emerlink.stream.service.microphone.MicrophoneMonitor
import net.emerlink.stream.service.stream.StreamManager
import org.koin.java.KoinJavaComponent.inject
import kotlin.math.log10

class StreamService : Service(), ConnectChecker, SensorEventListener {
    companion object {
        private const val TAG = "StreamService"
        private const val AUDIO_LEVEL_UPDATE_INTERVAL = 100L // 200ms update interval for audio level
    }

    private val settingsRepository: SettingsRepository by inject(SettingsRepository::class.java)
    private val connectionRepository: ConnectionProfileRepository by inject(ConnectionProfileRepository::class.java)

    private lateinit var notificationManager: NotificationManager
    private lateinit var errorHandler: ErrorHandler
    private lateinit var streamManager: StreamManager
    private lateinit var mediaManager: MediaManager
    private lateinit var connectionSettings: ConnectionSettings
    private lateinit var sensorManager: SensorManager
    private lateinit var magnetometer: android.hardware.Sensor
    private lateinit var accelerometer: android.hardware.Sensor
    private lateinit var microphoneMonitor: MicrophoneMonitor

    private var bitrateAdapter: BitrateAdapter? = null
    private var exiting = false
    private var currentCameraId = 0
    private var openGlView: OpenGlView? = null
    private var commandReceiver: BroadcastReceiver? = null
    private var audioLevelUpdateHandler: Handler? = null
    private var audioLevelRunnable: Runnable? = null
    private var isRunningAudioLevelUpdates = false

    private val binder = LocalBinder()
    private val gravityData = FloatArray(3)
    private val geomagneticData = FloatArray(3)

    private var hasGravityData = false
    private var hasGeomagneticData = false
    private var rotationInDegrees: Double = 0.0

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        initDependencies()
        initSensors()
        registerCommandReceiver()
        initForeground()
        initAudioLevelUpdates()

        Intent(this, StreamService::class.java).also { startService(it) }
    }

    private fun initDependencies() {
        errorHandler = ErrorHandler(this)
        notificationManager = NotificationManager.getInstance(this)
        connectionSettings = connectionRepository.activeProfileFlow.value?.settings ?: ConnectionSettings()
        streamManager = StreamManager(this, this, errorHandler, settingsRepository)
        streamManager.setStreamType(connectionSettings.protocol)
        mediaManager = MediaManager(this, streamManager, notificationManager)
        microphoneMonitor = MicrophoneMonitor()
    }

    private fun initSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        magnetometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)!!
        accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)!!
    }

    private fun initForeground() {
        val notifyId = NotificationManager.START_STREAM_NOTIFICATION_ID
        val notification = notificationManager.createNotification(
            getString(R.string.ready_to_stream), true, NotificationManager.ACTION_STOP_ONLY
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var types = 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }

            if (types != 0) {
                startForeground(notifyId, notification, types)
                Log.d(TAG, "Started foreground service with types: $types")
            } else {
                startForeground(notifyId, notification)
                Log.d(TAG, "Started foreground service without specific types")
            }
        } else {
            startForeground(notifyId, notification)
            Log.d(TAG, "Started legacy foreground service")
        }
    }

    private fun initAudioLevelUpdates() {
        audioLevelUpdateHandler = Handler(Looper.getMainLooper())
        audioLevelRunnable = object : Runnable {
            override fun run() {
                val audioLevel = getAudioLevel()
                broadcastAudioLevel(audioLevel)

                if (isRunningAudioLevelUpdates) {
                    audioLevelUpdateHandler?.postDelayed(this, AUDIO_LEVEL_UPDATE_INTERVAL)
                }
            }
        }
    }

    /**
     * Get the current audio level (0.0f - 1.0f)
     */
    private fun getAudioLevel(): Float {
        try {
            val maxAmplitude = microphoneMonitor.getAudioLevel()

            val normalizedLevel = if (maxAmplitude > 0) {
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

    /**
     * Broadcast audio level to the UI
     */
    private fun broadcastAudioLevel(audioLevel: Float) {
        val intent = Intent(AppIntentActions.BROADCAST_AUDIO_LEVEL)
        intent.putExtra(AppIntentActions.EXTRA_AUDIO_LEVEL, audioLevel)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /**
     * Start audio level updates
     */
    private fun startAudioLevelUpdates() {
        if (!isRunningAudioLevelUpdates) {
            isRunningAudioLevelUpdates = true
            audioLevelRunnable?.let {
                audioLevelUpdateHandler?.post(it)
            }
        }
    }

    /**
     * Stop audio level updates
     */
    private fun stopAudioLevelUpdates() {
        isRunningAudioLevelUpdates = false
        audioLevelRunnable?.let {
            audioLevelUpdateHandler?.removeCallbacks(it)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerCommandReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(AppIntentActions.ACTION_START_STREAM)
            addAction(AppIntentActions.ACTION_STOP_STREAM)
            addAction(AppIntentActions.ACTION_EXIT_APP)
            addAction(AppIntentActions.ACTION_DISMISS_ERROR)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                Log.d(TAG, "Received intent: ${intent.action}")
                try {
                    when (intent.action) {
                        AppIntentActions.ACTION_START_STREAM -> startStream()

                        AppIntentActions.ACTION_STOP_STREAM -> stopStream(null, null)

                        AppIntentActions.ACTION_EXIT_APP -> {
                            exiting = true
                            stopStream(null, null)
                            notificationManager.clearAllNotifications()
                            stopSelf()
                        }

                        AppIntentActions.ACTION_DISMISS_ERROR -> notificationManager.clearErrorNotifications()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing intent in BroadcastReceiver", e)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, intentFilter)
        }

        this.commandReceiver = receiver
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        intent?.action?.let { action ->
            try {
                when (action) {
                    AppIntentActions.ACTION_START_STREAM -> startStream()
                    AppIntentActions.ACTION_STOP_STREAM -> stopStream(null, null)

                    AppIntentActions.ACTION_EXIT_APP -> {
                        exiting = true
                        stopStream(null, null)
                        notificationManager.clearAllNotifications()
                        stopSelf()
                    }

                    AppIntentActions.ACTION_DISMISS_ERROR -> {
                        notificationManager.clearErrorNotifications()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                errorHandler.handleStreamError(e)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        try {
            Log.d(TAG, "onDestroy")

            // Only stop streaming if the app is explicitly exiting
            if (exiting && streamManager.isStreaming()) {
                stopStream(null, null)
            }

            if (commandReceiver != null) {
                unregisterReceiver(commandReceiver)
                commandReceiver = null
            }

            stopAudioLevelUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying service", e)
        }
        super.onDestroy()
    }

    // ConnectChecker implementation
    override fun onAuthError() {
        stopStream(getString(R.string.auth_error), AppIntentActions.ACTION_AUTH_ERROR, true)
    }

    override fun onAuthSuccess() {
        Log.d(TAG, "Auth success")
    }

    override fun onConnectionFailed(reason: String) {
        Log.e(TAG, "Connection failed: $reason")
        try {
            if (streamManager.isStreaming()) {
                streamManager.stopStream()

                val errorText = when {
                    reason.contains("461") -> getString(R.string.error_unsupported_transport)
                    else -> getString(R.string.connection_failed) + ": " + reason
                }

                notificationManager.showErrorSafely(this, errorText)
                applicationContext.sendBroadcast(Intent(AppIntentActions.ACTION_CONNECTION_FAILED))
                Handler(Looper.getMainLooper()).postDelayed(
                    { stopStream(null, null, false) }, 500
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling connection failure", e)
            try {
                streamManager.stopStream()


                notificationManager.showErrorSafely(this, "Critical error: ${e.message}")
            } catch (e2: Exception) {
                Log.e(TAG, "Double error", e2)
            }
        }
    }

    override fun onConnectionStarted(url: String) {}

    override fun onConnectionSuccess() {
        val videoSettings = settingsRepository.videoSettingsFlow.value
        if (videoSettings.adaptiveBitrate) {
            bitrateAdapter = BitrateAdapter { bitrate ->
                streamManager.setVideoBitrateOnFly(bitrate)
            }
            bitrateAdapter?.setMaxBitrate(videoSettings.bitrate * 1024)
        }
    }

    override fun onDisconnect() {}

    override fun onNewBitrate(bitrate: Long) {
        bitrateAdapter?.adaptBitrate(bitrate, streamManager.hasCongestion())
        val intent = Intent(AppIntentActions.ACTION_NEW_BITRATE)
        intent.putExtra(AppIntentActions.ACTION_NEW_BITRATE, bitrate)
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
            val success = SensorManager.getRotationMatrix(
                rotationMatrix, identityMatrix, gravityData, geomagneticData
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
        try {
            Log.d(TAG, "Starting preview")
            refreshSettings()
            openGlView = view

            val isCurrentlyStreaming = streamManager.isStreaming()
            if (!isCurrentlyStreaming) {
                streamManager.prepareVideo()
            }

            streamManager.startPreview(view)
            startAudioLevelUpdates()
            microphoneMonitor.startMonitoring()

            Log.d(TAG, "Preview started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting preview", e)
            openGlView = null
        }
    }

    fun stopPreview() {
        try {
            val isCurrentlyStreaming = streamManager.isStreaming()
            Log.d(TAG, "Stopping preview, streaming=$isCurrentlyStreaming")

            if (!isCurrentlyStreaming) {
                streamManager.stopPreview()
                stopAudioLevelUpdates()
                microphoneMonitor.stopMonitoring()
            } else {
                Log.d(TAG, "Keeping preview fully active because streaming is active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping preview", e)
        }
    }

    fun toggleLantern(): Boolean = streamManager.toggleLantern()

    fun switchCamera(): Boolean {
        try {
            val result = streamManager.switchCamera()
            if (result) {
                currentCameraId = streamManager.getCurrentCameraId()
            }
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error switching camera", e)
            return false
        }
    }

    fun setZoom(motionEvent: MotionEvent) = streamManager.setZoom(motionEvent)

    fun tapToFocus(motionEvent: MotionEvent) = streamManager.tapToFocus(motionEvent)

    fun takePhoto() = mediaManager.takePhoto()

    fun startStream() {
        try {
            val audioInitialized = streamManager.prepareAudio()
            val videoInitialized = streamManager.prepareVideo()

            if (!audioInitialized) {
                notificationManager.showErrorSafely(
                    this, getString(R.string.failed_to_prepare_audio)
                )
            }

            if (!videoInitialized) {
                notificationManager.showErrorSafely(
                    this, getString(R.string.failed_to_prepare)
                )
                return
            }

            val videoSettings = settingsRepository.videoSettingsFlow.value

            val notification = notificationManager.createNotification(
                getString(R.string.streaming), true, NotificationManager.ACTION_STOP_ONLY
            )

            val notifyId = NotificationManager.START_STREAM_NOTIFICATION_ID
            notificationManager.notificationManager.notify(notifyId, notification)

            if (videoSettings.streamVideo) {
                startStreaming()
            }
            if (videoSettings.recordVideo) {
                mediaManager.startRecording()
            }

            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)

            Log.d(TAG, "Stream started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in startStream", e)
            errorHandler.handleStreamError(e)
        }
    }

    private fun startStreaming() {
        try {
            val streamUrl = connectionSettings.buildStreamUrl()
            Log.d(TAG, "Stream URL: ${streamUrl.replace(Regex(":[^:/]*:[^:/]*"), ":****:****")}")

            streamManager.startStream(
                streamUrl,
                connectionSettings.protocol.toString(),
                connectionSettings.username,
                connectionSettings.password,
                connectionSettings.tcp
            )

            Log.d(TAG, "Streaming started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting streaming", e)
            errorHandler.handleStreamError(e)
        }
    }

    fun stopStream(
        message: String? = null,
        action: String? = null,
        isError: Boolean = false,
    ) {
        try {
            // Use the safe method to clear streaming notifications
            notificationManager.clearStreamingNotificationsSafely(this)

            if (streamManager.isStreaming()) {
                streamManager.stopStream()
            }

            if (streamManager.isRecording()) {
                mediaManager.stopRecording()
            }

            try {
                sensorManager.unregisterListener(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering sensor listener", e)
            }

            when {
                message != null && isError -> notificationManager.showErrorSafely(this, message)
                message != null -> notificationManager.showStreamingNotification(message, false)
            }

            action?.let {
                applicationContext.sendBroadcast(Intent(it))
            }

            if (exiting) {
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopStream", e)

            val errorMsg = message?.let { "$it (${e.message})" } ?: e.message ?: "Unknown error"
            notificationManager.showErrorSafely(this, errorMsg)
            errorHandler.handleStreamError(e)
        }
    }

    fun toggleMute(muted: Boolean) {
        if (muted) streamManager.disableAudio() else streamManager.enableAudio()
    }

    fun releaseCamera() {
        try {
            Log.d(TAG, "Releasing camera resources")

            if (!streamManager.isStreaming()) {
                if (streamManager.isOnPreview()) {
                    streamManager.stopPreview()
                }
                streamManager.releaseCamera()
                Log.d(TAG, "Camera resources released")

                openGlView = null
            } else {
                Log.d(TAG, "Not releasing camera because streaming is active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera resources", e)
            if (!streamManager.isStreaming()) {
                openGlView = null
            }
        }
    }

    /**
     * Restart camera preview with the given view
     */
    fun restartPreview(view: OpenGlView) {
        try {
            Log.d(TAG, "Restarting preview, current streaming=${streamManager.isStreaming()}")
            refreshSettings()

            if (view.holder.surface?.isValid != true) {
                Log.w(TAG, "Surface not valid, only saving reference")
                openGlView = view
                return
            }

            if (streamManager.isStreaming()) {
                try {
                    openGlView = view
                    streamManager.startPreview(view)
                    startAudioLevelUpdates()
                    microphoneMonitor.startMonitoring()
                    Log.d(TAG, "Preview restarted during active streaming")
                } catch (e: Exception) {
                    Log.e(TAG, "Error restarting preview during streaming", e)
                }
                return
            }

            if (streamManager.isOnPreview()) {
                streamManager.stopPreview()
            }

            openGlView = view
            streamManager.prepareVideo()
            streamManager.startPreview(view)
            startAudioLevelUpdates()
            microphoneMonitor.startMonitoring()
            Log.d(TAG, "Preview started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting preview", e)
        }
    }

    fun getStreamInfo(): StreamInfo {
        val streamSettings = settingsRepository.videoSettingsFlow.value
        return StreamInfo(
            protocol = connectionSettings.protocol.toString(),
            resolution = streamSettings.resolution.toString(),
            bitrate = "${streamSettings.bitrate} kbps",
            fps = "${streamSettings.fps} fps"
        )
    }

    fun isStreaming(): Boolean = streamManager.isStreaming()

    private fun refreshSettings() {
        try {
            connectionSettings = connectionRepository.activeProfileFlow.value?.settings ?: ConnectionSettings()
            streamManager.setStreamType(connectionSettings.protocol)

            if (openGlView != null) {
                streamManager.handleResolutionChange()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing settings", e)
        }
    }

    fun checkAndRestorePreview() {
        if (streamManager.isStreaming() && openGlView != null && openGlView?.holder?.surface?.isValid == true) {
            Log.d(TAG, "Restoring preview connection after app resume")
            try {
                if (!streamManager.isOnPreview()) {
                    streamManager.startPreview(openGlView!!)
                    startAudioLevelUpdates()
                    microphoneMonitor.startMonitoring()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore preview", e)
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): StreamService = this@StreamService
    }
}
