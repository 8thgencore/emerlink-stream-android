package net.emerlink.stream.ui.camera

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.R
import net.emerlink.stream.model.StreamInfo
import net.emerlink.stream.service.StreamService

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun CameraScreen(onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var streamService by remember { mutableStateOf<StreamService?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }

    // New state variables for dialogs and notifications
    var showSettingsConfirmDialog by remember { mutableStateOf(false) }
    var showScreenshotNotification by remember { mutableStateOf(false) }

    // Добавляем состояние для отображения информационной панели
    var showStreamInfo by remember { mutableStateOf(false) }

    // Добавляем состояние для хранения информации о стриме
    var streamInfo by remember { mutableStateOf(StreamInfo()) }

    // Состояние для контроля предпросмотра - важно!
    var openGlView by remember { mutableStateOf<OpenGlView?>(null) }
    
    // Добавляем состояние для отслеживания экрана
    var screenWasOff by remember { mutableStateOf(false) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as StreamService.LocalBinder
                streamService = binder.getService()
                Log.d("CameraScreen", "Service connected")
                
                // Инициализируем предпросмотр если OpenGlView уже создан
                openGlView?.let { view ->
                    try {
                        // Если экран был заблокирован, нужно пересоздать поверхность
                        if (screenWasOff) {
                            Log.d("CameraScreen", "Перезапуск после блокировки экрана")
                            // Небольшая задержка для корректной инициализации поверхности
                            Handler(android.os.Looper.getMainLooper()).postDelayed({
                                try {
                                    streamService?.stopPreview()
                                    streamService?.startPreview(view)
                                    screenWasOff = false
                                } catch (e: Exception) {
                                    Log.e("CameraScreen", "Ошибка перезапуска предпросмотра: ${e.message}", e)
                                }
                            }, 500)
                        } else {
                            streamService?.startPreview(view)
                        }
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Ошибка при запуске предпросмотра: ${e.message}", e)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d("CameraScreen", "Service disconnected")
                streamService = null
            }
        }
    }

    // Подключаемся к сервису при создании экрана
    LaunchedEffect(Unit) {
        val intent = Intent(context, StreamService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // Регистрируем приемник событий экрана
    DisposableEffect(Unit) {
        val screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d("CameraScreen", "Экран выключен")
                        screenWasOff = true
                        
                        // При выключении экрана полностью освобождаем ресурсы камеры
                        streamService?.releaseCamera()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d("CameraScreen", "Экран включен")
                        // Перезапуск камеры будет происходить в ACTION_USER_PRESENT
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        Log.d("CameraScreen", "Пользователь разблокировал экран")
                        if (screenWasOff && openGlView != null && streamService != null) {
                            // Перезапускаем камеру с задержкой, чтобы система успела подготовиться
                            Handler(android.os.Looper.getMainLooper()).postDelayed({
                                try {
                                    // Полный перезапуск камеры с предпросмотром
                                    openGlView?.let { view ->
                                        // Используем новый метод для полного перезапуска
                                        streamService?.restartPreview(view)
                                    }
                                    screenWasOff = false
                                } catch (e: Exception) {
                                    Log.e("CameraScreen", "Ошибка перезапуска предпросмотра: ${e.message}", e)
                                    
                                    // При ошибке пытаемся повторить еще раз с большей задержкой
                                    Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        try {
                                            openGlView?.let { view ->
                                                streamService?.restartPreview(view)
                                            }
                                            screenWasOff = false
                                        } catch (e: Exception) {
                                            Log.e("CameraScreen", "Повторная ошибка перезапуска: ${e.message}", e)
                                        }
                                    }, 1000)
                                }
                            }, 500)
                        }
                    }
                }
            }
        }
        
        // Регистрируем приемник для отслеживания состояния экрана
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(screenStateReceiver, filter)
        
        onDispose {
            try {
                context.unregisterReceiver(screenStateReceiver)
            } catch (e: Exception) {
                Log.e("CameraScreen", "Ошибка при отмене регистрации приемника: ${e.message}", e)
            }
        }
    }

    // Отслеживаем жизненный цикл
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // При уходе с экрана полностью освобождаем ресурсы камеры
                    streamService?.releaseCamera()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Проверяем, нужно ли перезапустить предпросмотр
                    if (screenWasOff && openGlView != null && streamService != null) {
                        Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                openGlView?.let { view ->
                                    streamService?.restartPreview(view)
                                }
                                screenWasOff = false
                            } catch (e: Exception) {
                                Log.e("CameraScreen", "Ошибка перезапуска предпросмотра: ${e.message}", e)
                            }
                        }, 500)
                    }
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { 
            try {
                // Освобождаем все ресурсы при удалении компонента
                streamService?.releaseCamera()
                // Отключаемся от сервиса
                context.unbindService(serviceConnection)
                lifecycleOwner.lifecycle.removeObserver(observer)
            } catch (e: Exception) {
                Log.e("CameraScreen", "Ошибка при очистке ресурсов: ${e.message}", e)
            }
        }
    }

    // Наблюдаем за LiveData для скриншотов
    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.Observer<Boolean> { taken ->
            if (taken) {
                Log.d("CameraScreen", "Получено уведомление о скриншоте через LiveData")
                showScreenshotNotification = true
                // Auto-hide notification after 3 seconds
                Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showScreenshotNotification = false
                    // Сбрасываем значение
                    StreamService.screenshotTaken.value = false
                }, 3000)
            }
        }

        StreamService.screenshotTaken.observeForever(observer)

        onDispose {
            StreamService.screenshotTaken.removeObserver(observer)
        }
    }

    // Обновляем информацию о стриме при подключении к сервису
    DisposableEffect(streamService) {
        streamService?.let { service ->
            // Получаем информацию из сервиса
            val settings = service.streamSettings
            streamInfo = StreamInfo(
                protocol = settings.protocol,
                resolution = settings.resolution.toString(),
                bitrate = "${settings.bitrate} kbps",
                fps = "${settings.fps} fps"
            )
        }
        onDispose { }
    }

    // Перемещаем определение конфигурации на уровень Composable функции
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        CameraPreview(
            streamService = streamService,
            onOpenGlViewCreated = { view -> 
                // Сохраняем ссылку на OpenGlView
                openGlView = view 
                
                // Запускаем предпросмотр если сервис уже подключен
                if (streamService != null) {
                    try {
                        streamService?.startPreview(view)
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Ошибка при запуске предпросмотра: ${e.message}", e)
                    }
                }
            }
        )

        // Stream Status Indicator
        StreamStatusIndicator(
            isStreaming = isStreaming, isLandscape = isLandscape, onInfoClick = { showStreamInfo = !showStreamInfo })

        // Stream Info Panel (отображается по нажатию на индикатор статуса)
        if (showStreamInfo) {
            StreamInfoPanel(
                streamInfo = streamInfo, isLandscape = isLandscape
            )
        }

        // Screenshot notification
        if (showScreenshotNotification) {
            ScreenshotNotification(isLandscape = isLandscape)
        }

        // Camera Controls
        CameraControls(
            isLandscape = isLandscape,
            isStreaming = isStreaming,
            isFlashOn = isFlashOn,
            isMuted = isMuted,
            streamService = streamService,
            onStreamingToggle = { isStreaming = it },
            onFlashToggle = { isFlashOn = it },
            onMuteToggle = { isMuted = it },
            onSettingsClick = {
                if (isStreaming) {
                    showSettingsConfirmDialog = true
                } else {
                    try {
                        // Обязательно останавливаем предпросмотр перед переходом на экран настроек
                        streamService?.stopPreview()
                        openGlView = null
                        onSettingsClick()
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Ошибка при остановке предпросмотра: ${e.message}", e)
                    }
                }
            },
            onShowScreenshotNotification = {
                showScreenshotNotification = true
                Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showScreenshotNotification = false
                }, 3000)
            })
    }

    // Settings confirmation dialog
    if (showSettingsConfirmDialog) {
        SettingsConfirmationDialog(
            onDismiss = { showSettingsConfirmDialog = false }, 
            onConfirm = {
                showSettingsConfirmDialog = false
                try {
                    streamService?.stopStream(null, null)
                    // Обязательно останавливаем предпросмотр перед переходом на экран настроек
                    streamService?.stopPreview()
                    openGlView = null
                    onSettingsClick()
                } catch (e: Exception) {
                    Log.e("CameraScreen", "Ошибка при переходе в настройки: ${e.message}", e)
                }
            }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CameraPreview(
    streamService: StreamService?,
    onOpenGlViewCreated: (OpenGlView) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            OpenGlView(ctx).apply { 
                Log.d("CameraScreen", "Создание OpenGlView")
                // Сохраняем ссылку на созданный OpenGlView
                onOpenGlViewCreated(this)
            }
        }, 
        modifier = Modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        streamService?.tapToFocus(event)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        streamService?.setZoom(event)
                        true
                    }
                    else -> false
                }
            }
    )
}

