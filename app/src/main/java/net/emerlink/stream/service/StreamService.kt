package net.emerlink.stream.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
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
import android.os.Looper
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
import net.emerlink.stream.notification.NotificationManager
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

        const val ACTION_START_STREAM = "net.emerlink.stream.START_STREAM"
        const val ACTION_STOP_STREAM = "net.emerlink.stream.STOP_STREAM"
        const val ACTION_EXIT_APP = "net.emerlink.stream.EXIT_APP"
        const val ACTION_AUTH_ERROR = "net.emerlink.stream.AUTH_ERROR"
        const val ACTION_CONNECTION_FAILED = "net.emerlink.stream.CONNECTION_FAILED"
        const val ACTION_TOOK_PICTURE = "net.emerlink.stream.TOOK_PICTURE"
        const val ACTION_NEW_BITRATE = "net.emerlink.stream.NEW_BITRATE"
        const val ACTION_LOCATION_CHANGE = "net.emerlink.stream.LOCATION_CHANGE"
        const val ACTION_DISMISS_ERROR = "net.emerlink.stream.DISMISS_ERROR"

        val observer = MutableLiveData<StreamService?>()
        val screenshotTaken = MutableLiveData<Boolean>()
    }

    private lateinit var preferences: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    private lateinit var errorHandler: ErrorHandler
    private lateinit var streamManager: StreamManager
    private lateinit var cameraManager: CameraManager

    // Replace all individual settings variables with a single StreamSettings object
    lateinit var streamSettings: StreamSettings

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

    // Добавьте флаг для отслеживания состояния превью
    private var isPreviewActive = false

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        errorHandler = ErrorHandler(this)
        notificationManager = NotificationManager.getInstance(this)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.registerOnSharedPreferenceChangeListener(this)

        folder = PathUtils.getRecordPath(this)

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
        locManager = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
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
            addAction(ACTION_DISMISS_ERROR)
        }

        val commandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "Получен интент: ${intent.action}")
                try {
                    when (intent.action) {
                        ACTION_START_STREAM -> startStream()
                        ACTION_STOP_STREAM -> {
                            Log.d(TAG, "Обработка команды остановки стрима из BroadcastReceiver")
                            // Сначала очищаем уведомление
                            notificationManager.clearAllNotifications()
                            stopStream(null, null)
                        }
                        ACTION_EXIT_APP -> {
                            Log.d(TAG, "Обработка команды выхода из приложения из BroadcastReceiver")
                            exiting = true
                            notificationManager.clearAllNotifications()
                            stopStream(null, null)
                            stopSelf()
                        }
                        ACTION_DISMISS_ERROR -> {
                            Log.d(TAG, "Обработка команды скрытия ошибки из BroadcastReceiver")
                            notificationManager.clearAllNotifications()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при обработке интента в BroadcastReceiver: ${e.message}", e)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, intentFilter)
        }

        // Сохраняем ссылку на приемник для дальнейшей отмены регистрации
        this.commandReceiver = commandReceiver
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        try {
            intent?.action?.let { action ->
                Log.d(TAG, "Обработка действия: $action")
                
                when (action) {
                    ACTION_START_STREAM -> {
                        startStream()
                    }
                    ACTION_STOP_STREAM -> {
                        Log.d(TAG, "Обработка команды остановки стрима")
                        // При остановке через уведомление закрываем само уведомление
                        notificationManager.clearAllNotifications()
                        stopStream(null, null)
                    }
                    ACTION_EXIT_APP -> {
                        Log.d(TAG, "Обработка команды выхода из приложения")
                        exiting = true
                        notificationManager.clearAllNotifications()
                        stopStream(null, null)
                        stopSelf()
                    }
                    ACTION_DISMISS_ERROR -> {
                        Log.d(TAG, "Обработка команды скрытия ошибки")
                        notificationManager.clearAllNotifications()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при обработке команды: ${e.message}", e)
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
        
        try {
            // Принудительно останавливаем стрим сначала
            if (streamManager.isStreaming()) {
                Log.d(TAG, "Принудительная остановка стрима из-за ошибки подключения")
                streamManager.stopStream()
            }
            
            // Предотвращаем автоматические повторные попытки подключения
            prepareAudio = false
            prepareVideo = false
            
            // Создаем текст ошибки
            val errorText = getString(R.string.connection_failed) + ": " + reason
            Log.d(TAG, "Создание уведомления об ошибке: $errorText")
            
            // Очищаем все существующие уведомления
            notificationManager.clearAllNotifications()
            
            // Показываем уведомление об ошибке
            notificationManager.showErrorNotification(errorText)
            
            // Отправляем бродкаст о проблеме подключения
            val intent = Intent(ACTION_CONNECTION_FAILED)
            applicationContext.sendBroadcast(intent)
            
            // После отображения уведомления вызываем полную остановку стрима
            Handler(Looper.getMainLooper()).postDelayed({
                stopStream(null, null, NotificationManager.ACTION_NONE, false)
            }, 500)
            
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при обработке сбоя подключения: ${e.message}", e)
            // Аварийный случай - пытаемся остановить все, что можно
            try {
                streamManager.stopStream()
                notificationManager.clearAllNotifications()
                notificationManager.showErrorNotification("Критическая ошибка: ${e.message}")
            } catch (e2: Exception) {
                Log.e(TAG, "Двойная ошибка: ${e2.message}", e2)
            }
        }
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

    /** Запускает предпросмотр камеры */
    fun startPreview(view: OpenGlView) {
        if (isPreviewActive) {
            Log.d(TAG, "Предпросмотр уже активен, игнорируем запрос")
            return
        }
        
        try {
            // Сохраняем ссылку на OpenGlView
            openGlView = view
            
            // Запускаем предпросмотр
            val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            
            // Настраиваем видео
            streamManager.prepareVideo()
            
            // Запускаем предпросмотр
            streamManager.startPreview(view, isPortrait)
            
            isPreviewActive = true
            Log.d(TAG, "Предпросмотр успешно запущен")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при запуске предпросмотра: ${e.message}", e)
        }
    }

    /** Останавливает предпросмотр камеры */
    fun stopPreview() {
        if (!isPreviewActive) {
            Log.d(TAG, "Предпросмотр не активен, игнорируем запрос остановки")
            return
        }
        
        try {
            Log.d(TAG, "Остановка предпросмотра")
            streamManager.stopPreview()
            isPreviewActive = false
            Log.d(TAG, "Предпросмотр успешно остановлен")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при остановке предпросмотра: ${e.message}", e)
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
                        notificationManager.showPhotoNotification(getString(R.string.saved_photo))
                    } ?: run {
                        notificationManager.showErrorNotification(getString(R.string.saved_photo_failed))
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
                notificationManager.showPhotoNotification(getString(R.string.saved_photo))
            }

            // Используем LiveData вместо бродкаста
            Handler(Looper.getMainLooper()).post {
                Log.d(TAG, "Уведомление через LiveData о скриншоте")
                screenshotTaken.value = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении фото: ${e.message}", e)
            notificationManager.showErrorNotification(
                getString(R.string.saved_photo_failed) + ": " + e.message
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
                    applicationContext.getSystemService(CAMERA_SERVICE) as android.hardware.camera2.CameraManager
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
            notificationManager.showErrorNotification(getString(R.string.failed_to_prepare))
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

            // Только кнопка "Стоп" для стрима
            notificationManager.showStreamingNotification(
                getString(R.string.streaming), 
                true,
                NotificationManager.ACTION_STOP_ONLY
            )
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
                notificationManager.showErrorNotification(getString(R.string.failed_to_record))
                return
            }
        }

        val filename = "EmerlinkStream_$currentDateAndTime.mp4"
        val filePath = "${folder.absolutePath}/$filename"

        Log.d(TAG, "Recording to: $filePath")

        streamManager.startRecord(filePath)
        notificationManager.showStreamingNotification(
            getString(R.string.recording), 
            true,
            NotificationManager.ACTION_STOP_ONLY
        )
    }

    fun stopStream(message: String?, action: String?, actionType: Int = NotificationManager.ACTION_ALL, isError: Boolean = false) {
        Log.d(TAG, "Stopping stream with message: $message, isError: $isError")

        try {
            // Очищаем ВСЕ существующие уведомления перед отображением нового,
            // особенно важно для ошибок
            if (isError) {
                notificationManager.clearAllNotifications()
            }

            // Убедимся, что стрим действительно останавливается
            if (streamManager.isStreaming()) {
                Log.d(TAG, "Stopping active stream")
                streamManager.stopStream()
            }

            if (streamManager.isRecording()) {
                Log.d(TAG, "Stopping active recording")
                streamManager.stopRecord()
            }

            prepareAudio = false
            prepareVideo = false

            // Stop location updates
            try {
                if (locListener != null) {
                    locManager?.removeUpdates(locListener!!)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping location updates", e)
            }

            // Stop sensor updates
            try {
                sensorManager.unregisterListener(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering sensor listener", e)
            }

            // Показываем сообщение, если оно есть
            if (message != null) {
                Log.d(TAG, "Отображение уведомления: $message, тип действия: $actionType, isError: $isError")
                if (isError) {
                    notificationManager.showErrorNotification(message, actionType)
                } else {
                    notificationManager.showStreamingNotification(message, false, actionType)
                }
            }

            if (action != null) {
                val intent = Intent(action)
                applicationContext.sendBroadcast(intent)
            }

            if (exiting) {
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopStream: ${e.message}", e)
            errorHandler.handleStreamError(e)
            
            // Даже при ошибке в stopStream, пытаемся показать уведомление
            if (message != null) {
                // Обязательно очищаем все уведомления перед показом ошибки
                notificationManager.clearAllNotifications()
                notificationManager.showErrorNotification(message + " (${e.message})")
            }
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

    /** Полностью останавливает камеру и освобождает все ресурсы */
    fun releaseCamera() {
        try {
            Log.d(TAG, "Полное освобождение ресурсов камеры")
            
            // Остановка предпросмотра
            if (streamManager.isOnPreview()) {
                streamManager.stopPreview()
            }
            
            // Освобождение камеры
            try {
                streamManager.releaseCamera()
                Log.d(TAG, "Ресурсы камеры успешно освобождены")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при освобождении камеры: ${e.message}", e)
            }
            
            // Обнуление ссылки на OpenGlView
            openGlView = null
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при освобождении ресурсов камеры: ${e.message}", e)
        }
    }

    /** Запускает предпросмотр камеры с полной перезагрузкой ресурсов */
    fun restartPreview(view: OpenGlView) {
        try {
            Log.d(TAG, "Полный перезапуск предпросмотра с пересозданием камеры")
            
            // Сначала полностью освобождаем ресурсы
            if (streamManager.isOnPreview()) {
                streamManager.stopPreview()
            }
            
            // Небольшая задержка, чтобы система успела обработать освобождение ресурсов
            Thread.sleep(50)
            
            // Сохраняем новую ссылку на OpenGlView
            openGlView = view
            
            // Перезапускаем предпросмотр
            val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            
            // Гарантируем, что камера будет переинициализирована
            streamManager.prepareVideo()
            
            // Запускаем предпросмотр
            streamManager.startPreview(view, isPortrait)
            
            Log.d(TAG, "Предпросмотр успешно перезапущен")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при перезапуске предпросмотра: ${e.message}", e)
            try {
                // В случае ошибки пытаемся освободить все ресурсы
                streamManager.stopPreview()
                streamManager.releaseCamera()
            } catch (e2: Exception) {
                Log.e(TAG, "Ошибка при очистке после сбоя: ${e2.message}", e2)
            }
        }
    }

    // В StreamService добавим метод проверки состояния предпросмотра
    fun isPreviewRunning(): Boolean {
        return isPreviewActive
    }
}
