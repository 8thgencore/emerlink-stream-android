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
import com.pedro.common.AudioCodec
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.util.BitrateAdapter
import com.pedro.library.view.GlInterface
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.R
import net.emerlink.stream.core.AppIntentActions
import net.emerlink.stream.core.ErrorHandler
import net.emerlink.stream.core.notification.NotificationManager
import net.emerlink.stream.data.model.ConnectionSettings
import net.emerlink.stream.data.model.StreamInfo
import net.emerlink.stream.data.repository.ConnectionProfileRepository
import net.emerlink.stream.data.repository.SettingsRepository
import net.emerlink.stream.data.model.Resolution
import net.emerlink.stream.data.model.StreamType
import net.emerlink.stream.service.camera.CameraInterface
import net.emerlink.stream.service.media.MediaManager
import net.emerlink.stream.service.microphone.MicrophoneMonitor
import org.koin.java.KoinJavaComponent.inject
import kotlin.math.log10

class StreamService : Service(), ConnectChecker, SensorEventListener {
    companion object {
        private const val TAG = "StreamService"
        private const val AUDIO_LEVEL_UPDATE_INTERVAL = 100L
        private const val FALLBACK_AUDIO_BITRATE = 128 * 1024
        private const val FALLBACK_AUDIO_SAMPLE_RATE = 44100
    }

    private val settingsRepository: SettingsRepository by inject(SettingsRepository::class.java)
    private val connectionRepository: ConnectionProfileRepository by inject(ConnectionProfileRepository::class.java)

    private lateinit var notificationManager: NotificationManager
    private lateinit var errorHandler: ErrorHandler
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
    private var isPreviewActive = false
    private var audioLevelUpdateHandler: Handler? = null
    private var audioLevelRunnable: Runnable? = null
    private var isRunningAudioLevelUpdates = false

    private val binder = LocalBinder()
    private val gravityData = FloatArray(3)
    private val geomagneticData = FloatArray(3)

    private var hasGravityData = false
    private var hasGeomagneticData = false
    private var rotationInDegrees: Double = 0.0