@Composable
private fun StreamStatusIndicator(
    isStreaming: Boolean, isLandscape: Boolean, onInfoClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!isLandscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                else Modifier
            )
            .padding(16.dp), contentAlignment = Alignment.TopStart
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable { onInfoClick() }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (isStreaming) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isStreaming) "LIVE" else "OFF",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Добавляем иконку для подсказки, что можно нажать
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Stream Info",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ScreenshotNotification(isLandscape: Boolean) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!isLandscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                else Modifier
            )
            .padding(16.dp), contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = if (isLandscape) 8.dp else 48.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = context.getString(R.string.screenshot_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CameraControls(
    isLandscape: Boolean,
    isStreaming: Boolean,
    isFlashOn: Boolean,
    isMuted: Boolean,
    streamService: StreamService?,
    onStreamingToggle: (Boolean) -> Unit,
    onFlashToggle: (Boolean) -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onShowScreenshotNotification: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!isLandscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                else Modifier
            )
            .padding(16.dp), contentAlignment = Alignment.BottomCenter
    ) {
        if (isLandscape) {
            LandscapeCameraControls(
                isStreaming = isStreaming,
                isFlashOn = isFlashOn,
                isMuted = isMuted,
                streamService = streamService,
                onStreamingToggle = onStreamingToggle,
                onFlashToggle = onFlashToggle,
                onMuteToggle = onMuteToggle,
                onSettingsClick = onSettingsClick,
                onShowScreenshotNotification = onShowScreenshotNotification
            )
        } else {
            PortraitCameraControls(
                isStreaming = isStreaming,
                isFlashOn = isFlashOn,
                isMuted = isMuted,
                streamService = streamService,
                onStreamingToggle = onStreamingToggle,
                onFlashToggle = onFlashToggle,
                onMuteToggle = onMuteToggle,
                onSettingsClick = onSettingsClick,
                onShowScreenshotNotification = onShowScreenshotNotification
            )
        }
    }
}

