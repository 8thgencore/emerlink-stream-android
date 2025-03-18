package net.emerlink.stream.presentation.camera.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.emerlink.stream.data.model.StreamInfo
import net.emerlink.stream.service.StreamService
import java.lang.ref.WeakReference

/**
 * ViewModel for managing CameraScreen state
 */
class CameraViewModel : ViewModel() {
    private var streamServiceRef: WeakReference<StreamService>? = null
    private var bound = false

    // UI state
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

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

    private val _screenWasOff = MutableStateFlow(false)
    val screenWasOff: StateFlow<Boolean> = _screenWasOff.asStateFlow()

    private val _isPreviewActive = MutableStateFlow(false)
    val isPreviewActive: StateFlow<Boolean> = _isPreviewActive.asStateFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    private val _permissionType = MutableStateFlow("")
    val permissionType: StateFlow<String> = _permissionType.asStateFlow()

    private val _openGlView = MutableStateFlow<OpenGlView?>(null)
    val openGlView: StateFlow<OpenGlView?> = _openGlView.asStateFlow()

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

                // Signal that service is connected
                _isServiceConnected.value = true

                // Initialize camera if we already have OpenGlView
                _openGlView.value?.let { view ->
                    startPreview(view)
                }
            }

            override fun onServiceDisconnected(arg0: ComponentName) {
                streamServiceRef = null
                bound = false
                _isServiceConnected.value = false
            }
        }

    fun bindService(context: Context) {
        Intent(context, StreamService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService(context: Context) {
        if (bound) {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error unbinding service", e)
            }
            bound = false
            streamServiceRef = null
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
                setPreviewActive(true)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error starting preview", e)
            }
        }
    }

    fun stopPreview() {
        viewModelScope.launch {
            try {
                streamServiceRef?.get()?.stopPreview()
                setPreviewActive(false)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error stopping preview", e)
            }
        }
    }

    fun releaseCamera() {
        viewModelScope.launch {
            try {
                streamServiceRef?.get()?.releaseCamera()
                setPreviewActive(false)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error releasing camera", e)
            }
        }
    }

    fun restartPreview(view: OpenGlView) {
        viewModelScope.launch {
            try {
                streamServiceRef?.get()?.restartPreview(view)
                setPreviewActive(true)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error restarting preview", e)
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
                streamServiceRef?.get()?.takePhoto()
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error taking photo", e)
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
                val result = streamServiceRef?.get()?.toggleLantern() ?: false
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

    fun setScreenWasOff(wasOff: Boolean) {
        _screenWasOff.value = wasOff
    }

    fun setPreviewActive(isActive: Boolean) {
        _isPreviewActive.value = isActive
    }

    fun showPermissionDialog(type: String) {
        _permissionType.value = type
        _showPermissionDialog.value = true
    }

    fun dismissPermissionDialog() {
        _showPermissionDialog.value = false
    }

    fun setOpenGlView(view: OpenGlView?) {
        _openGlView.value = view
    }

    fun startStreaming() {
        viewModelScope.launch {
            try {
                val service = streamServiceRef?.get()
                if (service != null && !service.isStreaming()) {
                    Log.d("CameraViewModel", "Starting stream")
                    service.startStream()
                    updateStreamingState(true)
                    updateStreamInfo(service.getStreamInfo())
                } else {
                    Log.d("CameraViewModel", "Stream already running or service unavailable")
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error starting stream", e)
            }
        }
    }

    fun stopStreaming() {
        viewModelScope.launch {
            try {
                val service = streamServiceRef?.get()
                if (service != null && service.isStreaming()) {
                    Log.d("CameraViewModel", "Stopping stream")
                    service.stopStream(null, null)
                    updateStreamingState(false)
                } else {
                    Log.d("CameraViewModel", "Stream not running or service unavailable")
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error stopping stream", e)
            }
        }
    }
}
