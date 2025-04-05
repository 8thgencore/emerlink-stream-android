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
    companion object {
        private const val TAG = "CameraViewModel"
    }

    private var streamServiceRef: WeakReference<StreamService>? = null
    private var bound = false
    private var broadcastReceiver: BroadcastReceiver? = null

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
                        -> updateStreamingState(false)
                }
            }
        }

    init {
        StreamService.observer.observeForever { service ->
            streamServiceRef = service?.let { WeakReference(it) }

            if (service != null) {
                updateStreamingState(service.isStreaming() || service.isRecording())
                setPreviewActive(service.isPreviewRunning())
                updateStreamInfo(service.getStreamInfo())

                _openGlView.value?.let { view ->
                    if (!_isPreviewActive.value) {
                        startPreview(view)
                    }
                }
            } else {
                updateStreamingState(false)
                setPreviewActive(false)
                _audioLevel.value = 0.0f
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

                updateStreamingState(streamService.isStreaming() || streamService.isRecording())
                setPreviewActive(streamService.isPreviewRunning())
                updateStreamInfo(streamService.getStreamInfo())

                _openGlView.value?.let { view ->
                    if (!streamService.isPreviewRunning()) {
                        startPreview(view)
                    }
                }
            }

            override fun onServiceDisconnected(arg0: ComponentName) {
                Log.d(TAG, "Service disconnected")
                streamServiceRef = null
                bound = false
                updateStreamingState(false)
                setPreviewActive(false)
                _audioLevel.value = 0.0f
            }
        }

    fun bindService(context: Context) {
        val filter =
            IntentFilter().apply {
                addAction(AppIntentActions.START_STREAM)
                addAction(AppIntentActions.STOP_STREAM)
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
            ContextCompat.startForegroundService(context, intent)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        registerBroadcastReceivers(context)
    }

    private fun registerBroadcastReceivers(context: Context) {
        if (broadcastReceiver != null) return

        broadcastReceiver =
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

                        AppIntentActions.BROADCAST_STREAM_STOPPED -> {
                            updateStreamingState(false)
                        }

                        AppIntentActions.BROADCAST_STREAM_STARTED -> {
                            updateStreamingState(true)
                        }
                    }
                }
            }

        val filter =
            IntentFilter().apply {
                addAction(AppIntentActions.BROADCAST_AUDIO_LEVEL)
                addAction(AppIntentActions.BROADCAST_PREVIEW_STATUS)
                addAction(AppIntentActions.BROADCAST_STREAM_STOPPED)
                addAction(AppIntentActions.BROADCAST_STREAM_STARTED)
            }

        LocalBroadcastManager
            .getInstance(context)
            .registerReceiver(broadcastReceiver!!, filter)
    }

    fun unbindService(context: Context) {
        if (bound) {
            try {
                val shouldUnbind = streamServiceRef?.get()?.let { !it.isStreaming() && !it.isRecording() } != false
                if (shouldUnbind) {
                    context.unregisterReceiver(streamStatusReceiver)
                    context.unbindService(connection)
                    bound = false
                    streamServiceRef = null
                } else {
                    Log.d(TAG, "Skipping unbind because stream/recording is active")
                }
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Service not registered? ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding service", e)
            }
        }

        broadcastReceiver?.let {
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(it)
                broadcastReceiver = null
                Log.d(TAG, "Unregistered broadcast receiver")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering broadcast receiver", e)
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
                Log.e(TAG, "Error starting preview", e)
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
                Log.e(TAG, "Error stopping preview", e)
                _isPreviewActive.value = false
            }
        }
    }

    fun tapToFocus(event: MotionEvent) {
        viewModelScope.launch {
            try {
                streamServiceRef?.get()?.tapToFocus(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error focusing", e)
            }
        }
    }

    fun setZoom(event: MotionEvent) {
        viewModelScope.launch {
            try {
                streamServiceRef?.get()?.setZoom(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting zoom", e)
            }
        }
    }

    fun takePhoto() {
        viewModelScope.launch {
            try {
                _flashOverlayVisible.value = true
                streamServiceRef?.get()?.takePhoto()
            } catch (e: Exception) {
                Log.e(TAG, "Error taking photo", e)
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
                Log.e(TAG, "Error switching camera", e)
            }
        }
    }

    // State update methods
    fun updateStreamingState(isStreaming: Boolean) {
        Log.d(TAG, "Updating streaming state to: $isStreaming")
        _isStreaming.value = isStreaming
        if (isStreaming) {
            streamServiceRef?.get()?.getStreamInfo()?.let { updateStreamInfo(it) }
        }
    }

    fun toggleFlash() {
        viewModelScope.launch {
            try {
                val result = streamServiceRef?.get()?.toggleLantern() == true
                _isFlashOn.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling flash", e)
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
                Log.e(TAG, "Error toggling mute", e)
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
                Log.d(TAG, "Attempting to start stream via service")
                streamServiceRef?.get()?.startStream()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting stream", e)
                updateStreamingState(false)
            }
        }
    }

    fun stopStreaming() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Attempting to stop stream via service")
                streamServiceRef?.get()?.stopStream()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping stream", e)
            }
        }
    }

    fun stopStreamingWithConfirmation() {
        if (isStreaming.value) {
            setShowSettingsConfirmDialog(true)
        } else {
            stopStreaming()
        }
    }

    /**
     * Hide flash overlay after a short delay
     */
    fun hideFlashOverlayAfterDelay() {
        viewModelScope.launch {
            delay(150)
            _flashOverlayVisible.value = false
        }
    }
}