@Composable
private fun LandscapeCameraControls(
    isStreaming: Boolean,
    isFlashOn: Boolean,
    isMuted: Boolean,
    streamService: StreamService?,
    onStreamingToggle: (Boolean) -> Unit,
    onFlashToggle: (Boolean) -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onShowScreenshotNotification: () -> Unit
) {
    // Right side controls column
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            modifier = Modifier.padding(end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top controls
            SettingsButton(onClick = onSettingsClick)
            MuteButton(
                isMuted = isMuted, onClick = {
                    streamService?.toggleMute(!isMuted)
                    onMuteToggle(!isMuted)
                })

            // Record button in the middle
            Spacer(modifier = Modifier.height(8.dp))
            RecordButton(
                isStreaming = isStreaming, onClick = {
                    if (isStreaming) {
                        streamService?.stopStream(null, null)
                        onStreamingToggle(false)
                    } else {
                        streamService?.startStream()
                        onStreamingToggle(true)
                    }
                })
            Spacer(modifier = Modifier.height(8.dp))

            // Bottom controls
            PhotoButton(
                onClick = {
                    streamService?.takePhoto()
                    onShowScreenshotNotification()
                })
            FlashButton(
                isFlashOn = isFlashOn, onClick = {
                    val newState = streamService?.toggleLantern() == true
                    onFlashToggle(newState)
                })
        }
    }

    // Left side - camera switch button
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Box(modifier = Modifier.padding(start = 16.dp)) {
            SwitchCameraButton(onClick = { streamService?.switchCamera() })
        }
    }
}

