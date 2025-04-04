@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.camera.components

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
import net.emerlink.stream.presentation.camera.viewmodel.CameraViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CameraPreview(
    viewModel: CameraViewModel,
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
                            viewModel.tapToFocus(event)
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            viewModel.setZoom(event)
                            true
                        }

                        else -> false
                    }
                }
    )
}
