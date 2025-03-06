package net.emerlink.stream.ui.camera

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
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
import net.emerlink.stream.service.StreamService
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CameraScreen(onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var streamService by remember { mutableStateOf<StreamService?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }

    var previewStarted by remember { mutableStateOf(false) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as StreamService.LocalBinder
                streamService = binder.getService()
                Log.d("CameraScreen", "Service connected")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                streamService = null
                Log.d("CameraScreen", "Service disconnected")
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    val intent = Intent(context, StreamService::class.java)
                    context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                }

                Lifecycle.Event.ON_STOP -> {
                    streamService?.stopPreview()
                    previewStarted = false
                    context.unbindService(serviceConnection)
                }

                Lifecycle.Event.ON_RESUME -> {
                    previewStarted = false
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Add a broadcast receiver to update streaming status
    DisposableEffect(Unit) {
        val streamStartReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                isStreaming = true
            }
        }

        val streamStopReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                isStreaming = false
            }
        }

        context.registerReceiver(
            streamStartReceiver, IntentFilter(StreamService.ACTION_START_STREAM), Context.RECEIVER_NOT_EXPORTED
        )

        context.registerReceiver(
            streamStopReceiver, IntentFilter(StreamService.ACTION_STOP_STREAM), Context.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(streamStartReceiver)
            context.unregisterReceiver(streamStopReceiver)
        }
    }

    // Extract common control buttons
    @Composable
    fun RecordButton() {
        FloatingActionButton(
            onClick = {
                if (isStreaming) {
                    // Вместо отправки бродкаста, напрямую взаимодействуем с сервисом
                    streamService?.stopStream(null, null)
                    isStreaming = false
                } else {
                    // Вместо отправки бродкаста, напрямую взаимодействуем с сервисом
                    streamService?.startStream()
                    isStreaming = true
                }
                // isStreaming = !isStreaming // Это будет установлено через BroadcastReceiver
            },
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
    fun FlashButton() {
        SmallFloatingActionButton(
            onClick = {
                streamService?.let {
                    isFlashOn = it.toggleLantern()
                    Log.d("CameraScreen", "Переключение фонарика: $isFlashOn")
                }
            },
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
    fun PhotoButton() {
        SmallFloatingActionButton(
            onClick = {
                streamService?.let {
                    Log.d("CameraScreen", "Попытка сделать фото")
                    it.takePhoto()
                }
            },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ) {
            Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Take Photo")
        }
    }

    @Composable
    fun MuteButton() {
        SmallFloatingActionButton(
            onClick = {
                streamService?.let {
                    isMuted = !isMuted
                    it.toggleMute(isMuted)
                    Log.d("CameraScreen", "Toggle mute: $isMuted")
                }
            },
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
    fun SwitchCameraButton() {
        SmallFloatingActionButton(
            onClick = {
                streamService?.let {
                    Log.d("CameraScreen", "Попытка переключить камеру")
                    it.switchCamera()
                }
            },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera"
            )
        }
    }

    @Composable
    fun SettingsButton() {
        SmallFloatingActionButton(
            onClick = onSettingsClick,
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings"
            )
        }
    }

    // Перемещаем определение конфигурации на уровень Composable функции
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview - без safeDrawing, чтобы видео занимало весь экран
        AndroidView(
            factory = { ctx ->
                OpenGlView(ctx).apply { 
                    Log.d("CameraScreen", "Создание OpenGlView") 
                }
            }, modifier = Modifier
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
                }, update = { view ->
                if (streamService != null && !previewStarted) {
                    Log.d("CameraScreen", "Запуск preview")
                    
                    // Исправляем проблему с поворотом и сжатием видео в портретном режиме
                    if (!isLandscape) {
                        // Поскольку setKeepAspectRatio недоступен, используем имеющиеся методы OpenGlView
                        // Устанавливаем правильную ориентацию поверхности
                        // Вместо прямой установки аспект-соотношения, используем streamService
                        streamService?.setPortraitOrientation(view, true)
                    } else {
                        // В ландшафтном режиме используем обычную ориентацию
                        streamService?.setPortraitOrientation(view, false)
                    }
                    
                    streamService?.startPreview(view)
                    previewStarted = true
                }
            })

        // Stream Status Indicator
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isLandscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .padding(16.dp), 
            contentAlignment = Alignment.TopStart
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            ) {
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
                }
            }
        }

        // Camera Controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isLandscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (isLandscape) {
                // Right side controls column
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Column(
                        modifier = Modifier
                            .padding(end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Top controls
                        SettingsButton()
                        MuteButton()

                        // Record button in the middle
                        Spacer(modifier = Modifier.height(8.dp))
                        RecordButton()
                        Spacer(modifier = Modifier.height(8.dp))

                        // Bottom controls
                        PhotoButton()
                        FlashButton()
                    }
                }

                // Left side - camera switch button
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        SwitchCameraButton()
                    }
                }
            } else {
                // Portrait mode
                // Main Controls (Record button in center)
                RecordButton()

                // Left Controls
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Bottom
                ) {
                    FlashButton()
                    Spacer(modifier = Modifier.width(16.dp))
                    PhotoButton()
                }


                // Right Controls
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.Bottom
                ) {
                    MuteButton()
                    Spacer(modifier = Modifier.width(16.dp))
                    SettingsButton()
                }


                // Center Left - Camera Switch
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp, start = 0.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    SwitchCameraButton()
                }
            }
        }
    }
}
