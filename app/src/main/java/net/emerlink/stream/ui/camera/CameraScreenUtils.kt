package net.emerlink.stream.ui.camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.service.StreamService

/**
 * Создает приемник событий экрана
 */
fun createScreenStateReceiver(
    onScreenOff: () -> Unit,
    onUserPresent: () -> Unit,
): BroadcastReceiver =
    object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d("CameraScreen", "Экран выключен")
                    onScreenOff()
                }

                Intent.ACTION_SCREEN_ON -> {
                    Log.d("CameraScreen", "Экран включен")
                }

                Intent.ACTION_USER_PRESENT -> {
                    Log.d("CameraScreen", "Пользователь разблокировал экран")
                    onUserPresent()
                }
            }
        }
    }

/**
 * Создает наблюдатель жизненного цикла
 */
fun createLifecycleObserver(
    onPause: () -> Unit,
    onStop: () -> Unit,
    onResume: () -> Unit,
): LifecycleEventObserver =
    LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_PAUSE -> onPause()
            Lifecycle.Event.ON_STOP -> onStop()
            Lifecycle.Event.ON_RESUME -> onResume()

            else -> {}
        }
    }

/**
 * Перезапускает камеру после включения экрана
 */
fun restartCameraAfterScreenOn(
    streamService: StreamService,
    openGlView: OpenGlView?,
    onSuccess: () -> Unit,
) {
    // Перезапускаем камеру с задержкой, чтобы система успела подготовиться
    Handler(Looper.getMainLooper()).postDelayed({
        try {
            // Полный перезапуск камеры с предпросмотром
            openGlView?.let { view ->
                // Используем новый метод для полного перезапуска
                streamService.restartPreview(view)
            }
            onSuccess()
        } catch (e: Exception) {
            Log.e("CameraScreen", "Ошибка перезапуска предпросмотра: ${e.message}", e)

            // При ошибке пытаемся повторить еще раз с большей задержкой
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    openGlView?.let { view ->
                        streamService.restartPreview(view)
                    }
                    onSuccess()
                } catch (e: Exception) {
                    Log.e("CameraScreen", "Повторная ошибка перезапуска: ${e.message}", e)
                }
            }, 1000)
        }
    }, 500)
}
