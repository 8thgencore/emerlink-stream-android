package net.emerlink.stream.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
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
import android.view.MotionEvent
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.pedro.common.ConnectChecker
import com.pedro.library.util.BitrateAdapter
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.R
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.model.StreamSettings
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
        val screenshotTaken = MutableLiveData<Boolean>()
    }

    private lateinit var preferences: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    private lateinit var errorHandler: ErrorHandler
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var streamManager: StreamManager
    private lateinit var cameraManager: CameraManager

    // Replace all individual settings variables with a single StreamSettings object
    private lateinit var streamSettings: StreamSettings

    // Stream objects
    private var bitrateAdapter: BitrateAdapter? = null

    // State
    private var exiting = false
    private val binder = LocalBinder()

    // New variables
    private var currentCameraId = 0
    private var isFlashOn = false

    // Recording
    private lateinit var folder: File
    private var currentDateAndTime: String = ""

    // Location
    private var locListener: StreamLocationListener? = null
    private var locManager: LocationManager? = null

    // Camera
    private var prepareAudio = false
    private var prepareVideo = false
    private var openGlView: OpenGlView? = null
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

    // TODO(8thgencore): Remove these
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

    // Добавление поля для хранения ссылки на BroadcastReceiver
    private var commandReceiver: BroadcastReceiver? = null

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
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

        // Initialize with default settings
        streamSettings = StreamSettings()

        // Load actual settings from preferences
        loadPreferences()

        // Initialize stream manager
        streamManager = StreamManager(this, this, errorHandler)
        streamManager.setStreamType(getStreamTypeFromProtocol(streamSettings.protocol))

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

        // Добавление регистрации BroadcastReceiver для команд
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_START_STREAM)
            addAction(ACTION_STOP_STREAM)
            addAction(ACTION_EXIT_APP)
        }

        val commandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "Получен интент: ${intent.action}")
                when (intent.action) {
                    ACTION_START_STREAM -> startStream()
                    ACTION_STOP_STREAM -> stopStream(null, null)
                    ACTION_EXIT_APP -> {
                        exiting = true
                        stopStream(null, null)
                        stopSelf()
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, intentFilter)
        }

        // Сохраняем ссылку на приемник для дальнейшей отмены регистрации
        this.commandReceiver = commandReceiver
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
        try {
            Log.d(TAG, "onDestroy")

            if (streamManager.isStreaming()) {
                stopStream(null, null)
            }

            // Очищаем ресурсы StreamManager
            streamManager.destroy()

            // Отменяем регистрацию для событий изменения настроек
            preferences.unregisterOnSharedPreferenceChangeListener(this)

            // Отписываемся от BroadcastReceiver
            if (commandReceiver != null) {
                unregisterReceiver(commandReceiver)
                commandReceiver = null
            }

            observer.postValue(null)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при уничтожении сервиса: ${e.message}")
        }

        super.onDestroy()
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
        if (streamSettings.adaptiveBitrate) {
            Log.d(TAG, "Setting adaptive bitrate")
            bitrateAdapter = BitrateAdapter { bitrate ->
                streamManager.setVideoBitrateOnFly(bitrate)
            }
            bitrateAdapter?.setMaxBitrate(streamSettings.bitrate * 1024)
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

                // Исправляем критическую ошибку - не пытаемся получить display из сервиса
                // В сервисе невозможно получить display в Android 12+
                // Вместо этого используем предопределенную ориентацию или данные от приложения
                val screenOrientation = 0 // По умолчанию портретная ориентация

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

    /** Запускает предпросмотр камеры */
    fun startPreview(view: OpenGlView) {
        try {
            val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            streamManager.startPreview(view, isPortrait)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при запуске предпросмотра: ${e.message}", e)
        }
    }

    fun stopPreview() {
        try {
            Log.d(TAG, "Остановка preview с правильным освобождением ресурсов")

            if (streamManager.isOnPreview()) {
                streamManager.stopPreview()

                Handler(mainLooper).postDelayed(
                    {
                        try {
                            // Освобождаем ресурсы OpenGL
                            openGlView?.let {
                                Log.d(TAG, "Очистка ресурсов OpenGlView")
                                // Метод releaseTextureID может быть доступен в
                                // некоторых реализациях
                                try {
                                    val releaseMethod = it.javaClass.getMethod("release")
                                    releaseMethod.invoke(it)
                                } catch (e: Exception) {
                                    Log.d(TAG, "Метод release не найден: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(
                                TAG, "Ошибка при очистке ресурсов OpenGlView: ${e.message}"
                            )
                        } finally {
                            openGlView = null
                        }
                    }, 200
                )
            } else {
                openGlView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при остановке preview: ${e.message}", e)
            openGlView = null
        }
    }

    fun toggleLantern(): Boolean {
        Log.d(TAG, "Вызов toggleLantern")
        return try {
            val camera = streamManager.getStream() as com.pedro.library.base.Camera2Base
            isFlashOn = !isFlashOn
            if (isFlashOn) {
                camera.enableLantern()
            } else {
                camera.disableLantern()
            }
            isFlashOn
        } catch (e: ClassCastException) {
            Log.e(TAG, "Ошибка приведения типа при управлении фонариком", e)
            false
        }
    }

    /**
     * Switches between front and back cameras
     * @return true if camera switched successfully, false otherwise
     */
    fun switchCamera(): Boolean {
        try {
            Log.d(TAG, "Service: Switching camera")
            val result = streamManager.switchCamera()

            // Update the current camera ID if switch was successful
            if (result) {
                currentCameraId = if (currentCameraId == 0) 1 else 0
                Log.d(TAG, "Camera switched to ID: $currentCameraId")
            } else {
                Log.e(TAG, "Failed to switch camera")
            }

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error in service when switching camera: ${e.message}", e)
            return false
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
                        Log.d(TAG, "Отправка бродкаста ACTION_TOOK_PICTURE")
                        applicationContext.sendBroadcast(Intent(ACTION_TOOK_PICTURE))
                        notificationHelper.updateNotification(getString(R.string.saved_photo), true)
                    } ?: run {
                        notificationHelper.updateNotification(
                            getString(R.string.saved_photo_failed), false
                        )
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

                Log.d(TAG, "Отправка бродкаста ACTION_TOOK_PICTURE")
                applicationContext.sendBroadcast(Intent(ACTION_TOOK_PICTURE))
                notificationHelper.updateNotification(getString(R.string.saved_photo), true)
            }

            // Используем LiveData вместо бродкаста
            Handler(android.os.Looper.getMainLooper()).post {
                Log.d(TAG, "Уведомление через LiveData о скриншоте")
                screenshotTaken.value = true
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
        if (streamSettings.videoSource == PreferenceKeys.VIDEO_SOURCE_DEFAULT) {
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

        // Save old protocol for checking changes
        val oldProtocol = streamSettings.protocol

        // Load all settings at once into streamSettings
        streamSettings = preferencesLoader.loadPreferences(preferences)

        // If protocol changed, update stream type
        if (streamSettings.protocol != oldProtocol && ::streamManager.isInitialized) {
            streamManager.setStreamType(getStreamTypeFromProtocol(streamSettings.protocol))
        }
    }

    fun startStream() {
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

            if (streamSettings.stream) {
                startStreaming()
            }

            if (streamSettings.record) {
                startRecording()
            }

            // Запуск отслеживания местоположения только при наличии разрешений
            try {
                // Проверяем наличие разрешений
                if (hasLocationPermission()) {
                    locManager?.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 1000, 1f, locListener!!
                    )
                } else {
                    Log.w(TAG, "Отсутствуют разрешения на местоположение, отслеживание отключено")
                }
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
        try {
            Log.d(TAG, "Starting streaming")

            // Получаем URL из настроек
            val url = buildStreamUrl()

            // Получаем разрешение из настроек
            val (videoWidth, videoHeight) = parseVideoResolution()

            streamManager.switchStreamResolution(videoWidth, videoHeight)

            // Запускаем стрим
            streamManager.startStream(
                url, streamSettings.protocol, streamSettings.username, streamSettings.password, streamSettings.tcp
            )

            notificationHelper.updateNotification(getString(R.string.streaming), true)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting streaming: ${e.message}", e)
            errorHandler.handleStreamError(e)
        }
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

    fun stopStream(message: String?, action: String?) {
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
        streamManager.setStreamType(getStreamTypeFromProtocol(streamSettings.protocol))
        getCameraIds()
    }

    // Add this inner class inside your StreamService class
    inner class LocalBinder : Binder() {
        fun getService(): StreamService = this@StreamService
    }

    /** Переключает состояние аудио (вкл/выкл) */
    fun toggleMute(muted: Boolean) {
        try {
            Log.d(TAG, "Setting mute state to: $muted")

            when {
                muted -> streamManager.disableAudio()
                else -> streamManager.enableAudio()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mute state: ${e.message}", e)
        }
    }

    /** Парсит настройки разрешения видео */
    private fun parseVideoResolution(): Pair<Int, Int> {
        val videoResolution = preferences.getString(
            PreferenceKeys.VIDEO_RESOLUTION, PreferenceKeys.VIDEO_RESOLUTION_DEFAULT
        ) ?: "1920x1080"
        val dimensions = videoResolution.lowercase(Locale.getDefault()).replace("х", "x").split("x")
        val videoWidth = if (dimensions.isNotEmpty()) dimensions[0].toIntOrNull() ?: 1920 else 1920
        val videoHeight = if (dimensions.size >= 2) dimensions[1].toIntOrNull() ?: 1080 else 1080
        return Pair(videoWidth, videoHeight)
    }

    /** Формирует URL стрима на основе настроек */
    private fun buildStreamUrl(): String {
        return when {
            streamSettings.protocol.startsWith("rtmp") -> {
                val prefix = if (streamSettings.protocol == "rtmps") "rtmps" else "rtmp"
                "$prefix://${streamSettings.address}:${streamSettings.port}/${streamSettings.path}"
            }

            streamSettings.protocol.startsWith("rtsp") -> {
                val prefix = if (streamSettings.protocol == "rtsps") "rtsps" else "rtsp"
                "$prefix://${streamSettings.address}:${streamSettings.port}/${streamSettings.path}"
            }

            streamSettings.protocol == "srt" -> {
                "srt://${streamSettings.address}:${streamSettings.port}"
            }

            else -> {
                "udp://${streamSettings.address}:${streamSettings.port}"
            }
        }
    }

    /** Проверяет наличие разрешений на доступ к местоположению */
    private fun hasLocationPermission(): Boolean {
        return (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED || checkSelfPermission(
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
}
