package net.emerlink.stream.service

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import com.pedro.encoder.input.sources.video.Camera2Source

class CameraManager(private val context: Context, private val streamManager: StreamManager) {
    companion object {
        private const val TAG = "CameraManager"
    }
    
    private val cameraIds = ArrayList<String>()
    private var currentCameraId = 0
    private var lanternEnabled = false
    
    private fun getCameraIds() {
        if (streamManager.getVideoSource() is Camera2Source) {
            val camera2Source = streamManager.getVideoSource() as Camera2Source
            cameraIds.clear()
            cameraIds.addAll(camera2Source.camerasAvailable().toList())
            Log.d(TAG, "Got cameraIds $cameraIds")
        }
    }
    
    fun switchCamera() {
        if (streamManager.getVideoSource() is Camera2Source) {
            Log.d(TAG, "Camera Changed")
            val camera2Source = streamManager.getVideoSource() as Camera2Source
            
            if (cameraIds.isEmpty()) getCameraIds()
            
            // Switch the camera
            currentCameraId++
            if (currentCameraId > cameraIds.size - 1) {
                currentCameraId = 0
            }
            
            Log.d(TAG, "Switching to camera ${cameraIds[currentCameraId]}")
            camera2Source.openCameraId(cameraIds[currentCameraId])
        }
    }
    
    fun toggleLantern(): Boolean {
        if (streamManager.getVideoSource() is Camera2Source) {
            val camera2Source = streamManager.getVideoSource() as Camera2Source
            if (camera2Source.isLanternEnabled()) {
                camera2Source.disableLantern()
            } else {
                try {
                    camera2Source.enableLantern()
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to enable lantern: ${e.localizedMessage}")
                    e.printStackTrace()
                }
            }
            return camera2Source.isLanternEnabled()
        } else {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            try {
                camManager.setTorchMode(camManager.cameraIdList[0], !lanternEnabled)
                lanternEnabled = !lanternEnabled
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return lanternEnabled
        }
    }
    
    fun setZoom(motionEvent: MotionEvent) {
        if (streamManager.getVideoSource() is Camera2Source) {
            val camera2Source = streamManager.getVideoSource() as Camera2Source
            camera2Source.setZoom(motionEvent)
        }
    }
    
    fun tapToFocus(motionEvent: MotionEvent) {
        if (streamManager.getVideoSource() is Camera2Source) {
            val camera2Source = streamManager.getVideoSource() as Camera2Source
            camera2Source.tapToFocus(motionEvent)
        }
    }
    
    fun getZoom(): Float {
        if (streamManager.getVideoSource() is Camera2Source) {
            val camera2Source = streamManager.getVideoSource() as Camera2Source
            Log.d(TAG, "Zoom is ${camera2Source.getZoom()}")
            val zoomRange = camera2Source.getZoomRange()
            if (camera2Source.getZoom() < zoomRange.lower || camera2Source.getZoom() > zoomRange.upper) {
                return zoomRange.lower
            }
            return camera2Source.getZoom()
        }
        Log.d(TAG, "Zoom is 0")
        return 0f
    }
} 