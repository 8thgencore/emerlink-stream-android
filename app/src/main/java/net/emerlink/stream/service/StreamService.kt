@file:Suppress("ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
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
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.data.repository.ConnectionProfileRepository
import net.emerlink.stream.data.repository.SettingsRepository
import net.emerlink.stream.service.camera.CameraManager
import net.emerlink.stream.service.camera.ICameraManager
import net.emerlink.stream.service.location.StreamLocationListener
import net.emerlink.stream.service.media.MediaManager
import net.emerlink.stream.service.stream.StreamManager
import org.koin.java.KoinJavaComponent.inject

class StreamService :
    Service(),
    ConnectChecker,
    SensorEventListener {
    companion object {
        private const val TAG = "StreamService"
    }

    private val settingsRepository: SettingsRepository by inject(SettingsRepository::class.java)
    private val connectionRepository: ConnectionProfileRepository by inject(ConnectionProfileRepository::class.java)

    private lateinit var notificationManager: NotificationManager
    private lateinit var errorHandler: ErrorHandler
    private lateinit var streamManager: StreamManager
    private lateinit var cameraManager: ICameraManager
    private lateinit var mediaManager: MediaManager
    private lateinit var connectionSettings: ConnectionSettings
    private lateinit var sensorManager: SensorManager
    private lateinit var magnetometer: android.hardware.Sensor
    private lateinit var accelerometer: android.hardware.Sensor

    private var bitrateAdapter: BitrateAdapter? = null
    private var exiting = false
    private var currentCameraId = 0
    private var locListener: StreamLocationListener? = null
    private var locManager: LocationManager? = null
    private var openGlView: OpenGlView? = null
    private var commandReceiver: BroadcastReceiver? = null
    private var isPreviewActive = false

    private val binder = LocalBinder()
    private val cameraIds = ArrayList<String>()
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
        getCameraIds()
        registerCommandReceiver()
    }

    private fun initDependencies() {
        errorHandler = ErrorHandler(this)
        notificationManager = NotificationManager.getInstance(this)
        connectionSettings = connectionRepository.activeProfileFlow.value?.settings ?: ConnectionSettings()
        streamManager = StreamManager(this, this, errorHandler, settingsRepository)
        streamManager.setStreamType(connectionSettings.protocol)
        cameraManager = CameraManager(this, { streamManager.getVideoSource() }, { streamManager.getCameraInterface() })
        mediaManager = MediaManager(this, streamManager, notificationManager)
        locListener = StreamLocationListener(this)
        locManager = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
    }

    private fun initSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        magnetometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)!!
        accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)!!
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerCommandReceiver() {
        val intentFilter =
            IntentFilter().apply {
                addAction(AppIntentActions.ACTION_START_STREAM)
                addAction(AppIntentActions.ACTION_STOP_STREAM)
                addAction(AppIntentActions.ACTION_EXIT_APP)
                addAction(AppIntentActions.ACTION_DISMISS_ERROR)
            }

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    Log.d(TAG, "Received intent: ${intent.action}")
                    try {
                        when (intent.action) {
                            AppIntentActions.ACTION_START_STREAM -> startStream()

                            AppIntentActions.ACTION_STOP_STREAM -> {
                                notificationManager.clearStreamingNotifications()
                                stopStream(null, null)
                            }

                            AppIntentActions.ACTION_EXIT_APP -> {
                                exiting = true
                                notificationManager.clearAllNotifications()
                                stopStream(null, null)
                                stopSelf()
                            }

                            AppIntentActions.ACTION_DISMISS_ERROR -> {
                                notificationManager.clearErrorNotifications()
                            }
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
                        notificationManager.clearAllNotifications()
                        stopStream(null, null)
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
            if (streamManager.isStreaming()) {
                stopStream(null, null)
            }
            if (commandReceiver != null) {
                unregisterReceiver(commandReceiver)
                commandReceiver = null
            }
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
                notifyStreamStopped()

                val errorText =
                    when {
                        reason.contains("461") -> getString(R.string.error_unsupported_transport)
                        else -> getString(R.string.connection_failed) + ": " + reason
                    }

                notificationManager.showErrorNotification(errorText)
                applicationContext.sendBroadcast(Intent(AppIntentActions.ACTION_CONNECTION_FAILED))
                Handler(Looper.getMainLooper()).postDelayed(
                    { stopStream(null, null, false) },
                    500
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling connection failure", e)
            try {
                streamManager.stopStream()
                notifyStreamStopped()
                notificationManager.showErrorNotification("Critical error: ${e.message}")
            } catch (e2: Exception) {
                Log.e(TAG, "Double error", e2)
            }
        }
    }

    private fun notifyStreamStopped() {
        LocalBroadcastManager
            .getInstance(this)
            .sendBroadcast(Intent(AppIntentActions.BROADCAST_STREAM_STOPPED))
    }

    override fun onConnectionStarted(url: String) {}

    override fun onConnectionSuccess() {
        val videoSettings = settingsRepository.videoSettingsFlow.value
        if (videoSettings.adaptiveBitrate) {
            bitrateAdapter =
                BitrateAdapter { bitrate ->
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
    ) {}

    fun startPreview(view: OpenGlView) {
        if (isPreviewActive) {
            return
        }

        try {
            refreshSettings()
            openGlView = view
            streamManager.prepareVideo()
            streamManager.startPreview(view)
            isPreviewActive = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting preview", e)
        }
    }

    fun stopPreview() {
        if (!isPreviewActive) {
            return
        }

        try {
            streamManager.stopPreview()
            isPreviewActive = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping preview", e)
        }
    }

    fun toggleLantern(): Boolean = cameraManager.toggleLantern()

    fun switchCamera(): Boolean {
        try {
            val result = cameraManager.switchCamera()
            if (result) {
                currentCameraId = cameraManager.getCurrentCameraId()
            }
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error switching camera", e)
            return false
        }
    }

    fun setZoom(motionEvent: MotionEvent) = cameraManager.setZoom(motionEvent)

    fun tapToFocus(motionEvent: MotionEvent) = cameraManager.tapToFocus(motionEvent)

    fun takePhoto() = mediaManager.takePhoto()

    private fun getCameraIds() {
        val videoSettings = settingsRepository.videoSettingsFlow.value
        if (videoSettings.videoSource == PreferenceKeys.VIDEO_SOURCE_DEFAULT) {
            try {
                val cameraManager =
                    applicationContext.getSystemService(CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                cameraIds.addAll(cameraManager.cameraIdList.toList())
            } catch (e: Exception) {
                Log.e(TAG, "Error getting camera list", e)
                if (cameraIds.isEmpty()) {
                    cameraIds.add("0")
                    cameraIds.add("1")
                }
            }
        }
    }

    fun startStream() {
        if (streamManager.isStreaming() || streamManager.isRecording()) {
            return
        }

        val audioInitialized = streamManager.prepareAudio()
        val videoInitialized = streamManager.prepareVideo()

        if (!audioInitialized) {
            notificationManager.showErrorNotification(getString(R.string.failed_to_prepare_audio))
        }

        if (!videoInitialized) {
            notificationManager.showErrorNotification(getString(R.string.failed_to_prepare))
            return
        }

        val videoSettings = settingsRepository.videoSettingsFlow.value
        if (videoSettings.streamVideo) {
            startStreaming()
        }
        if (videoSettings.recordVideo) {
            mediaManager.startRecording()
        }

        startLocationTracking()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun startLocationTracking() {
        try {
            if (hasLocationPermission()) {
                locManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    1f,
                    locListener!!
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to request location updates", e)
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

            // Create notification and start foreground service to keep streaming when app is in background
            val notification =
                notificationManager.createNotification(
                    getString(R.string.streaming),
                    true,
                    NotificationManager.ACTION_STOP_ONLY
                )
            startForeground(NotificationManager.NOTIFICATION_ID, notification)

            notificationManager.showStreamingNotification(
                getString(R.string.streaming),
                true,
                NotificationManager.ACTION_STOP_ONLY
            )
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
            notificationManager.clearStreamingNotifications()

            if (streamManager.isStreaming()) {
                streamManager.stopStream()
                notifyStreamStopped()

                // Stop being a foreground service when stream stops
                stopForeground(STOP_FOREGROUND_REMOVE)
            }

            if (streamManager.isRecording()) {
                mediaManager.stopRecording()
            }

            stopLocationTracking()

            try {
                sensorManager.unregisterListener(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering sensor listener", e)
            }

            when {
                message != null && isError -> notificationManager.showErrorNotification(message)
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
            notificationManager.showErrorNotification(errorMsg)
            errorHandler.handleStreamError(e)
        }
    }

    private fun stopLocationTracking() {
        locListener?.let {
            try {
                locManager?.removeUpdates(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping location updates", e)
            }
        }
    }

    fun toggleMute(muted: Boolean) {
        try {
            if (muted) streamManager.disableAudio() else streamManager.enableAudio()
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mute state", e)
        }
    }

    private fun hasLocationPermission(): Boolean =
        (
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )

    fun releaseCamera() {
        try {
            if (streamManager.isOnPreview()) {
                streamManager.stopPreview()
            }
            streamManager.releaseCamera()
            openGlView = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera resources", e)
        }
    }

    fun restartPreview(view: OpenGlView) {
        try {
            refreshSettings()

            val isCurrentlyStreaming = streamManager.isStreaming()
            Log.d(TAG, "Перезапуск превью, стрим активен: $isCurrentlyStreaming")

            if (streamManager.isOnPreview()) {
                streamManager.stopPreview()
            }

            Thread.sleep(50)

            openGlView = view

            if (!isCurrentlyStreaming) {
                streamManager.prepareVideo()
            }

            streamManager.startPreview(view)
            isPreviewActive = true

            if (isCurrentlyStreaming) {
                Handler(Looper.getMainLooper()).postDelayed({
                    streamManager.restartVideoEncoder()
                }, 300)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting preview", e)
            try {
                streamManager.stopPreview()
                streamManager.releaseCamera()
                isPreviewActive = false
            } catch (e2: Exception) {
                Log.e(TAG, "Error cleaning up after failure", e2)
            }
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

    fun isStreaming(): Boolean = streamManager.isStreaming()

    private fun refreshSettings() {
        try {
            connectionSettings = connectionRepository.activeProfileFlow.value?.settings ?: ConnectionSettings()
            streamManager.setStreamType(connectionSettings.protocol)

            if (isPreviewActive && openGlView != null) {
                streamManager.handleResolutionChange()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing settings", e)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): StreamService = this@StreamService
    }
}