    private var currentView: OpenGlView? = null
    private var cameraInterface: CameraInterface? = null
    private var streamType: StreamType = StreamType.RTMP
    private val cameraIds = ArrayList<String>()
    private var lanternEnabled = false

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        initDependencies()
        initSensors()
        registerCommandReceiver()
        initForeground()
        initAudioLevelUpdates()
        initCamera()
    }

    private fun initDependencies() {
        errorHandler = ErrorHandler(this)
        notificationManager = NotificationManager.getInstance(this)
        connectionSettings = connectionRepository.activeProfileFlow.value?.settings ?: ConnectionSettings()
        mediaManager = MediaManager(this, this, notificationManager)
        microphoneMonitor = MicrophoneMonitor()
    }

    private fun initSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        magnetometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)!!
        accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)!!
    }

    private fun initForeground() {
        try {
            // Create a basic notification that will be replaced immediately
            val notifyId = NotificationManager.START_STREAM_NOTIFICATION_ID
            val notification = notificationManager.createNotification(
                getString(R.string.streaming), true, NotificationManager.ACTION_STOP_ONLY
            )

            // Handle foreground service types based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var types = 0

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        // Add foreground service type for media projection on Android 14+
                        types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    }
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
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)

            // Fallback to basic foreground if something went wrong
            try {
                val notification = notificationManager.createNotification(
                    getString(R.string.streaming), true, NotificationManager.ACTION_STOP_ONLY
                )
                startForeground(NotificationManager.START_STREAM_NOTIFICATION_ID, notification)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Critical error starting foreground service: ${fallbackEx.message}", fallbackEx)
            }
        }
    }

    private fun initAudioLevelUpdates() {
        audioLevelUpdateHandler = Handler(Looper.getMainLooper())
        audioLevelRunnable = object : Runnable {
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

            if (exiting && isStreaming()) {
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
            if (isStreaming()) {
                stopStream()
                notifyStreamStopped()

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
                stopStream()
                notifyStreamStopped()

                notificationManager.showErrorSafely(this, "Critical error: ${e.message}")
            } catch (e2: Exception) {
                Log.e(TAG, "Double error", e2)
            }
        }
    }

    private fun notifyStreamStopped() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(AppIntentActions.BROADCAST_STREAM_STOPPED))
    }

    override fun onConnectionStarted(url: String) {}

    override fun onConnectionSuccess() {
        val videoSettings = settingsRepository.videoSettingsFlow.value
        if (videoSettings.adaptiveBitrate) {
            bitrateAdapter = BitrateAdapter { bitrate ->
                setVideoBitrateOnFly(bitrate)
            }
            bitrateAdapter?.setMaxBitrate(videoSettings.bitrate * 1024)
        }
    }

    override fun onDisconnect() {}

    override fun onNewBitrate(bitrate: Long) {
        bitrateAdapter?.adaptBitrate(bitrate, hasCongestion())
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
        if (isPreviewActive) return

        try {
            Log.d(TAG, "Starting preview")
            refreshSettings()
            currentView = view

            if (!isStreaming()) {
                prepareVideo()
            }

            val rotation = CameraHelper.getCameraOrientation(this)
            cameraInterface?.replaceView(view)
            cameraInterface?.startPreview(CameraHelper.Facing.BACK, rotation)

            isPreviewActive = true
            startAudioLevelUpdates()
            microphoneMonitor.startMonitoring()

            Log.d(TAG, "Preview started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting preview", e)
            isPreviewActive = false
            currentView = null
            broadcastPreviewStatus(false)
        }
    }

    fun stopPreview() {
        if (!isPreviewActive) {
            Log.d(TAG, "Preview is not active, skipping stopPreview")
            return
        }

        try {
            Log.d(TAG, "Stopping preview, streaming=${isStreaming()}")
            cameraInterface?.stopPreview()
            isPreviewActive = false

            if (!isStreaming()) {
                stopAudioLevelUpdates()
                microphoneMonitor.stopMonitoring()
            }

            broadcastPreviewStatus(false)
            Log.d(TAG, "Preview stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping preview", e)
            isPreviewActive = false
            broadcastPreviewStatus(false)
        }
    }

    fun toggleLantern(): Boolean {
        try {
            val camera2Result = withCamera2Source({ camera2Source ->
                try {
                    Log.d(TAG, "Toggling lantern using Camera2Source")
                    val wasEnabled = camera2Source.isLanternEnabled()
                    if (wasEnabled) {
                        camera2Source.disableLantern()
                        Log.d(TAG, "Lantern disabled")
                    } else {
                        camera2Source.enableLantern()
                        Log.d(TAG, "Lantern enabled")
                    }
                    camera2Source.isLanternEnabled()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to toggle lantern with Camera2Source: ${e.localizedMessage}", e)
                    false
                }
            }, false)

            if (!camera2Result) {
                Log.d(TAG, "Toggling lantern using CameraInterface")
                lanternEnabled = !lanternEnabled

                if (lanternEnabled) {
                    cameraInterface?.enableLantern()
                    Log.d(TAG, "Lantern enabled via CameraInterface")
                } else {
                    cameraInterface?.disableLantern()
                    Log.d(TAG, "Lantern disabled via CameraInterface")
                }

                return lanternEnabled
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling lantern: ${e.message}", e)
            return false
        }
    }

    fun switchCamera(): Boolean {
        try {
            val camera2Result = withCamera2Source({ camera2Source ->
                Log.d(TAG, "Switching camera using Camera2Source")

                if (cameraIds.isEmpty()) getCameraIds()
                if (cameraIds.isEmpty()) return@withCamera2Source false

                currentCameraId = (currentCameraId + 1) % cameraIds.size

                Log.d(TAG, "Switching to camera ${cameraIds[currentCameraId]}")
                camera2Source.openCameraId(cameraIds[currentCameraId])
                true
            }, false)

            if (!camera2Result) {
                Log.d(TAG, "Switching camera using CameraInterface")
                cameraInterface?.switchCamera()
                currentCameraId = if (currentCameraId == 0) 1 else 0
                return true
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error switching camera: ${e.message}", e)
            return false
        }
    }

    fun setZoom(event: MotionEvent) {
        withCamera2Source({ camera2Source ->
            camera2Source.setZoom(event)
        }, Unit)
    }

    fun tapToFocus(event: MotionEvent) {
        withCamera2Source({ camera2Source ->
            camera2Source.tapToFocus(event)
        }, Unit)
    }

    fun takePhoto() = mediaManager.takePhoto()

    fun startStream() {
        if (isStreaming() || isRecording()) {
            Log.d(TAG, "Stream already active, not starting again")
            initForeground()
            return
        }

        Log.d(TAG, "Starting stream")
        initForeground()

        val audioInitialized = prepareAudio()
        val videoInitialized = prepareVideo()

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
        if (videoSettings.streamVideo) {
            startStreaming()
        }
        if (videoSettings.recordVideo) {
            mediaManager.startRecording()
        }

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)

        Log.d(TAG, "Stream started successfully")
    }

    private fun startStreaming() {
        try {
            val streamUrl = connectionSettings.buildStreamUrl()
            Log.d(TAG, "Stream URL: ${streamUrl.replace(Regex(":[^:/]*:[^:/]*"), ":****:****")}")

            if (connectionSettings.protocol.toString().startsWith("rtsp")) {
                cameraInterface?.setProtocol(connectionSettings.tcp)
            }

            if (connectionSettings.username.isNotEmpty() &&
                connectionSettings.password.isNotEmpty() &&
                (connectionSettings.protocol.toString().startsWith("rtmp") ||
                 connectionSettings.protocol.toString().startsWith("rtsp"))
            ) {
                cameraInterface?.setAuthorization(connectionSettings.username, connectionSettings.password)
            }

            cameraInterface?.startStream(streamUrl)
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

            if (cameraInterface?.isStreaming == true) {
                cameraInterface?.stopStream()
                notifyStreamStopped()
            }

            if (cameraInterface?.isRecording == true) {
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
            notifyStreamStopped()

            val errorMsg = message?.let { "$it (${e.message})" } ?: e.message ?: "Unknown error"
            notificationManager.showErrorSafely(this, errorMsg)
            errorHandler.handleStreamError(e)
        }
    }

    fun toggleMute(muted: Boolean) {
        try {
            if (muted) disableAudio() else enableAudio()
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mute state", e)
        }
    }

    fun releaseCamera() {
        try {
            Log.d(TAG, "Освобождение ресурсов камеры")

            if (isOnPreview()) {
                stopPreview()
            }

            if (isStreaming()) {
                stopStream()
            }

            if (isRecording()) {
                stopRecord()
            }

            cameraInterface = CameraInterface.create(this, this, streamType)
            currentView = null

            Log.d(TAG, "Ресурсы камеры освобождены")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка освобождения камеры: ${e.message}", e)
        }
    }

    fun isPreviewRunning(): Boolean = isPreviewActive

    fun getStreamInfo(): StreamInfo {
        val streamSettings = settingsRepository.videoSettingsFlow.value
        return StreamInfo(
            protocol = connectionSettings.protocol.toString(),
            resolution = streamSettings.resolution.toString(),
            bitrate = "${streamSettings.bitrate} kbps",
            fps = "${streamSettings.fps} fps"
        )
    }

    fun isStreaming(): Boolean = cameraInterface?.isStreaming ?: false

    fun isRecording(): Boolean = cameraInterface?.isRecording ?: false

    fun setVideoBitrateOnFly(bitrate: Int) = cameraInterface?.setVideoBitrateOnFly(bitrate)

    fun hasCongestion(): Boolean = cameraInterface?.hasCongestion() ?: false

    fun enableAudio() = cameraInterface?.enableAudio()

    fun disableAudio() = cameraInterface?.disableAudio()

    private fun refreshSettings() {
        try {
            connectionSettings = connectionRepository.activeProfileFlow.value?.settings ?: ConnectionSettings()
            setStreamType(connectionSettings.protocol)

            if (isPreviewActive && openGlView != null) {
                handleResolutionChange()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing settings", e)
        }
    }

    /**
     * Broadcast preview status to UI components
     */
    private fun broadcastPreviewStatus(active: Boolean) {
        val intent = Intent(AppIntentActions.BROADCAST_PREVIEW_STATUS)
        intent.putExtra(AppIntentActions.EXTRA_PREVIEW_ACTIVE, active)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun initCamera() {
        cameraInterface = CameraInterface.create(this, this, streamType)
        getCameraIds()
    }

    private fun getCameraIds() {
        withCamera2Source({ camera2Source ->
            cameraIds.clear()
            cameraIds.addAll(camera2Source.camerasAvailable().toList())
            Log.d(TAG, "Got cameraIds $cameraIds")
        }, Unit)
    }

    private fun <T> withCamera2Source(
        action: (Camera2Source) -> T,
        defaultValue: T
    ): T {
        val videoSource = getVideoSource()
        if (videoSource is Camera2Source) {
            return action(videoSource)
        }
        return defaultValue
    }

    private fun getVideoSource(): Any = cameraInterface ?: throw IllegalStateException("Camera interface not initialized")

    fun setStreamType(type: StreamType) {
        if (type != streamType) {
            streamType = type
            cameraInterface = CameraInterface.create(this, this, streamType)
        }
    }

    private fun handleResolutionChange() {
        currentView?.let { view ->
            Log.d(TAG, "Перезапуск превью с новым разрешением")
            try {
                switchStreamResolution()
                startPreview(view)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при обновлении разрешения: ${e.message}", e)
            }
        }
    }

    private fun switchStreamResolution() {
        try {
            val videoSettings = settingsRepository.videoSettingsFlow.value
            val resolution = Resolution.parseFromSize(videoSettings.resolution)

            if (isOnPreview()) {
                val rotation = CameraHelper.getCameraOrientation(this)

                cameraInterface?.stopPreview()

                prepareVideoWithParams(
                    resolution,
                    videoSettings.fps,
                    videoSettings.keyframeInterval,
                    videoSettings.bitrate * 1000
                )

                currentView?.let { view ->
                    cameraInterface?.replaceView(view)
                    cameraInterface?.startPreview(CameraHelper.Facing.BACK, rotation)
                }
            }

            Log.d(
                TAG,
                "Установлено новое разрешение стрима: ${resolution.width}x${resolution.height}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при изменении разрешения стрима: ${e.message}")
        }
    }

    private fun prepareVideoWithParams(
        resolution: Resolution,
        fps: Int,
        iFrameInterval: Int,
        bitrate: Int
    ) {
        val rotation = CameraHelper.getCameraOrientation(this)
        cameraInterface?.prepareVideo(
            width = resolution.width,
            height = resolution.height,
            fps = fps,
            bitrate = bitrate,
            iFrameInterval = iFrameInterval,
            rotation = rotation
        )
    }

    fun isOnPreview(): Boolean = cameraInterface?.isOnPreview ?: false

    fun prepareVideo(): Boolean {
        try {
            prepareVideoWithCurrentSettings()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка подготовки видео: ${e.message}", e)
            return false
        }
    }

    private fun prepareVideoWithCurrentSettings() {
        val videoSettings = settingsRepository.videoSettingsFlow.value
        val resolution = Resolution.parseFromSize(videoSettings.resolution)
        prepareVideoWithParams(
            resolution,
            videoSettings.fps,
            videoSettings.keyframeInterval,
            videoSettings.bitrate * 1000
        )
    }

    fun prepareAudio(): Boolean {
        Log.d(TAG, "Подготовка аудио")

        val audioSettings = settingsRepository.audioSettingsFlow.value

        try {
            cameraInterface?.prepareAudio(
                bitrate = audioSettings.bitrate * 1024,
                sampleRate = audioSettings.sampleRate,
                isStereo = audioSettings.stereo
            )
            cameraInterface?.setAudioCodec(AudioCodec.AAC)
            return true
        } catch (_: Exception) {
            return tryFallbackAudioSettings()
        }
    }

    private fun tryFallbackAudioSettings(): Boolean {
        try {
            cameraInterface?.prepareAudio(
                bitrate = FALLBACK_AUDIO_BITRATE,
                sampleRate = FALLBACK_AUDIO_SAMPLE_RATE,
                isStereo = false
            )
            cameraInterface?.setAudioCodec(AudioCodec.AAC)
            return true
        } catch (e2: Exception) {
            Log.e(TAG, "Не удалось инициализировать аудио: ${e2.message}", e2)
            return false
        }
    }

    fun getGlInterface(): GlInterface = cameraInterface?.glInterface
        ?: throw IllegalStateException("Camera interface not initialized")

    fun startRecord(filePath: String) {
        cameraInterface?.startRecord(filePath)
    }

    fun stopRecord() {
        cameraInterface?.stopRecord()
    }

    inner class LocalBinder : Binder() {
        fun getService(): StreamService = this@StreamService
    }
}
