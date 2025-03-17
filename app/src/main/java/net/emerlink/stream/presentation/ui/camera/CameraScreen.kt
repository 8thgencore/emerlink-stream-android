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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.core.AppIntentActions
import net.emerlink.stream.presentation.ui.camera.components.*
import net.emerlink.stream.presentation.ui.camera.viewmodel.CameraViewModel

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun CameraScreen(
    onSettingsClick: () -> Unit,
    viewModel: CameraViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Bind service
    DisposableEffect(Unit) {
        viewModel.bindService(context)
        onDispose {
            viewModel.unbindService(context)
        }
    }

    // Collect states
    val isServiceConnected by viewModel.isServiceConnected.collectAsStateWithLifecycle()
    val openGlView by viewModel.openGlView.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val isFlashOn by viewModel.isFlashOn.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val showStreamInfo by viewModel.showStreamInfo.collectAsStateWithLifecycle()
    val streamInfo by viewModel.streamInfo.collectAsStateWithLifecycle()
    val screenWasOff by viewModel.screenWasOff.collectAsStateWithLifecycle()
    val isPreviewActive by viewModel.isPreviewActive.collectAsStateWithLifecycle()
    val showPermissionDialog by viewModel.showPermissionDialog.collectAsStateWithLifecycle()
    val permissionType by viewModel.permissionType.collectAsStateWithLifecycle()

    // Используем lateinit для объявления лаунчеров заранее
    lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    lateinit var requestMicrophonePermissionLauncher: ActivityResultLauncher<String>

    // Функция инициализации камеры
    fun initializeCamera(view: OpenGlView) {
        if (!isPreviewActive && isServiceConnected) {
            try {
                Log.d("CameraScreen", "Запуск камеры из единой точки инициализации")
                viewModel.startPreview(view)
            } catch (e: Exception) {
                Log.e("CameraScreen", "Ошибка при запуске предпросмотра: ${e.message}", e)
            }
        } else {
            Log.d("CameraScreen", "Предпросмотр уже активен или сервис не подключен")
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
                checkMicrophonePermission()
            } else {
                viewModel.showPermissionDialog("camera")
            }
        }

    requestMicrophonePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                openGlView?.let { view ->
                    initializeCamera(view)
                }
            } else {
                viewModel.showPermissionDialog("microphone")
            }
        }

    // Регистрируем приемник событий экрана
    DisposableEffect(key1 = Unit) {
        val screenStateReceiver =
            createScreenStateReceiver(onScreenOff = {
                Log.d("CameraScreen", "Экран выключен")
                viewModel.setScreenWasOff(true)
                viewModel.releaseCamera()
            }, onUserPresent = {
                Log.d("CameraScreen", "Пользователь разблокировал экран")
                if (screenWasOff && openGlView != null) {
                    openGlView?.let { view ->
                        viewModel.restartPreview(view)
                        viewModel.setScreenWasOff(false)
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
                viewModel.stopPreview()
            }, onStop = {
                Log.d("CameraScreen", "Lifecycle.Event.ON_STOP")
                viewModel.releaseCamera()
            }, onResume = {
                Log.d("CameraScreen", "Lifecycle.Event.ON_RESUME")
                if (!isPreviewActive && openGlView != null) {
                    openGlView?.let { view ->
                        if (view.holder.surface?.isValid == true) {
                            Log.d("CameraScreen", "Запуск камеры из ON_RESUME")
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    viewModel.restartPreview(view)
                                    viewModel.setScreenWasOff(false)
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
                        viewModel.updateStreamingState(false)
                    }
                }
            }

        val filter = IntentFilter(AppIntentActions.BROADCAST_STREAM_STOPPED)

        LocalBroadcastManager
            .getInstance(context)
            .registerReceiver(streamStoppedReceiver, filter)

        onDispose {
            try {
                LocalBroadcastManager
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
            viewModel = viewModel,
            onOpenGlViewCreated = { view ->
                viewModel.setOpenGlView(view)
                checkCameraPermission()
            }
        )

        // Stream Status Indicator
        StreamStatusIndicator(
            isStreaming = isStreaming,
            isLandscape = isLandscape,
            onInfoClick = { viewModel.toggleStreamInfo() }
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
            viewModel = viewModel,
            onSettingsClick = {
                if (isStreaming) {
                    viewModel.setShowSettingsConfirmDialog(true)
                } else {
                    try {
                        viewModel.stopPreview()
                        viewModel.setOpenGlView(null)
                        onSettingsClick()
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Ошибка при остановке предпросмотра: ${e.message}", e)
                    }
                }
            }
        )
    }

    // Settings confirmation dialog
    if (viewModel.showSettingsConfirmDialog.value) {
        SettingsConfirmationDialog(
            onDismiss = { viewModel.setShowSettingsConfirmDialog(false) },
            onConfirm = {
                viewModel.setShowSettingsConfirmDialog(false)
                try {
                    viewModel.stopStreaming()
                    viewModel.stopPreview()
                    viewModel.setOpenGlView(null)
                    onSettingsClick()
                } catch (e: Exception) {
                    Log.e("CameraScreen", "Ошибка при переходе в настройки: ${e.message}", e)
                }
            }
        )
    }

    // Обновляем диалог с объяснением необходимости разрешения
    if (showPermissionDialog) {
        PermissionDialog(
            permissionType = permissionType,
            onDismiss = { viewModel.dismissPermissionDialog() }
        )
    }
}
