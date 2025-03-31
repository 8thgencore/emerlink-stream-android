@file:Suppress("ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.camera.viewmodel

import android.content.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.emerlink.stream.core.AppIntentActions
import net.emerlink.stream.data.model.StreamInfo
import net.emerlink.stream.service.StreamService
import java.lang.ref.WeakReference

/**
 * ViewModel for managing CameraScreen state
 */
class CameraViewModel : ViewModel() {
    private var streamServiceRef: WeakReference<StreamService>? = null
    private var bound = false
    private var audioLevelReceiver: BroadcastReceiver? = null

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _showSettingsConfirmDialog = MutableStateFlow(false)
    val showSettingsConfirmDialog: StateFlow<Boolean> = _showSettingsConfirmDialog.asStateFlow()

    private val _showStreamInfo = MutableStateFlow(false)
    val showStreamInfo: StateFlow<Boolean> = _showStreamInfo.asStateFlow()

    private val _streamInfo = MutableStateFlow(StreamInfo())
    val streamInfo: StateFlow<StreamInfo> = _streamInfo.asStateFlow()

    private val _isPreviewActive = MutableStateFlow(false)
    val isPreviewActive: StateFlow<Boolean> = _isPreviewActive.asStateFlow()

    private val _openGlView = MutableStateFlow<OpenGlView?>(null)
    val openGlView: StateFlow<OpenGlView?> = _openGlView.asStateFlow()

    // Audio level state (0.0f - 1.0f)
    private val _audioLevel = MutableStateFlow(0.0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // Flash overlay state
    private val _flashOverlayVisible = MutableStateFlow(false)
    val flashOverlayVisible: StateFlow<Boolean> = _flashOverlayVisible.asStateFlow()

    private val streamStatusReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    AppIntentActions.START_STREAM -> updateStreamingState(true)
                    AppIntentActions.STOP_STREAM,
                    AppIntentActions.AUTH_ERROR,
                    AppIntentActions.CONNECTION_FAILED,
                        -> updateStreamingState(false)
                }
            }
        }

    init {
        // Observe service instance
        StreamService.observer.observeForever { service ->
            streamServiceRef = service?.let { WeakReference(it) }

            if (service != null) {
                // Initialize states
                updateStreamingState(service.isStreaming())
                setPreviewActive(service.isPreviewRunning())
                updateStreamInfo(service.getStreamInfo())

                // Initialize camera if we have OpenGlView
                _openGlView.value?.let { view ->
                    if (!_isPreviewActive.value) {
                        startPreview(view)
                    }
                }
            }
        }
    }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                className: ComponentName,
                service: IBinder,
            ) {
                val binder = service as StreamService.LocalBinder
                val streamService = binder.getService()
                streamServiceRef = WeakReference(streamService)
                bound = true

                // Initialize states
                updateStreamingState(streamService.isStreaming())
                setPreviewActive(streamService.isPreviewRunning())
                updateStreamInfo(streamService.getStreamInfo())

                // Initialize camera if we already have OpenGlView and no preview is active
                _openGlView.value?.let { view ->
                    if (!_isPreviewActive.value) {
                        startPreview(view)
                    }
                }
            }

            override fun onServiceDisconnected(arg0: ComponentName) {
                streamServiceRef = null
                bound = false
            }
        }

    fun bindService(context: Context) {
        val filter =
            IntentFilter().apply {
                addAction(AppIntentActions.START_STREAM)
                addAction(AppIntentActions.STOP_STREAM)
                addAction(AppIntentActions.AUTH_ERROR)
                addAction(AppIntentActions.CONNECTION_FAILED)
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                streamStatusReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            ContextCompat.registerReceiver(context, streamStatusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        // Bind to service
        Intent(context, StreamService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        registerAudioLevelReceiver(context)
    }

    private fun registerAudioLevelReceiver(context: Context) {
        audioLevelReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    when (intent.action) {
                        AppIntentActions.BROADCAST_AUDIO_LEVEL -> {
                            val level = intent.getFloatExtra(AppIntentActions.EXTRA_AUDIO_LEVEL, 0.0f)
                            _audioLevel.value = level
                        }

                        AppIntentActions.BROADCAST_PREVIEW_STATUS -> {
                            val isActive = intent.getBooleanExtra(AppIntentActions.EXTRA_PREVIEW_ACTIVE, false)
                            setPreviewActive(isActive)
                        }
                    }
                }
            }

        val filter =
            IntentFilter().apply {
                addAction(AppIntentActions.BROADCAST_AUDIO_LEVEL)
                addAction(AppIntentActions.BROADCAST_PREVIEW_STATUS)
            }

        // LocalBroadcastManager doesn't need the exported flag since it's local to the app
        LocalBroadcastManager
            .getInstance(context)
            .registerReceiver(audioLevelReceiver!!, filter)
    }

    fun unbindService(context: Context) {
        if (bound) {
            try {
                // Отвязываемся от сервиса только если нет активного стрима
                if (!_isStreaming.value) {
                    context.unbindService(connection)
                    context.unregisterReceiver(streamStatusReceiver)
                    bound = false
                    streamServiceRef = null
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error unbinding service", e)
            }
        }

        // Отписываемся от broadcast receiver в любом случае
        audioLevelReceiver?.let {
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(it)
                audioLevelReceiver = null
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error unregistering audio level receiver", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamServiceRef = null
    }

    fun startPreview(view: OpenGlView) {
        viewModelScope.launch {
            try {
                streamServiceRef?.get()?.startPreview(view)
                _isPreviewActive.value = true
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error starting preview", e)
                _isPreviewActive.value = false
            }
        }
    }

    fun stopPreview() {
        viewModelScope.launch {
            try {
                streamServiceRef?.get()?.stopPreview()
                _isPreviewActive.value = false
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error stopping preview", e)
                // Still mark preview as inactive
                _isPreviewActive.value = false
            }
        }
    }

    fun tapToFocus(event: MotionEvent) {
        viewModelScope.launch {
            try {
                streamServiceRef?.get()?.tapToFocus(event)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error focusing", e)
            }
        }
    }

    fun setZoom(event: MotionEvent) {
        viewModelScope.launch {
            try {
                streamServiceRef?.get()?.setZoom(event)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error setting zoom", e)
            }
        }
    }

    fun takePhoto() {
        viewModelScope.launch {
            try {
                _flashOverlayVisible.value = true
                streamServiceRef?.get()?.takePhoto()
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error taking photo", e)
                // Hide flash overlay in case of error
                _flashOverlayVisible.value = false
            }
        }
    }

    fun switchCamera() {
        viewModelScope.launch {
            try {
                streamServiceRef?.get()?.switchCamera()
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error switching camera", e)
            }
        }
    }

    // State update methods
    fun updateStreamingState(isStreaming: Boolean) {
        _isStreaming.value = isStreaming
    }

    fun toggleFlash() {
        viewModelScope.launch {
            try {
                val result = streamServiceRef?.get()?.toggleLantern() == true
                _isFlashOn.value = result
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error toggling flash", e)
            }
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            try {
                val newMuteState = !_isMuted.value
                streamServiceRef?.get()?.toggleMute(newMuteState)
                _isMuted.value = newMuteState
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error toggling mute", e)
            }
        }
    }

    fun setShowSettingsConfirmDialog(show: Boolean) {
        _showSettingsConfirmDialog.value = show
    }

    fun toggleStreamInfo() {
        _showStreamInfo.value = !_showStreamInfo.value
    }

    fun updateStreamInfo(info: StreamInfo) {
        _streamInfo.value = info
    }

    fun setPreviewActive(isActive: Boolean) {
        _isPreviewActive.value = isActive
    }

    fun setOpenGlView(view: OpenGlView?) {
        _openGlView.value = view
    }

    fun startStreaming() {
        viewModelScope.launch {
            try {
                streamServiceRef?.get()?.startStream()
                updateStreamingState(true)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error starting stream", e)
            }
        }
    }

    fun stopStreaming() {
        viewModelScope.launch {
            try {
                streamServiceRef?.get()?.stopStream()
                updateStreamingState(false)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error stopping stream", e)
            }
        }
    }

    /**
     * Hide flash overlay after a short delay
     */
    fun hideFlashOverlayAfterDelay() {
        viewModelScope.launch {
            delay(150) // Show flash for 150ms
            _flashOverlayVisible.value = false
        }
    }
}
