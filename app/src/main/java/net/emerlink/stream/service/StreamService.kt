package net.emerlink.stream.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.library.util.BitrateAdapter
import com.pedro.library.view.OpenGlView
import com.pedro.rtmp.rtmp.RtmpClient
import com.pedro.rtsp.rtsp.RtspClient
import com.pedro.srt.srt.SrtClient
import com.pedro.udp.UdpClient
import net.emerlink.stream.R
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.model.StreamType
import net.emerlink.stream.util.ErrorHandler
import net.emerlink.stream.util.NotificationHelper
import net.emerlink.stream.util.PathUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StreamService :
    Service(),
    ConnectChecker,
    SharedPreferences.OnSharedPreferenceChangeListener,
    SensorEventListener {

    companion object {
        private const val TAG = "StreamService"
        private const val NOTIFICATION_ID = 3425

        const val ACTION_START_STREAM = "net.emerlink.stream.START_STREAM"
        const val ACTION_STOP_STREAM = "net.emerlink.stream.STOP_STREAM"
        const val ACTION_EXIT_APP = "net.emerlink.stream.EXIT_APP"
        const val ACTION_AUTH_ERROR = "net.emerlink.stream.AUTH_ERROR"
        const val ACTION_CONNECTION_FAILED = "net.emerlink.stream.CONNECTION_FAILED"
        const val ACTION_TOOK_PICTURE = "net.emerlink.stream.TOOK_PICTURE"
        const val ACTION_NEW_BITRATE = "net.emerlink.stream.NEW_BITRATE"
        const val ACTION_LOCATION_CHANGE = "net.emerlink.stream.LOCATION_CHANGE"

        val observer = MutableLiveData<StreamService?>()
    }

    private lateinit var preferences: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    private lateinit var errorHandler: ErrorHandler
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var streamManager: StreamManager
    private lateinit var cameraManager: CameraManager

    // Stream objects
    private var bitrateAdapter: BitrateAdapter? = null

    // Stream settings
    private var protocol: String = ""
    private var address: String = ""
    private var port: Int = 0
    private var path: String = ""
    private var tcp: Boolean = false
    private var username: String = ""
    private var password: String = ""
    private var streamSelfSignedCert: Boolean = false
    private var certFile: String? = null
    private var certPassword: String = ""

    // Audio settings
    private var sampleRate: Int = 0
    private var stereo: Boolean = false
    private var echoCancel: Boolean = false
    private var noiseReduction: Boolean = false
    private var enableAudio: Boolean = false
    private var audioBitrate: Int = 0
    private var audioCodec: String = ""

    // Video settings
    private var fps: Int = 0
    private var resolution: Size? = null
    private var adaptiveBitrate: Boolean = false
    private var record: Boolean = false
    private var stream: Boolean = false
    private var bitrate: Int = 0
    private var codec: String = ""
    private var uid: String = ""

    // Camera
    private var videoSource: String = ""
    private var openGlView: OpenGlView? = null
    private var prepareAudio = false
    private var prepareVideo = false
    private val cameraIds = ArrayList<String>()

    // Sensors
    private lateinit var sensorManager: SensorManager
    private lateinit var magnetometer: android.hardware.Sensor
    private lateinit var accelerometer: android.hardware.Sensor
    private val gravityData = FloatArray(3)
    private val geomagneticData = FloatArray(3)
    private var hasGravityData = false
    private var hasGeomagneticData = false
    private var rotationInDegrees: Double = 0.0

    // Recording
    private lateinit var folder: File
    private var currentDateAndTime: String = ""

    // State
    private var exiting = false
    private val binder = LocalBinder()

    // Location
    private var locListener: StreamLocationListener? = null
    private var locManager: LocationManager? = null

    // Advanced Video Settings
    private var keyframeInterval: Int = 0
    private var videoProfile: String = ""
    private var videoLevel: String = ""
    private var bitrateMode: String = ""
    private var encodingQuality: String = ""

    // Network Settings
    private var bufferSize: Int = 0
    private var connectionTimeout: Int = 0
    private var autoReconnect: Boolean = false
    private var reconnectDelay: Int = 0
    private var maxReconnectAttempts: Int = 0

    // Stability Settings
    private var lowLatencyMode: Boolean = false
    private var hardwareRotation: Boolean = false
    private var dynamicFps: Boolean = false

    // New stream clients
    private lateinit var rtmpClient: RtmpClient
    private lateinit var rtspClient: RtspClient
    private lateinit var srtClient: SrtClient
    private lateinit var udpClient: UdpClient

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        errorHandler = ErrorHandler(this)
        notificationHelper = NotificationHelper(this)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.registerOnSharedPreferenceChangeListener(this)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        folder = PathUtils.getRecordPath(this)

        val notification =
            notificationHelper.createNotification(getString(R.string.ready_to_stream), true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val type =
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        loadPreferences()

        // Initialize stream manager
        streamManager = StreamManager(this, this, errorHandler)
        streamManager.setStreamType(getStreamTypeFromProtocol(protocol))

        // Initialize camera manager
        cameraManager = CameraManager(this, streamManager)

        observer.postValue(this)

        // Setup location and sensors
        locListener = StreamLocationListener(this)
        locManager =
            applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        magnetometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)!!
        accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)!!

        getCameraIds()

        // Initialize stream clients
        initializeStream()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_START_STREAM -> startStream()
                ACTION_STOP_STREAM -> stopStream(null, null)
                ACTION_EXIT_APP -> {
                    exiting = true
                    stopStream(null, null)
                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        observer.postValue(null)
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    // ConnectChecker implementation
    override fun onAuthError() {
        Log.d(TAG, "Auth error")
        stopStream(getString(R.string.auth_error), ACTION_AUTH_ERROR)
    }

    override fun onAuthSuccess() {
        Log.d(TAG, "Auth success")
    }

    override fun onConnectionFailed(reason: String) {
        Log.e(TAG, "Connection failed: $reason")
        stopStream(getString(R.string.connection_failed) + ": " + reason, ACTION_CONNECTION_FAILED)
    }

    override fun onConnectionStarted(url: String) {}

    override fun onConnectionSuccess() {
        if (adaptiveBitrate) {
            Log.d(TAG, "Setting adaptive bitrate")
            bitrateAdapter = BitrateAdapter { bitrate ->
                streamManager.setVideoBitrateOnFly(bitrate)
            }
            bitrateAdapter?.setMaxBitrate(bitrate * 1024)
        } else {
            Log.d(TAG, "Not doing adaptive bitrate")
        }
    }

    override fun onDisconnect() {}

    override fun onNewBitrate(bitrate: Long) {
        bitrateAdapter?.adaptBitrate(bitrate, streamManager.hasCongestion())
        val intent = Intent(ACTION_NEW_BITRATE)
        intent.putExtra(ACTION_NEW_BITRATE, bitrate)
        applicationContext.sendBroadcast(intent)
    }

    // SensorEventListener implementation
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

                val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display?.rotation ?: Surface.ROTATION_0
                } else {
                    @Suppress("DEPRECATION")
                    (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
                }
                val screenOrientation =
                    when (rotation) {
                        Surface.ROTATION_90 -> 90
                        Surface.ROTATION_180 -> -180
                        Surface.ROTATION_270 -> -90
                        else -> 0
                    }

                rotationInDegrees += screenOrientation
                if (rotationInDegrees < 0.0) {
                    rotationInDegrees += 360.0
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}

    // SharedPreferences.OnSharedPreferenceChangeListener implementation
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(TAG, "onSharedPreferenceChanged")
        if (key != PreferenceKeys.TEXT_OVERLAY) {
            loadPreferences()
        }
    }

    // Public methods
    fun startPreview(openGlView: OpenGlView) {
        this.openGlView = openGlView
        streamManager.startPreview(openGlView)
    }

    fun stopPreview() {
        streamManager.stopPreview()
    }

    fun toggleLantern(): Boolean {
        return cameraManager.toggleLantern()
    }

    fun switchCamera() {
        cameraManager.switchCamera()
    }

    fun setZoom(motionEvent: MotionEvent) {
        cameraManager.setZoom(motionEvent)
    }

    fun tapToFocus(motionEvent: MotionEvent) {
        cameraManager.tapToFocus(motionEvent)
    }

    fun takePhoto() {
        (streamManager.getGlInterface() as com.pedro.library.view.GlInterface).takePhoto { bitmap ->
            val handlerThread = android.os.HandlerThread("HandlerThread")
            handlerThread.start()
            Handler(handlerThread.looper).post {
                try {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
                    val currentDateAndTime = dateFormat.format(Date())
                    val filename = "EmerlinkStream_$currentDateAndTime.jpg"
                    val filePath = "${folder.absolutePath}/$filename"
                    
                    // Use ContentValues and MediaStore for modern API
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, 
                                "Pictures/EmerlinkStream")
                        }
                        
                        val resolver = applicationContext.contentResolver
                        val uri = resolver.insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                            contentValues
                        )
                        
                        uri?.let {
                            resolver.openOutputStream(it)?.use { outputStream ->
                                val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                                if (success) {
                                    applicationContext.sendBroadcast(Intent(ACTION_TOOK_PICTURE))
                                    notificationHelper.updateNotification(getString(R.string.saved_photo), true)
                                } else {
                                    notificationHelper.updateNotification(getString(R.string.saved_photo_failed), false)
                                }
                            }
                        }
                    } else {
                        // For older versions, use direct file saving
                        val file = File(filePath)
                        val fos = java.io.FileOutputStream(file)
                        val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                        fos.flush()
                        fos.close()
                        
                        if (success) {
                            // Make the image visible in gallery
                            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            mediaScanIntent.data = android.net.Uri.fromFile(file)
                            applicationContext.sendBroadcast(mediaScanIntent)
                            
                            applicationContext.sendBroadcast(Intent(ACTION_TOOK_PICTURE))
                            notificationHelper.updateNotification(getString(R.string.saved_photo), true)
                        } else {
                            notificationHelper.updateNotification(getString(R.string.saved_photo_failed), false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save photo: ${e.message}")
                    notificationHelper.updateNotification(
                        getString(R.string.saved_photo_failed) + ": " + e.message,
                        false
                    )
                }
            }
        }
    }

    // Private methods
    private fun getStreamTypeFromProtocol(protocol: String): StreamType {
        return when {
            protocol.startsWith("rtmp") -> StreamType.RTMP
            protocol.startsWith("rtsp") -> StreamType.RTSP
            protocol == "srt" -> StreamType.SRT
            else -> StreamType.UDP
        }
    }

    private fun getCameraIds() {
        if (videoSource == PreferenceKeys.VIDEO_SOURCE_DEFAULT) {
            val camera2Source = streamManager.getVideoSource() as Camera2Source
            cameraIds.addAll(camera2Source.camerasAvailable().toList())

            Log.d(TAG, "Got cameraIds $cameraIds")
        }
    }

    private fun loadPreferences() {
        Log.d(TAG, "Get settings")

        uid =
            preferences.getString(PreferenceKeys.UID, PreferenceKeys.UID_DEFAULT)
                ?: PreferenceKeys.UID_DEFAULT

        val oldProtocol = protocol
        protocol =
            preferences.getString(
                PreferenceKeys.STREAM_PROTOCOL,
                PreferenceKeys.STREAM_PROTOCOL_DEFAULT
            )
                ?: PreferenceKeys.STREAM_PROTOCOL_DEFAULT

        if (protocol != oldProtocol && ::streamManager.isInitialized) {
            streamManager.setStreamType(getStreamTypeFromProtocol(protocol))
        }

        // Stream Preferences
        stream =
            preferences.getBoolean(
                PreferenceKeys.STREAM_VIDEO,
                PreferenceKeys.STREAM_VIDEO_DEFAULT
            )
        address =
            preferences.getString(
                PreferenceKeys.STREAM_ADDRESS,
                PreferenceKeys.STREAM_ADDRESS_DEFAULT
            )
                ?: PreferenceKeys.STREAM_ADDRESS_DEFAULT
        port =
            preferences
                .getString(PreferenceKeys.STREAM_PORT, PreferenceKeys.STREAM_PORT_DEFAULT)
                ?.toInt()
                ?: PreferenceKeys.STREAM_PORT_DEFAULT.toInt()
        path =
            preferences.getString(
                PreferenceKeys.STREAM_PATH,
                PreferenceKeys.STREAM_PATH_DEFAULT
            )
                ?: PreferenceKeys.STREAM_PATH_DEFAULT
        tcp =
            preferences.getBoolean(
                PreferenceKeys.STREAM_USE_TCP,
                PreferenceKeys.STREAM_USE_TCP_DEFAULT
            )
        username =
            preferences.getString(
                PreferenceKeys.STREAM_USERNAME,
                PreferenceKeys.STREAM_USERNAME_DEFAULT
            )
                ?: PreferenceKeys.STREAM_USERNAME_DEFAULT
        password =
            preferences.getString(
                PreferenceKeys.STREAM_PASSWORD,
                PreferenceKeys.STREAM_PASSWORD_DEFAULT
            )
                ?: PreferenceKeys.STREAM_PASSWORD_DEFAULT
        streamSelfSignedCert =
            preferences.getBoolean(
                PreferenceKeys.STREAM_SELF_SIGNED_CERT,
                PreferenceKeys.STREAM_SELF_SIGNED_CERT_DEFAULT
            )
        certFile =
            preferences.getString(
                PreferenceKeys.STREAM_CERTIFICATE,
                PreferenceKeys.STREAM_CERTIFICATE_DEFAULT
            )
        certPassword =
            preferences.getString(
                PreferenceKeys.STREAM_CERTIFICATE_PASSWORD,
                PreferenceKeys.STREAM_CERTIFICATE_PASSWORD_DEFAULT
            )
                ?: PreferenceKeys.STREAM_CERTIFICATE_PASSWORD_DEFAULT

        // Video Preferences
        fps =
            preferences
                .getString(PreferenceKeys.VIDEO_FPS, PreferenceKeys.VIDEO_FPS_DEFAULT)
                ?.toInt()
                ?: PreferenceKeys.VIDEO_FPS_DEFAULT.toInt()
        record =
            preferences.getBoolean(
                PreferenceKeys.RECORD_VIDEO,
                PreferenceKeys.RECORD_VIDEO_DEFAULT
            )
        codec =
            preferences.getString(
                PreferenceKeys.VIDEO_CODEC,
                PreferenceKeys.VIDEO_CODEC_DEFAULT
            )
                ?: PreferenceKeys.VIDEO_CODEC_DEFAULT
        bitrate =
            preferences
                .getString(
                    PreferenceKeys.VIDEO_BITRATE,
                    PreferenceKeys.VIDEO_BITRATE_DEFAULT
                )
                ?.toInt()
                ?: PreferenceKeys.VIDEO_BITRATE_DEFAULT.toInt()
        adaptiveBitrate =
            preferences.getBoolean(
                PreferenceKeys.VIDEO_ADAPTIVE_BITRATE,
                PreferenceKeys.VIDEO_ADAPTIVE_BITRATE_DEFAULT
            )

        // Audio Preferences
        enableAudio =
            preferences.getBoolean(
                PreferenceKeys.ENABLE_AUDIO,
                PreferenceKeys.ENABLE_AUDIO_DEFAULT
            )
        echoCancel =
            preferences.getBoolean(
                PreferenceKeys.AUDIO_ECHO_CANCEL,
                PreferenceKeys.AUDIO_ECHO_CANCEL_DEFAULT
            )
        noiseReduction =
            preferences.getBoolean(
                PreferenceKeys.AUDIO_NOISE_REDUCTION,
                PreferenceKeys.AUDIO_NOISE_REDUCTION_DEFAULT
            )
        sampleRate =
            preferences
                .getString(
                    PreferenceKeys.AUDIO_SAMPLE_RATE,
                    PreferenceKeys.AUDIO_SAMPLE_RATE_DEFAULT
                )
                ?.toInt()
                ?: PreferenceKeys.AUDIO_SAMPLE_RATE_DEFAULT.toInt()
        stereo =
            preferences.getBoolean(
                PreferenceKeys.AUDIO_STEREO,
                PreferenceKeys.AUDIO_STEREO_DEFAULT
            )
        audioBitrate =
            preferences
                .getString(
                    PreferenceKeys.AUDIO_BITRATE,
                    PreferenceKeys.AUDIO_BITRATE_DEFAULT
                )
                ?.toInt()
                ?: PreferenceKeys.AUDIO_BITRATE_DEFAULT.toInt()
        audioCodec =
            preferences.getString(
                PreferenceKeys.AUDIO_CODEC,
                PreferenceKeys.AUDIO_CODEC_DEFAULT
            )
                ?: PreferenceKeys.AUDIO_CODEC_DEFAULT

        // Camera Preferences
        videoSource =
            preferences.getString(
                PreferenceKeys.VIDEO_SOURCE,
                PreferenceKeys.VIDEO_SOURCE_DEFAULT
            )
                ?: PreferenceKeys.VIDEO_SOURCE_DEFAULT

        // Resolution
        val resolutionString =
            preferences.getString(
                PreferenceKeys.VIDEO_RESOLUTION,
                PreferenceKeys.VIDEO_RESOLUTION_DEFAULT
            )
                ?: PreferenceKeys.VIDEO_RESOLUTION_DEFAULT

        val split = resolutionString.split("x")
        if (split.size == 2) {
            try {
                val width = split[0].trim().toInt()
                val height = split[1].trim().toInt()
                resolution = Size(width, height)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse resolution: $resolutionString", e)
                resolution = Size(1280, 720)
            }
        } else {
            resolution = Size(1280, 720)
        }

        // Advanced Video Settings
        keyframeInterval =
            preferences
                .getString(
                    PreferenceKeys.VIDEO_KEYFRAME_INTERVAL,
                    PreferenceKeys.VIDEO_KEYFRAME_INTERVAL_DEFAULT
                )
                ?.toInt()
                ?: PreferenceKeys.VIDEO_KEYFRAME_INTERVAL_DEFAULT.toInt()

        videoProfile =
            preferences.getString(
                PreferenceKeys.VIDEO_PROFILE,
                PreferenceKeys.VIDEO_PROFILE_DEFAULT
            )
                ?: PreferenceKeys.VIDEO_PROFILE_DEFAULT

        videoLevel =
            preferences.getString(
                PreferenceKeys.VIDEO_LEVEL,
                PreferenceKeys.VIDEO_LEVEL_DEFAULT
            )
                ?: PreferenceKeys.VIDEO_LEVEL_DEFAULT

        bitrateMode =
            preferences.getString(
                PreferenceKeys.VIDEO_BITRATE_MODE,
                PreferenceKeys.VIDEO_BITRATE_MODE_DEFAULT
            )
                ?: PreferenceKeys.VIDEO_BITRATE_MODE_DEFAULT

        encodingQuality =
            preferences.getString(
                PreferenceKeys.VIDEO_QUALITY,
                PreferenceKeys.VIDEO_QUALITY_DEFAULT
            )
                ?: PreferenceKeys.VIDEO_QUALITY_DEFAULT

        // Network Settings
        bufferSize =
            preferences
                .getString(
                    PreferenceKeys.NETWORK_BUFFER_SIZE,
                    PreferenceKeys.NETWORK_BUFFER_SIZE_DEFAULT
                )
                ?.toInt()
                ?: PreferenceKeys.NETWORK_BUFFER_SIZE_DEFAULT.toInt()

        connectionTimeout =
            preferences
                .getString(
                    PreferenceKeys.NETWORK_TIMEOUT,
                    PreferenceKeys.NETWORK_TIMEOUT_DEFAULT
                )
                ?.toInt()
                ?: PreferenceKeys.NETWORK_TIMEOUT_DEFAULT.toInt()

        autoReconnect =
            preferences.getBoolean(
                PreferenceKeys.NETWORK_RECONNECT,
                PreferenceKeys.NETWORK_RECONNECT_DEFAULT
            )

        reconnectDelay =
            preferences
                .getString(
                    PreferenceKeys.NETWORK_RECONNECT_DELAY,
                    PreferenceKeys.NETWORK_RECONNECT_DELAY_DEFAULT
                )
                ?.toInt()
                ?: PreferenceKeys.NETWORK_RECONNECT_DELAY_DEFAULT.toInt()

        maxReconnectAttempts =
            preferences
                .getString(
                    PreferenceKeys.NETWORK_MAX_RECONNECT_ATTEMPTS,
                    PreferenceKeys.NETWORK_MAX_RECONNECT_ATTEMPTS_DEFAULT
                )
                ?.toInt()
                ?: PreferenceKeys.NETWORK_MAX_RECONNECT_ATTEMPTS_DEFAULT.toInt()

        // Stability Settings
        lowLatencyMode =
            preferences.getBoolean(
                PreferenceKeys.STABILITY_LOW_LATENCY,
                PreferenceKeys.STABILITY_LOW_LATENCY_DEFAULT
            )

        hardwareRotation =
            preferences.getBoolean(
                PreferenceKeys.STABILITY_HARDWARE_ROTATION,
                PreferenceKeys.STABILITY_HARDWARE_ROTATION_DEFAULT
            )

        dynamicFps =
            preferences.getBoolean(
                PreferenceKeys.STABILITY_DYNAMIC_FPS,
                PreferenceKeys.STABILITY_DYNAMIC_FPS_DEFAULT
            )
    }

    private fun startStream() {
        Log.d(TAG, "Starting stream")

        if (streamManager.isStreaming()) {
            Log.d(TAG, "Already streaming")
            return
        }

        if (streamManager.isRecording()) {
            Log.d(TAG, "Already recording")
            return
        }

        if (streamManager.prepareAudio() && streamManager.prepareVideo()) {
            prepareAudio = true
            prepareVideo = true

            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
            currentDateAndTime = dateFormat.format(Date())

            if (stream) {
                startStreaming()
            }

            if (record) {
                startRecording()
            }

            // Start location updates
            try {
                locManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    1f,
                    locListener!!
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to request location updates", e)
            }

            // Start sensor updates
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            Log.e(TAG, "Failed to prepare audio or video")
            notificationHelper.updateNotification(getString(R.string.failed_to_prepare), false)
        }
    }

    private fun startStreaming() {
        Log.d(TAG, "Starting streaming")

        val url =
            when {
                protocol.startsWith("rtmp") -> {
                    if (protocol == "rtmps") {
                        "rtmps://$address:$port/$path"
                    } else {
                        "rtmp://$address:$port/$path"
                    }
                }

                protocol.startsWith("rtsp") -> {
                    if (protocol == "rtsps") {
                        "rtsps://$address:$port/$path"
                    } else {
                        "rtsp://$address:$port/$path"
                    }
                }

                protocol == "srt" -> {
                    "srt://$address:$port"
                }

                else -> {
                    "udp://$address:$port"
                }
            }

        Log.d(TAG, "Stream URL: $url")

        streamManager.startStream(url, protocol, username, password, tcp)
        notificationHelper.updateNotification(getString(R.string.streaming), true)
    }

    private fun startRecording() {
        Log.d(TAG, "Starting recording")

        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                Log.e(TAG, "Failed to create folder: ${folder.absolutePath}")
                notificationHelper.updateNotification(getString(R.string.failed_to_record), false)
                return
            }
        }

        val filename = "EmerlinkStream_$currentDateAndTime.mp4"
        val filePath = "${folder.absolutePath}/$filename"

        Log.d(TAG, "Recording to: $filePath")

        streamManager.startRecord(filePath)
        notificationHelper.updateNotification(getString(R.string.recording), true)
    }

    private fun stopStream(message: String?, action: String?) {
        Log.d(TAG, "Stopping stream")

        try {
            streamManager.stopStream()

            if (streamManager.isRecording()) {
                streamManager.stopRecord()
            }

            prepareAudio = false
            prepareVideo = false

            // Stop location updates
            locManager?.removeUpdates(locListener!!)

            // Stop sensor updates
            sensorManager.unregisterListener(this)

            if (message != null) {
                notificationHelper.updateNotification(message, false)
            } else {
                notificationHelper.updateNotification(getString(R.string.ready_to_stream), true)
            }

            if (action != null) {
                val intent = Intent(action)
                applicationContext.sendBroadcast(intent)
            }

            if (exiting) {
                stopSelf()
            }
        } catch (e: Exception) {
            errorHandler.handleStreamError(e)
        }
    }

    private fun initializeStream() {
        // Initialize based on stream type
        when (getStreamTypeFromProtocol(protocol)) {
            StreamType.RTMP -> {
                rtmpClient = RtmpClient(this)
            }

            StreamType.RTSP -> {
                rtspClient = RtspClient(this)
            }

            StreamType.SRT -> {
                srtClient = SrtClient(this)
            }

            StreamType.UDP -> {
                udpClient = UdpClient(this)
            }
        }
    }

    // Add this inner class inside your StreamService class
    inner class LocalBinder : Binder() {
        fun getService(): StreamService = this@StreamService
    }
}
