package net.emerlink.stream.presentation.ui.camera.viewmodel

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
import net.emerlink.stream.core.AppIntentActions
import net.emerlink.stream.data.model.StreamInfo
import net.emerlink.stream.service.StreamService

/**
 * ViewModel for managing CameraScreen state
 */
class CameraViewModel : ViewModel() {
    private var streamService: StreamService? = null
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
                streamService = binder.getService()
                bound = true

                // Initialize states
                updateStreamingState(streamService?.isStreaming() ?: false)
                setPreviewActive(streamService?.isPreviewRunning() ?: false)
                updateStreamInfo(streamService?.getStreamInfo() ?: StreamInfo())

                // Signal that service is connected
                _isServiceConnected.value = true

                // Initialize camera if we already have OpenGlView
                _openGlView.value?.let { view ->
                    startPreview(view)
                }
            }

            override fun onServiceDisconnected(arg0: ComponentName) {
                streamService = null
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
            context.unbindService(connection)
            bound = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamService = null
    }

    fun startPreview(view: OpenGlView) {
        viewModelScope.launch {
            try {
                streamService?.startPreview(view)
                setPreviewActive(true)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error starting preview", e)
            }
        }
    }

    fun stopPreview() {
        viewModelScope.launch {
            try {
                streamService?.stopPreview()
                setPreviewActive(false)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error stopping preview", e)
            }
        }
    }

    fun releaseCamera() {
        viewModelScope.launch {
            try {
                streamService?.releaseCamera()
                setPreviewActive(false)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error releasing camera", e)
            }
        }
    }

    fun restartPreview(view: OpenGlView) {
        viewModelScope.launch {
            try {
                streamService?.restartPreview(view)
                setPreviewActive(true)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error restarting preview", e)
            }
        }
    }

    fun tapToFocus(event: MotionEvent) {
        viewModelScope.launch {
            try {
                streamService?.tapToFocus(event)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error focusing", e)
            }
        }
    }

    fun setZoom(event: MotionEvent) {
        viewModelScope.launch {
            try {
                streamService?.setZoom(event)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error setting zoom", e)
            }
        }
    }

    fun takePhoto() {
        viewModelScope.launch {
            try {
                streamService?.takePhoto()
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error taking photo", e)
            }
        }
    }

    fun switchCamera() {
        viewModelScope.launch {
            try {
                streamService?.switchCamera()
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
                val result = streamService?.toggleLantern() ?: false
                _isFlashOn.value = result
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error toggling flash", e)
            }
        }
    }

    fun updateFlashState(isOn: Boolean) {
        _isFlashOn.value = isOn
    }

    fun toggleMute() {
        viewModelScope.launch {
            try {
                val newMuteState = !_isMuted.value
                streamService?.toggleMute(newMuteState)
                _isMuted.value = newMuteState
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error toggling mute", e)
            }
        }
    }

    fun updateMuteState(isMuted: Boolean) {
        _isMuted.value = isMuted
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
                if (streamService?.isStreaming() == false) {
                    val intent = Intent(AppIntentActions.ACTION_START_STREAM)
                    streamService?.sendBroadcast(intent)
                    _isStreaming.value = true
                    _streamInfo.value = streamService?.getStreamInfo() ?: StreamInfo()
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error starting streaming", e)
            }
        }
    }

    fun stopStreaming() {
        viewModelScope.launch {
            try {
                if (streamService?.isStreaming() == true) {
                    val intent = Intent(AppIntentActions.ACTION_STOP_STREAM)
                    streamService?.sendBroadcast(intent)
                    _isStreaming.value = false
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error stopping streaming", e)
            }
        }
    }
}
