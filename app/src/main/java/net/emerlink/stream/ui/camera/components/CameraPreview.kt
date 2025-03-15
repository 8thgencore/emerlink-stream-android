@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.ui.camera.components

import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.viewinterop.AndroidView
import com.pedro.library.view.OpenGlView
import net.emerlink.stream.service.StreamService

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CameraPreview(
    streamService: StreamService?,
    onOpenGlViewCreated: (OpenGlView) -> Unit,
) {
    AndroidView(
        factory = { ctx ->
            Log.d("CameraScreen", "Создание OpenGlView")
            OpenGlView(ctx).apply {
                // Создаем именованный объект для колбека SurfaceHolder
                val surfaceCallback =
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            Log.d("CameraScreen", "Surface создан")
                            // Сохраняем ссылку на созданный OpenGlView только когда surface готов
                            onOpenGlViewCreated(this@apply)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) {
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
        modifier =
            Modifier
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
