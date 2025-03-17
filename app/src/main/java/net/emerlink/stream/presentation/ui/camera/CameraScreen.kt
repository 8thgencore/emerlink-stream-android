@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.ui.camera

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.core.AppIntentActions
import net.emerlink.stream.data.model.StreamInfo
import net.emerlink.stream.presentation.ui.camera.components.*
import net.emerlink.stream.service.StreamService

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun CameraScreen(
    streamService: StreamService?,
    onSettingsClick: () -> Unit,
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
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionType by remember { mutableStateOf("") }

    // Используем lateinit для объявления лаунчеров заранее
    lateinit var requestCameraPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
    lateinit var requestMicrophonePermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>

    LaunchedEffect(streamService) {
        streamService?.let { service ->
            isStreaming = service.isStreaming()
        }
    }

    // Функция инициализации камеры
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

    // Функция проверки разрешения на микрофон
    fun checkMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                openGlView?.let { view ->
                    initializeCamera(view)
                }
            }

            else -> {
                requestMicrophonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Функция проверки разрешений на камеру
    fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                checkMicrophonePermission()
            }

            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // Инициализируем лаунчеры
    requestCameraPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Проверяем разрешение на микрофон после получения разрешения на камеру
                checkMicrophonePermission()
            } else {
                // Разрешение на камеру не получено, показываем диалог
                permissionType = "camera"
                showPermissionDialog = true
            }
        }

    requestMicrophonePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Оба разрешения получены, можно инициализировать камеру
                openGlView?.let { view ->
                    initializeCamera(view)
                }
            } else {
                // Разрешение на микрофон не получено, показываем диалог
                permissionType = "microphone"
                showPermissionDialog = true
            }
        }

    // Регистрируем приемник событий экрана
    DisposableEffect(key1 = Unit) {
        val screenStateReceiver =
            createScreenStateReceiver(onScreenOff = {
                Log.d("CameraScreen", "Экран выключен")
                screenWasOff = true
                streamService?.releaseCamera()
            }, onUserPresent = {
                Log.d("CameraScreen", "Пользователь разблокировал экран")
                if (screenWasOff && openGlView != null && streamService != null) {
                    restartCameraAfterScreenOn(streamService, openGlView) {
                        screenWasOff = false
                    }
                }
            })

        val filter =
            IntentFilter().apply {
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
        val observer =
            createLifecycleObserver(onPause = {
                Log.d("CameraScreen", "Lifecycle.Event.ON_PAUSE")
                streamService?.stopPreview()
                isPreviewActive = false
            }, onStop = {
                Log.d("CameraScreen", "Lifecycle.Event.ON_STOP")
                streamService?.releaseCamera()
                isPreviewActive = false
            }, onResume = {
                Log.d("CameraScreen", "Lifecycle.Event.ON_RESUME")
                // Запускаем камеру только если предпросмотр не активен и OpenGlView готов
                if (!isPreviewActive &&
                    openGlView != null &&
                    streamService != null &&
                    !streamService.isPreviewRunning()
                ) {
                    openGlView?.let { view ->
                        if (view.holder.surface?.isValid == true) {
                            Log.d("CameraScreen", "Запуск камеры из ON_RESUME")
                            Handler(Looper.getMainLooper()).postDelayed({
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
            })

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Обновляем информацию о стриме при подключении к сервису
    DisposableEffect(streamService) {
        streamService?.let { service ->
            streamInfo = service.getStreamInfo()
        }
        onDispose { }
    }

    // Регистрируем приемник через LocalBroadcastManager
    DisposableEffect(key1 = isStreaming) {
        val streamStoppedReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.action == AppIntentActions.BROADCAST_STREAM_STOPPED) {
                        Log.d("CameraScreen", "Получено уведомление об остановке стрима")
                        isStreaming = false
                    }
                }
            }

        val filter = IntentFilter(AppIntentActions.BROADCAST_STREAM_STOPPED)

        // Используем LocalBroadcastManager
        androidx.localbroadcastmanager.content.LocalBroadcastManager
            .getInstance(context)
            .registerReceiver(streamStoppedReceiver, filter)

        onDispose {
            try {
                androidx.localbroadcastmanager.content.LocalBroadcastManager
                    .getInstance(context)
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
        CameraPreview(
            streamService = streamService,
            onOpenGlViewCreated = { view ->
                openGlView = view
                checkCameraPermission()
            }
        )

        // Stream Status Indicator
        StreamStatusIndicator(
            isStreaming = isStreaming,
            isLandscape = isLandscape,
            onInfoClick = { showStreamInfo = !showStreamInfo }
        )

        // Stream Info Panel (отображается по нажатию на индикатор статуса)
        if (showStreamInfo) {
            StreamInfoPanel(
                streamInfo = streamInfo,
                isLandscape = isLandscape
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
            }
        )
    }

    // Settings confirmation dialog
    if (showSettingsConfirmDialog) {
        SettingsConfirmationDialog(onDismiss = { showSettingsConfirmDialog = false }, onConfirm = {
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
        })
    }

    // Обновляем диалог с объяснением необходимости разрешения
    if (showPermissionDialog) {
        PermissionDialog(
            permissionType = permissionType,
            onDismiss = { showPermissionDialog = false }
        )
    }
}
