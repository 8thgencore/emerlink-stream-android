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
import android.media.MediaScannerConnection
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
import com.pedro.library.util.BitrateAdapter
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.R
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.model.StreamType
import net.emerlink.stream.util.ErrorHandler
import net.emerlink.stream.util.NotificationHelper
import net.emerlink.stream.util.PathUtils
import net.emerlink.stream.util.PreferencesLoader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StreamService : Service(), ConnectChecker, SharedPreferences.OnSharedPreferenceChangeListener,
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

    // New variables
    private var currentCameraId = 0
    private var isFlashOn = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        errorHandler = ErrorHandler(this)
        notificationHelper = NotificationHelper(this)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.registerOnSharedPreferenceChangeListener(this)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        folder = PathUtils.getRecordPath(this)

        val notification = notificationHelper.createNotification(getString(R.string.ready_to_stream), true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
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
        locManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

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
            val success = SensorManager.getRotationMatrix(
                rotationMatrix, identityMatrix, gravityData, geomagneticData
            )

            if (success) {
                val orientationMatrix = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientationMatrix)
                val rotationInRadians = orientationMatrix[0]
                rotationInDegrees = Math.toDegrees(rotationInRadians.toDouble())

                val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display?.rotation ?: Surface.ROTATION_0
                } else {
                    @Suppress("DEPRECATION") (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
                }
                val screenOrientation = when (rotation) {
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
    fun isPreviewActive(): Boolean {
        return streamManager.isOnPreview()
    }

    fun startPreview(openGlView: OpenGlView) {
        try {
            Log.d(TAG, "Запуск preview")
            this.openGlView = openGlView

            // Необходимо обеспечить корректное освобождение ресурсов перед запуском нового preview
            if (streamManager.isOnPreview()) {
                Log.d(TAG, "Останавливаем существующий preview перед запуском нового")
                streamManager.stopPreview()
            }

            streamManager.startPreview(openGlView)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при запуске preview: ${e.message}", e)
        }
    }

    fun stopPreview() {
        try {
            Log.d(TAG, "Остановка preview")
            if (streamManager.isOnPreview()) {
                streamManager.stopPreview()
            }
            this.openGlView = null
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при остановке preview: ${e.message}", e)
        }
    }

    fun toggleLantern(): Boolean {
        try {
            Log.d(TAG, "Вызов toggleLantern")

            // Не можем использовать CameraManager напрямую, так как камера уже используется
            // Вместо этого воспользуемся методом streamManager
            return try {
                when (getStreamTypeFromProtocol(protocol)) {
                    StreamType.RTMP -> {
                        val camera = streamManager.getStream() as com.pedro.library.rtmp.RtmpCamera2
                        isFlashOn = !isFlashOn
                        if (isFlashOn) {
                            camera.enableLantern()
                        } else {
                            camera.disableLantern()
                        }
                        isFlashOn
                    }

                    StreamType.RTSP -> {
                        val camera = streamManager.getStream() as com.pedro.library.rtsp.RtspCamera2
                        isFlashOn = !isFlashOn
                        if (isFlashOn) {
                            camera.enableLantern()
                        } else {
                            camera.disableLantern()
                        }
                        isFlashOn
                    }

                    StreamType.SRT -> {
                        val camera = streamManager.getStream() as com.pedro.library.srt.SrtCamera2
                        isFlashOn = !isFlashOn
                        if (isFlashOn) {
                            camera.enableLantern()
                        } else {
                            camera.disableLantern()
                        }
                        isFlashOn
                    }

                    StreamType.UDP -> {
                        val camera = streamManager.getStream() as com.pedro.library.udp.UdpCamera2
                        isFlashOn = !isFlashOn
                        if (isFlashOn) {
                            camera.enableLantern()
                        } else {
                            camera.disableLantern()
                        }
                        isFlashOn
                    }
                }
            } catch (e: ClassCastException) {
                Log.e(TAG, "Ошибка приведения типа при управлении фонариком", e)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при toggleLantern: ${e.message}", e)
            return false
        }
    }

    fun switchCamera() {
        try {
            Log.d(TAG, "Вызов метода switchCamera")

            // Используем API библиотеки для переключения камеры
            val switchResult = when (getStreamTypeFromProtocol(protocol)) {
                StreamType.RTMP -> {
                    val camera = streamManager.getStream() as com.pedro.library.rtmp.RtmpCamera2
                    camera.switchCamera()
                }

                StreamType.RTSP -> {
                    val camera = streamManager.getStream() as com.pedro.library.rtsp.RtspCamera2
                    camera.switchCamera()
                }

                StreamType.SRT -> {
                    val camera = streamManager.getStream() as com.pedro.library.srt.SrtCamera2
                    camera.switchCamera()
                }

                StreamType.UDP -> {
                    val camera = streamManager.getStream() as com.pedro.library.udp.UdpCamera2
                    camera.switchCamera()
                }
            }

            // Обновляем индекс текущей камеры после переключения
            currentCameraId = (currentCameraId + 1) % cameraIds.size

            Log.d(TAG, "Камера переключена на ${cameraIds[currentCameraId]}, результат: $switchResult")

            // Сбрасываем состояние фонарика при переключении камер
            isFlashOn = false
        } catch (e: ClassCastException) {
            Log.e(TAG, "Ошибка приведения типа при переключении камеры", e)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при переключении камеры: ${e.message}", e)
        }
    }

    fun setZoom(motionEvent: MotionEvent) {
        cameraManager.setZoom(motionEvent)
    }

    fun tapToFocus(motionEvent: MotionEvent) {
        cameraManager.tapToFocus(motionEvent)
    }

    fun takePhoto() {
        try {
            Log.d(TAG, "Делаем снимок")

            // Используем streamManager для получения glInterface
            val glInterface = streamManager.getGlInterface()

            // Проверяем, что glInterface - это тот тип, который нам нужен
            if (glInterface is com.pedro.library.view.GlInterface) {
                glInterface.takePhoto { bitmap ->
                    val handlerThread = android.os.HandlerThread("HandlerThread")
                    handlerThread.start()
                    Handler(handlerThread.looper).post {
                        saveBitmapToGallery(bitmap)
                    }
                }
            } else {
                Log.e(TAG, "glInterface неправильного типа: ${glInterface.javaClass.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при создании снимка: ${e.message}", e)
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
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
                    put(
                        android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EmerlinkStream"
                    )
                }

                val resolver = applicationContext.contentResolver
                val uri = resolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                )

                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                        applicationContext.sendBroadcast(Intent(ACTION_TOOK_PICTURE))
                        notificationHelper.updateNotification(getString(R.string.saved_photo), true)
                    } ?: run {
                        notificationHelper.updateNotification(getString(R.string.saved_photo_failed), false)
                    }
                }
            } else {
                // For older versions, use direct file saving
                val file = File(filePath)
                val fos = java.io.FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                fos.flush()
                fos.close()

                // Make the image visible in gallery
                MediaScannerConnection.scanFile(
                    applicationContext, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null
                )

                applicationContext.sendBroadcast(Intent(ACTION_TOOK_PICTURE))
                notificationHelper.updateNotification(getString(R.string.saved_photo), true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении фото: ${e.message}", e)
            notificationHelper.updateNotification(
                getString(R.string.saved_photo_failed) + ": " + e.message, false
            )
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
            try {
                // Библиотека RTMP-RTSP использует класс CameraHelper для управления камерой
                // Попробуем получить доступ к камерам через android.hardware.camera2.CameraManager
                val cameraManager =
                    applicationContext.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                cameraIds.addAll(cameraManager.cameraIdList.toList())

                Log.d(TAG, "Получены идентификаторы камер через CameraManager: $cameraIds")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при получении списка камер", e)

                // Резервный вариант - добавляем стандартные идентификаторы "0" и "1"
                // Обычно "0" - это задняя камера, "1" - фронтальная
                if (cameraIds.isEmpty()) {
                    cameraIds.add("0") // Задняя камера
                    cameraIds.add("1") // Фронтальная камера
                    Log.d(TAG, "Используем стандартные идентификаторы камер: $cameraIds")
                }
            }
        }
    }

    private fun loadPreferences() {
        val preferencesLoader = PreferencesLoader(applicationContext)
        val settings = preferencesLoader.loadPreferences(preferences)

        // Сохраняем старый протокол для проверки изменений
        val oldProtocol = protocol

        // Копируем все настройки из объекта settings в поля класса
        protocol = settings.protocol
        address = settings.address
        port = settings.port
        path = settings.path
        tcp = settings.tcp
        username = settings.username
        password = settings.password
        streamSelfSignedCert = settings.streamSelfSignedCert
        certFile = settings.certFile
        certPassword = settings.certPassword

        sampleRate = settings.sampleRate
        stereo = settings.stereo
        echoCancel = settings.echoCancel
        noiseReduction = settings.noiseReduction
        enableAudio = settings.enableAudio
        audioBitrate = settings.audioBitrate
        audioCodec = settings.audioCodec

        fps = settings.fps
        resolution = settings.resolution
        adaptiveBitrate = settings.adaptiveBitrate
        record = settings.record
        stream = settings.stream
        bitrate = settings.bitrate
        codec = settings.codec
        uid = settings.uid

        videoSource = settings.videoSource

        keyframeInterval = settings.keyframeInterval
        videoProfile = settings.videoProfile
        videoLevel = settings.videoLevel
        bitrateMode = settings.bitrateMode
        encodingQuality = settings.encodingQuality

        bufferSize = settings.bufferSize
        connectionTimeout = settings.connectionTimeout
        autoReconnect = settings.autoReconnect
        reconnectDelay = settings.reconnectDelay
        maxReconnectAttempts = settings.maxReconnectAttempts

        lowLatencyMode = settings.lowLatencyMode
        hardwareRotation = settings.hardwareRotation
        dynamicFps = settings.dynamicFps

        // Если протокол изменился, обновляем тип стрима
        if (protocol != oldProtocol && ::streamManager.isInitialized) {
            streamManager.setStreamType(getStreamTypeFromProtocol(protocol))
        }
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
                    LocationManager.GPS_PROVIDER, 1000, 1f, locListener!!
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

        val url = when {
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
        // Используем streamManager для инициализации, это более безопасно
        streamManager.setStreamType(getStreamTypeFromProtocol(protocol))
        // Получаем доступные камеры
        getCameraIds()
    }

    // Add this inner class inside your StreamService class
    inner class LocalBinder : Binder() {
        fun getService(): StreamService = this@StreamService
    }

    fun toggleMute(muted: Boolean) {
        try {
            Log.d(TAG, "Setting mute state to: $muted")

            when (getStreamTypeFromProtocol(protocol)) {
                StreamType.RTMP -> {
                    val camera = streamManager.getStream() as com.pedro.library.rtmp.RtmpCamera2
                    if (muted) {
                        camera.disableAudio()
                    } else {
                        camera.enableAudio()
                    }
                }

                StreamType.RTSP -> {
                    val camera = streamManager.getStream() as com.pedro.library.rtsp.RtspCamera2
                    if (muted) {
                        camera.disableAudio()
                    } else {
                        camera.enableAudio()
                    }
                }

                StreamType.SRT -> {
                    val camera = streamManager.getStream() as com.pedro.library.srt.SrtCamera2
                    if (muted) {
                        camera.disableAudio()
                    } else {
                        camera.enableAudio()
                    }
                }

                StreamType.UDP -> {
                    val camera = streamManager.getStream() as com.pedro.library.udp.UdpCamera2
                    if (muted) {
                        camera.disableAudio()
                    } else {
                        camera.enableAudio()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mute state: ${e.message}", e)
        }
    }
}
