package net.emerlink.stream.ui.camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
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
import net.emerlink.stream.util.AppIntentActions

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun CameraScreen(
    streamService: StreamService?,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isStreaming by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var showSettingsConfirmDialog by remember { mutableStateOf(false) }
    var showStreamInfo by remember { mutableStateOf(false) }
    var streamInfo by remember { mutableStateOf(StreamInfo()) }
    var openGlView by remember { mutableStateOf<OpenGlView?>(null) }
    var screenWasOff by remember { mutableStateOf(false) }
    var isPreviewActive by remember { mutableStateOf(streamService?.isPreviewRunning() == true) }

    LaunchedEffect(streamService) {
        streamService?.let { service ->
            isStreaming = service.streamManager.isStreaming()
        }
    }

    fun initializeCamera(view: OpenGlView) {
        if (streamService != null && !isPreviewActive && !streamService.isPreviewRunning()) {
            try {
                Log.d("CameraScreen", "Запуск камеры из единой точки инициализации")
                streamService.startPreview(view)
                isPreviewActive = true
            } catch (e: Exception) {
                Log.e("CameraScreen", "Ошибка при запуске предпросмотра: ${e.message}", e)
            }
        } else {
            Log.d("CameraScreen", "Предпросмотр уже активен или сервис не готов")
        }
    }

    // Регистрируем приемник событий экрана
    DisposableEffect(key1 = Unit) {
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
                                        streamService.restartPreview(view)
                                    }
                                    screenWasOff = false
                                } catch (e: Exception) {
                                    Log.e("CameraScreen", "Ошибка перезапуска предпросмотра: ${e.message}", e)

                                    // При ошибке пытаемся повторить еще раз с большей задержкой
                                    Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        try {
                                            openGlView?.let { view ->
                                                streamService.restartPreview(view)
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

    // Отслеживаем жизненный цикл для управления камерой
    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d("CameraScreen", "Lifecycle.Event.ON_PAUSE")
                    streamService?.stopPreview()
                    isPreviewActive = false
                }

                Lifecycle.Event.ON_STOP -> {
                    Log.d("CameraScreen", "Lifecycle.Event.ON_STOP")
                    streamService?.releaseCamera()
                    isPreviewActive = false
                }

                Lifecycle.Event.ON_RESUME -> {
                    Log.d("CameraScreen", "Lifecycle.Event.ON_RESUME")
                    // Запускаем камеру только если предпросмотр не активен и OpenGlView готов
                    if (!isPreviewActive && openGlView != null && streamService != null && !streamService.isPreviewRunning()) {
                        openGlView?.let { view ->
                            if (view.holder.surface?.isValid == true) {
                                Log.d("CameraScreen", "Запуск камеры из ON_RESUME")
                                Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    try {
                                        streamService.restartPreview(view)
                                        screenWasOff = false
                                        isPreviewActive = true
                                    } catch (e: Exception) {
                                        Log.e("CameraScreen", "Ошибка перезапуска предпросмотра: ${e.message}", e)
                                    }
                                }, 500)
                            }
                        }
                    }
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Наблюдаем за LiveData для скриншотов
    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.Observer<Boolean> { taken ->
            if (taken) {
                Log.d("CameraScreen", "Получено уведомление о скриншоте через LiveData")
                Handler(android.os.Looper.getMainLooper()).postDelayed({
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
            val settings = service.streamSettings
            streamInfo = StreamInfo(
                protocol = settings.protocol.toString(),
                resolution = settings.resolution.toString(),
                bitrate = "${settings.bitrate} kbps",
                fps = "${settings.fps} fps"
            )
        }
        onDispose { }
    }

    // Регистрируем приемник через LocalBroadcastManager
    DisposableEffect(key1 = isStreaming) {
        val streamStoppedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AppIntentActions.BROADCAST_STREAM_STOPPED) {
                    Log.d("CameraScreen", "Получено уведомление об остановке стрима")
                    isStreaming = false
                }
            }
        }

        val filter = IntentFilter(AppIntentActions.BROADCAST_STREAM_STOPPED)

        // Используем LocalBroadcastManager
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context)
            .registerReceiver(streamStoppedReceiver, filter)

        onDispose {
            try {
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context)
                    .unregisterReceiver(streamStoppedReceiver)
            } catch (e: Exception) {
                Log.e("CameraScreen", "Ошибка при отмене регистрации приемника: ${e.message}", e)
            }
        }
    }

    // Перемещаем определение конфигурации на уровень Composable функции
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        CameraPreview(
            streamService = streamService,
            onOpenGlViewCreated = { view ->
                openGlView = view
                initializeCamera(view)
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
                        streamService?.stopPreview()
                        openGlView = null
                        onSettingsClick()
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Ошибка при остановке предпросмотра: ${e.message}", e)
                    }
                }
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
            Log.d("CameraScreen", "Создание OpenGlView")
            OpenGlView(ctx).apply {
                // Создаем именованный объект для колбека SurfaceHolder
                val surfaceCallback = object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.d("CameraScreen", "Surface создан")
                        // Сохраняем ссылку на созданный OpenGlView только когда surface готов
                        onOpenGlViewCreated(this@apply)
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        Log.d("CameraScreen", "Surface изменен: $width x $height")
                        // Не запускаем камеру повторно при изменении размера
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Log.d("CameraScreen", "Surface уничтожен")
                    }
                }

                // Добавляем колбек к SurfaceHolder
                holder.addCallback(surfaceCallback)
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
                onSettingsClick = onSettingsClick
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
                onSettingsClick = onSettingsClick
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
            .padding(bottom = 6.dp),
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
            })
    }

    // Right Controls
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 6.dp),
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
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            modifier = Modifier
                .padding(top = if (isLandscape) 16.dp else 48.dp)
                .then(if (isLandscape) Modifier.width(300.dp) else Modifier)
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