@Composable
private fun PortraitCameraControls(
    isStreaming: Boolean,
    isFlashOn: Boolean,
    isMuted: Boolean,
    streamService: StreamService?,
    onStreamingToggle: (Boolean) -> Unit,
    onFlashToggle: (Boolean) -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onShowScreenshotNotification: () -> Unit
) {
    // Main Controls (Record button in center)
    RecordButton(
        isStreaming = isStreaming, onClick = {
            if (isStreaming) {
                streamService?.stopStream(null, null)
                onStreamingToggle(false)
            } else {
                streamService?.startStream()
                onStreamingToggle(true)
            }
        })

    // Left Controls
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        FlashButton(
            isFlashOn = isFlashOn, onClick = {
                val newState = streamService?.toggleLantern() == true
                onFlashToggle(newState)
            })
        Spacer(modifier = Modifier.width(16.dp))
        PhotoButton(
            onClick = {
                streamService?.takePhoto()
                onShowScreenshotNotification()
            })
    }

    // Right Controls
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        MuteButton(
            isMuted = isMuted, onClick = {
                streamService?.toggleMute(!isMuted)
                onMuteToggle(!isMuted)
            })
        Spacer(modifier = Modifier.width(16.dp))
        SettingsButton(onClick = onSettingsClick)
    }

    // Center Left - Camera Switch
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp, start = 0.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        SwitchCameraButton(onClick = { streamService?.switchCamera() })
    }
}

@Composable
private fun RecordButton(isStreaming: Boolean, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Icon(
            imageVector = if (isStreaming) Icons.Default.VideocamOff else Icons.Default.Videocam,
            contentDescription = if (isStreaming) "Stop Streaming" else "Start Streaming"
        )
    }
}

@Composable
private fun FlashButton(isFlashOn: Boolean, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary
    ) {
        Icon(
            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
            contentDescription = if (isFlashOn) "Turn Flash Off" else "Turn Flash On"
        )
    }
}

@Composable
private fun PhotoButton(onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = {
            Log.d("CameraScreen", "Нажата кнопка фото")
            onClick()
        }, containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary
    ) {
        Icon(
            imageVector = Icons.Default.PhotoCamera, contentDescription = "Take Photo"
        )
    }
}

@Composable
private fun MuteButton(isMuted: Boolean, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary
    ) {
        Icon(
            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (isMuted) "Unmute" else "Mute"
        )
    }
}

@Composable
private fun SwitchCameraButton(onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary
    ) {
        Icon(
            imageVector = Icons.Default.Cameraswitch, contentDescription = "Switch Camera"
        )
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary
    ) {
        Icon(
            imageVector = Icons.Default.Settings, contentDescription = "Settings"
        )
    }
}

@Composable
private fun SettingsConfirmationDialog(
    onDismiss: () -> Unit, onConfirm: () -> Unit
) {
    val context = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.stop_streaming)) },
        text = { Text(context.getString(R.string.stop_streaming_confirmation)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(context.getString(R.string.yes))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel))
            }
        })
}

@Composable
private fun StreamInfoPanel(
    streamInfo: StreamInfo, isLandscape: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!isLandscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                else Modifier
            )
            .padding(16.dp), contentAlignment = Alignment.TopStart
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 48.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow(label = "Protocol", value = streamInfo.protocol)
                InfoRow(label = "Resolution", value = streamInfo.resolution)
                InfoRow(label = "Bitrate", value = streamInfo.bitrate)
                InfoRow(label = "FPS", value = streamInfo.fps)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface
        )
    }
}
