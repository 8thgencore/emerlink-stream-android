package net.emerlink.stream.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.pedro.common.ConnectChecker
import com.pedro.library.util.BitrateAdapter
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.R
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.data.preferences.PreferencesLoader
import net.emerlink.stream.model.StreamSettings
import net.emerlink.stream.notification.NotificationManager
import net.emerlink.stream.service.camera.CameraManager
import net.emerlink.stream.service.camera.ICameraManager
import net.emerlink.stream.service.location.StreamLocationListener
import net.emerlink.stream.service.stream.StreamManager
import net.emerlink.stream.util.AppIntentActions
import net.emerlink.stream.util.ErrorHandler
import net.emerlink.stream.util.PathUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StreamService : Service(), ConnectChecker, SharedPreferences.OnSharedPreferenceChangeListener,
    SensorEventListener {
    companion object {
        private const val TAG = "StreamService"

        val observer = MutableLiveData<StreamService?>()
        val screenshotTaken = MutableLiveData<Boolean>()
    }

    private lateinit var preferences: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    private lateinit var errorHandler: ErrorHandler
    internal lateinit var streamManager: StreamManager
    private lateinit var cameraManager: ICameraManager

    lateinit var streamSettings: StreamSettings

    private var bitrateAdapter: BitrateAdapter? = null

    private var exiting = false
    private val binder = LocalBinder()

    private var currentCameraId = 0
    private var isFlashOn = false

    private lateinit var folder: File
    private var currentDateAndTime: String = ""

    private var locListener: StreamLocationListener? = null
    private var locManager: LocationManager? = null

    private var prepareAudio = false
    private var prepareVideo = false
    private var openGlView: OpenGlView? = null
    private val cameraIds = ArrayList<String>()

    private lateinit var sensorManager: SensorManager
    private lateinit var magnetometer: android.hardware.Sensor
    private lateinit var accelerometer: android.hardware.Sensor
    private val gravityData = FloatArray(3)
    private val geomagneticData = FloatArray(3)
    private var hasGravityData = false
    private var hasGeomagneticData = false
    private var rotationInDegrees: Double = 0.0

    private var keyframeInterval: Int = 0
    private var videoProfile: String = ""
    private var videoLevel: String = ""
    private var bitrateMode: String = ""
    private var encodingQuality: String = ""

    private var bufferSize: Int = 0
    private var connectionTimeout: Int = 0
    private var autoReconnect: Boolean = false
    private var reconnectDelay: Int = 0
    private var maxReconnectAttempts: Int = 0

    private var lowLatencyMode: Boolean = false
    private var hardwareRotation: Boolean = false
    private var dynamicFps: Boolean = false

    private var commandReceiver: BroadcastReceiver? = null

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

        streamSettings = StreamSettings()

        loadPreferences()

        streamManager = StreamManager(this, this, errorHandler)
        streamManager.setStreamType(streamSettings.protocol)

        cameraManager = CameraManager(this, { streamManager.getVideoSource() }, { streamManager.getCameraInterface() })

        observer.postValue(this)

        locListener = StreamLocationListener(this)
        locManager = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        magnetometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)!!
        accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)!!

        getCameraIds()

        initializeStream()

        val intentFilter = IntentFilter().apply {
            addAction(AppIntentActions.ACTION_START_STREAM)
            addAction(AppIntentActions.ACTION_STOP_STREAM)
            addAction(AppIntentActions.ACTION_EXIT_APP)
            addAction(AppIntentActions.ACTION_DISMISS_ERROR)
        }

        val commandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "Получен интент: ${intent.action}")
                try {
                    when (intent.action) {
                        AppIntentActions.ACTION_START_STREAM -> startStream()
                        AppIntentActions.ACTION_STOP_STREAM -> {
                            Log.d(
                                TAG, "Обработка команды остановки стрима из BroadcastReceiver"
                            )
                            notificationManager.clearStreamingNotifications()
                            stopStream(null, null)
                        }

                        AppIntentActions.ACTION_EXIT_APP -> {
                            Log.d(
                                TAG, "Обработка команды выхода из приложения из BroadcastReceiver"
                            )
                            exiting = true
                            notificationManager.clearAllNotifications()
                            stopStream(null, null)
                            stopSelf()
                        }

                        AppIntentActions.ACTION_DISMISS_ERROR -> {
                            Log.d(
                                TAG, "Обработка команды скрытия ошибки из BroadcastReceiver"
                            )
                            notificationManager.clearErrorNotifications()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(
                        TAG, "Ошибка при обработке интента в BroadcastReceiver: ${e.message}", e
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, intentFilter)
        }

        this.commandReceiver = commandReceiver
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        intent?.action?.let { action ->
            Log.d(TAG, "Processing action: $action")
            try {
                when (action) {
                    AppIntentActions.ACTION_START_STREAM -> startStream()
                    
                    AppIntentActions.ACTION_STOP_STREAM -> {
                        Log.d(TAG, "Processing stop stream command")
                        stopStream(null, null)
                    }
                    
                    AppIntentActions.ACTION_EXIT_APP -> {
                        Log.d(TAG, "Processing exit app command")
                        exiting = true
                        notificationManager.clearAllNotifications()
                        stopStream(null, null)
                        stopSelf()
                    }
                    
                    AppIntentActions.ACTION_DISMISS_ERROR -> {
                        Log.d(TAG, "Processing dismiss error command")
                        notificationManager.clearErrorNotifications()
                    }
                    
                    else -> Log.d(TAG, "Unknown command: $action")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command: ${e.message}", e)
                errorHandler.handleStreamError(e)
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

            preferences.unregisterOnSharedPreferenceChangeListener(this)

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

    override fun onAuthError() {
        Log.d(TAG, "Auth error")
        stopStream(getString(R.string.auth_error), AppIntentActions.ACTION_AUTH_ERROR, true)
    }

    override fun onAuthSuccess() {
        Log.d(TAG, "Auth success")
    }

    override fun onConnectionFailed(reason: String) {
        Log.e(TAG, "Connection failed: $reason")

        try {
            if (streamManager.isStreaming()) {
                Log.d(TAG, "Принудительная остановка стрима из-за ошибки подключения")
                streamManager.stopStream()
                
                // Send broadcast to update UI immediately
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(AppIntentActions.BROADCAST_STREAM_STOPPED))

                val errorText = when {
                    reason.contains("461") -> getString(R.string.error_unsupported_transport)
                    else -> getString(R.string.connection_failed) + ": " + reason
                }

                Log.d(TAG, "Создание уведомления об ошибке: $errorText")

                notificationManager.showErrorNotification(errorText)

                val intent = Intent(AppIntentActions.ACTION_CONNECTION_FAILED)
                applicationContext.sendBroadcast(intent)

                Handler(Looper.getMainLooper()).postDelayed(
                    { stopStream(null, null, false) }, 500
                )
            }

            prepareAudio = false
            prepareVideo = false
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при обработке сбоя подключения: ${e.message}", e)

            try {
                streamManager.stopStream()
                
                // Send broadcast even in case of error
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(AppIntentActions.BROADCAST_STREAM_STOPPED))

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

    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            PreferenceKeys.VIDEO_RESOLUTION -> {
                Log.d(TAG, "Обнаружено изменение разрешения в настройках")
                streamManager.handleResolutionChange()
            }
        }
        loadPreferences()
    }

    /** Запускает предпросмотр камеры */
    fun startPreview(view: OpenGlView) {
        if (isPreviewActive) {
            Log.d(TAG, "Предпросмотр уже активен, игнорируем запрос")
            return
        }

        try {
            openGlView = view

            // Важно: сначала подготовить видео с правильной ориентацией
            streamManager.prepareVideo()
            // Затем запустить предпросмотр с той же ориентацией
            streamManager.startPreview(view)

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
        Log.d(TAG, "Calling toggleLantern")
        val result = cameraManager.toggleLantern()
        isFlashOn = result
        return result
    }

    /**
     * Switches between front and back cameras
     * @return true if camera switched successfully, false otherwise
     */
    fun switchCamera(): Boolean {
        try {
            Log.d(TAG, "Service: Switching camera")
            val result = cameraManager.switchCamera()

            if (result) {
                currentCameraId = cameraManager.getCurrentCameraId()
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

            val glInterface = streamManager.getGlInterface()

            if (glInterface is com.pedro.library.view.GlInterface) {
                glInterface.takePhoto { bitmap ->
                    val handlerThread = android.os.HandlerThread("HandlerThread")
                    handlerThread.start()
                    Handler(handlerThread.looper).post { saveBitmapToGallery(bitmap) }
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
                        applicationContext.sendBroadcast(Intent(AppIntentActions.ACTION_TOOK_PICTURE))
                        notificationManager.showPhotoNotification(getString(R.string.saved_photo))
                    } ?: run {
                        notificationManager.showErrorNotification(
                            getString(R.string.saved_photo_failed)
                        )
                    }
                }
            } else {
                val file = File(filePath)
                val fos = java.io.FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                fos.flush()
                fos.close()

                MediaScannerConnection.scanFile(
                    applicationContext, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null
                )

                Log.d(TAG, "Отправка бродкаста ACTION_TOOK_PICTURE")
                applicationContext.sendBroadcast(Intent(AppIntentActions.ACTION_TOOK_PICTURE))
                notificationManager.showPhotoNotification(getString(R.string.saved_photo))
            }

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

    private fun getCameraIds() {
        if (streamSettings.videoSource == PreferenceKeys.VIDEO_SOURCE_DEFAULT) {
            try {
                val cameraManager =
                    applicationContext.getSystemService(CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                cameraIds.addAll(cameraManager.cameraIdList.toList())

                Log.d(TAG, "Получены идентификаторы камер через CameraManager: $cameraIds")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при получении списка камер", e)

                if (cameraIds.isEmpty()) {
                    cameraIds.add("0")
                    cameraIds.add("1")
                    Log.d(TAG, "Используем стандартные идентификаторы камер: $cameraIds")
                }
            }
        }
    }

    private fun loadPreferences() {
        val preferencesLoader = PreferencesLoader()

        val oldProtocol = streamSettings.protocol

        streamSettings = preferencesLoader.loadPreferences(preferences)

        if (streamSettings.protocol != oldProtocol && ::streamManager.isInitialized) {
            streamManager.setStreamType(streamSettings.protocol)
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

            // Make sure to apply the correct orientation before starting the stream
            streamManager.switchStreamResolution()

            if (streamSettings.stream) {
                startStreaming()
            }
            if (streamSettings.record) {
                startRecording()
            }

            try {
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

            val streamUrl = streamSettings.protocol.buildStreamUrl(
                address = streamSettings.address,
                port = streamSettings.port.toString(),
                path = streamSettings.path,
                username = streamSettings.username.ifEmpty { null },
                password = streamSettings.password.ifEmpty { null })
            // Log the URL (remove sensitive info in production)
            Log.d(TAG, "Stream URL: ${streamUrl.replace(Regex(":[^:/]*:[^:/]*"), ":****:****")}")

            streamManager.switchStreamResolution()

            streamManager.startStream(
                streamUrl,
                streamSettings.protocol.toString(),
                streamSettings.username,
                streamSettings.password,
                streamSettings.tcp
            )

            notificationManager.showStreamingNotification(
                getString(R.string.streaming), true, NotificationManager.ACTION_STOP_ONLY
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
            getString(R.string.recording), true, NotificationManager.ACTION_STOP_ONLY
        )
    }

    fun stopStream(message: String?, action: String?, isError: Boolean = false) {
        Log.d(TAG, "Stopping stream with message: $message, isError: $isError")

        try {
            // Clear streaming notifications first
            notificationManager.clearStreamingNotifications()

            // Stop streaming if active
            if (streamManager.isStreaming()) {
                Log.d(TAG, "Stopping active stream")
                streamManager.stopStream()
                
                // Notify UI about stream stop
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(AppIntentActions.BROADCAST_STREAM_STOPPED))
            }

            // Stop recording if active
            if (streamManager.isRecording()) {
                Log.d(TAG, "Stopping active recording")
                streamManager.stopRecord()
            }

            // Reset preparation flags
            prepareAudio = false
            prepareVideo = false

            // Stop location updates
            locListener?.let {
                try {
                    locManager?.removeUpdates(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping location updates", e)
                }
            }

            // Unregister sensor listener
            try {
                sensorManager.unregisterListener(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering sensor listener", e)
            }

            // Handle notifications based on parameters
            when {
                message != null && isError -> notificationManager.showErrorNotification(message)
                message != null -> notificationManager.showStreamingNotification(message, false)
            }

            // Send broadcast if action is specified
            action?.let {
                applicationContext.sendBroadcast(Intent(it))
            }

            // Stop service if exiting
            if (exiting) {
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopStream: ${e.message}", e)
            
            // Send broadcast to update UI even on error
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(Intent(AppIntentActions.BROADCAST_STREAM_STOPPED))
            
            // Show error notification
            val errorMsg = message?.let { "$it (${e.message})" } ?: e.message ?: "Unknown error"
            notificationManager.showErrorNotification(errorMsg)
            
            // Forward error to handler
            errorHandler.handleStreamError(e)
        }
    }

    private fun initializeStream() {
        streamManager.setStreamType(streamSettings.protocol)
        getCameraIds()
    }

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

            if (streamManager.isOnPreview()) {
                streamManager.stopPreview()
            }

            try {
                streamManager.releaseCamera()
                Log.d(TAG, "Ресурсы камеры успешно освобождены")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при освобождении камеры: ${e.message}", e)
            }

            openGlView = null
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при освобождении ресурсов камеры: ${e.message}", e)
        }
    }

    /** Запускает предпросмотр камеры с полной перезагрузкой ресурсов */
    fun restartPreview(view: OpenGlView) {
        try {
            Log.d(TAG, "Полный перезапуск предпросмотра с пересозданием камеры")

            if (streamManager.isOnPreview()) {
                streamManager.stopPreview()
            }

            Thread.sleep(50)

            openGlView = view

            // Важно: сначала подготовить видео с правильной ориентацией
            streamManager.prepareVideo()
            // Затем запустить предпросмотр с той же ориентацией
            streamManager.startPreview(view)

            Log.d(TAG, "Предпросмотр успешно перезапущен")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при перезапуске предпросмотра: ${e.message}", e)
            try {
                streamManager.stopPreview()
                streamManager.releaseCamera()
            } catch (e2: Exception) {
                Log.e(TAG, "Ошибка при очистке после сбоя: ${e2.message}", e2)
            }
        }
    }

    fun isPreviewRunning(): Boolean = isPreviewActive
}
