@file:Suppress("ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.presentation.camera.viewmodel

import android.content.*
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
    private var broadcastReceiver: BroadcastReceiver? = null

    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

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

    private val _openGlView = MutableStateFlow<OpenGlView?>(null)
    val openGlView: StateFlow<OpenGlView?> = _openGlView.asStateFlow()

    // Audio level state (0.0f - 1.0f)
    private val _audioLevel = MutableStateFlow(0.0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // Flash overlay state
    private val _flashOverlayVisible = MutableStateFlow(false)
    val flashOverlayVisible: StateFlow<Boolean> = _flashOverlayVisible.asStateFlow()

    private val confirmAction = MutableStateFlow<() -> Unit> {}

    init {
        StreamService.observer.observeForever { service ->
            streamServiceRef = service?.let { WeakReference(it) }
            updateAllStatesFromService(service)
            service?.let { srv ->
                _openGlView.value?.let { view ->
                    if (!srv.isPreviewRunning()) {
                        startPreview(view)
                    }
                }
            } ?: run {
                _audioLevel.value = 0.0f
            }
        }
    }

    private fun updateAllStatesFromService(service: StreamService?) {
        service?.let { srv ->
            updateStreamingState(srv.isStreaming())
            updateRecordingState(srv.isRecording())
            updateStreamInfo(srv.getStreamInfo())
        } ?: run {
            updateStreamingState(false)
            updateRecordingState(false)
            updateStreamInfo(StreamInfo())
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
                _isServiceBound.value = true

                updateAllStatesFromService(streamService)
            }

            override fun onServiceDisconnected(arg0: ComponentName) {
                Log.d(TAG, "Service disconnected")
                streamServiceRef = null
                _isServiceBound.value = false
                updateAllStatesFromService(null)
                _audioLevel.value = 0.0f
            }
        }

    fun bindService(context: Context) {
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
                    val service = streamServiceRef?.get()
                    when (intent.action) {
                        AppIntentActions.BROADCAST_AUDIO_LEVEL -> {
                            val level = intent.getFloatExtra(AppIntentActions.EXTRA_AUDIO_LEVEL, 0.0f)
                            _audioLevel.value = level
                        }

                        AppIntentActions.BROADCAST_STREAM_STOPPED,
                        AppIntentActions.BROADCAST_STREAM_STARTED,
                        AppIntentActions.BROADCAST_RECORD_STARTED,
                        AppIntentActions.BROADCAST_RECORD_STOPPED,
                            -> {
                                updateAllStatesFromService(service)
                            }
                    }
                }
            }

        val filter =
            IntentFilter().apply {
                addAction(AppIntentActions.BROADCAST_AUDIO_LEVEL)
                addAction(AppIntentActions.BROADCAST_STREAM_STOPPED)
                addAction(AppIntentActions.BROADCAST_STREAM_STARTED)
                addAction(AppIntentActions.BROADCAST_RECORD_STARTED)
                addAction(AppIntentActions.BROADCAST_RECORD_STOPPED)
            }

        LocalBroadcastManager
            .getInstance(context)
            .registerReceiver(broadcastReceiver!!, filter)
    }

    fun unbindService(context: Context) {
        if (isServiceBound.value) {
            try {
                context.unbindService(connection)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Service not registered or already unbound? ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding service", e)
            }
            _isServiceBound.value = false
            streamServiceRef = null
        } else {
            Log.d(TAG, "Unbind requested but already unbound.")
        }

        broadcastReceiver?.let {
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(it)
                broadcastReceiver = null
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering broadcast receiver", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamServiceRef = null
    }

    fun startPreview() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "ViewModel requesting to start preview with existing view")
                _openGlView.value?.let { view ->
                    streamServiceRef?.get()?.startPreview(view)
                } ?: Log.e(TAG, "Cannot start preview - no OpenGlView available")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting preview", e)
            }
        }
    }

    fun startPreview(view: OpenGlView) {
        // No need for complex state checking - the service will handle it
        viewModelScope.launch {
            try {
                Log.d(TAG, "ViewModel requesting to start preview")
                _openGlView.value = view
                streamServiceRef?.get()?.startPreview(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting preview", e)
            }
        }
    }

    fun stopPreview() {
        viewModelScope.launch {
            Log.d(TAG, "ViewModel requesting to stop preview")
            streamServiceRef?.get()?.stopPreview()
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

    fun updateStreamingState(isStreaming: Boolean) {
        _isStreaming.value = isStreaming
        if (isStreaming) {
            streamServiceRef?.get()?.getStreamInfo()?.let { updateStreamInfo(it) }
        }
    }

    fun updateRecordingState(isRecording: Boolean) {
        _isRecording.value = isRecording
        if (isRecording) {
            streamServiceRef?.get()?.getStreamInfo()?.let { updateStreamInfo(it) }
        }
    }

    fun toggleFlash() {
        viewModelScope.launch {
            streamServiceRef?.get()?.toggleLantern() == true
            streamServiceRef?.get()?.let {
                _isFlashOn.value = !_isFlashOn.value
            }
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            val currentMuteState = _isMuted.value
            streamServiceRef?.get()?.toggleMute(!currentMuteState)
            streamServiceRef?.get()?.let {
                _isMuted.value = !currentMuteState
            }
        }
    }

    fun toggleStreamInfo() {
        _showStreamInfo.value = !_showStreamInfo.value
    }

    fun updateStreamInfo(info: StreamInfo) {
        _streamInfo.value = info
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
        if (isStreaming.value || isRecording.value) {
            requestConfirmation(true) { stopStreaming() }
        }
    }

    fun hideFlashOverlayAfterDelay() {
        viewModelScope.launch {
            delay(150)
            _flashOverlayVisible.value = false
        }
    }

    fun refreshStreamInfo() {
        viewModelScope.launch {
            streamServiceRef?.get()?.let { service ->
                val info = service.getStreamInfo()
                updateStreamInfo(info)
            }
        }
    }

    fun requestConfirmation(
        show: Boolean,
        actionOnConfirm: (() -> Unit)? = null,
    ) {
        if (show) {
            confirmAction.value = actionOnConfirm ?: {}
        }
        _showSettingsConfirmDialog.value = show
        if (!show) {
            confirmAction.value = {}
        }
    }

    fun confirmRequestedAction() {
        viewModelScope.launch {
            try {
                confirmAction.value()
            } catch (e: Exception) {
                Log.e(TAG, "Error executing confirmed action", e)
            } finally {
                requestConfirmation(false)
            }
        }
    }
}
