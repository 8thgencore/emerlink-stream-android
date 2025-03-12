package net.emerlink.stream.service.camera

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import com.pedro.encoder.input.sources.video.Camera2Source

/**
 * Implementation of ICameraManager that works with Camera2Source and CameraInterface
 */
class CameraManager(
    private val context: Context,
    private val videoSourceProvider: () -> Any,
    private val cameraInterfaceProvider: () -> CameraInterface,
) : ICameraManager {
    companion object {
        private const val TAG = "CameraManager"
    }

    private val cameraIds = ArrayList<String>()
    private var currentCameraId = 0
    private var lanternEnabled = false

    init {
        getCameraIds()
    }

    /**
     * Gets available camera IDs from the Camera2Source
     */
    private fun getCameraIds() {
        withCamera2Source({ camera2Source ->
            cameraIds.clear()
            cameraIds.addAll(camera2Source.camerasAvailable().toList())
            Log.d(TAG, "Got cameraIds $cameraIds")
        }, Unit)
    }

    /**
     * Executes an action with Camera2Source if it's available
     */
    private fun <T> withCamera2Source(
        action: (Camera2Source) -> T,
        defaultValue: T,
    ): T {
        val videoSource = videoSourceProvider()
        if (videoSource is Camera2Source) {
            return action(videoSource)
        }
        return defaultValue
    }

    /**
     * Switches between available cameras
     * @return true if camera switched successfully, false otherwise
     */
    override fun switchCamera(): Boolean {
        try {
            // First try using the Camera2Source approach
            val camera2Result =
                withCamera2Source({ camera2Source ->
                    Log.d(TAG, "Switching camera using Camera2Source")

                    if (cameraIds.isEmpty()) getCameraIds()
                    if (cameraIds.isEmpty()) return@withCamera2Source false

                    // Switch the camera
                    currentCameraId = (currentCameraId + 1) % cameraIds.size

                    Log.d(TAG, "Switching to camera ${cameraIds[currentCameraId]}")
                    camera2Source.openCameraId(cameraIds[currentCameraId])
                    true
                }, false)

            // If Camera2Source approach failed, try using the CameraInterface directly
            if (!camera2Result) {
                Log.d(TAG, "Switching camera using CameraInterface")
                cameraInterfaceProvider().switchCamera()
                currentCameraId = if (currentCameraId == 0) 1 else 0
                return true
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error switching camera: ${e.message}", e)
            return false
        }
    }

    /**
     * Toggles the flashlight/lantern
     * @return true if lantern is enabled after toggle, false otherwise
     */
    override fun toggleLantern(): Boolean {
        try {
            // First try using the Camera2Source approach
            val camera2Result =
                withCamera2Source({ camera2Source ->
                    try {
                        Log.d(TAG, "Toggling lantern using Camera2Source")
                        val wasEnabled = camera2Source.isLanternEnabled()
                        if (wasEnabled) {
                            camera2Source.disableLantern()
                            Log.d(TAG, "Lantern disabled")
                        } else {
                            camera2Source.enableLantern()
                            Log.d(TAG, "Lantern enabled")
                        }
                        camera2Source.isLanternEnabled()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to toggle lantern with Camera2Source: ${e.localizedMessage}", e)
                        false
                    }
                }, false)

            // If Camera2Source approach failed, try using the CameraInterface directly
            if (!camera2Result) {
                Log.d(TAG, "Toggling lantern using CameraInterface")
                val cameraInterface = cameraInterfaceProvider()
                lanternEnabled = !lanternEnabled

                if (lanternEnabled) {
                    cameraInterface.enableLantern()
                    Log.d(TAG, "Lantern enabled via CameraInterface")
                } else {
                    cameraInterface.disableLantern()
                    Log.d(TAG, "Lantern disabled via CameraInterface")
                }

                return lanternEnabled
            }

            return true
        } catch (e: Exception) {
            // If all else fails, try the fallback method
            Log.e(TAG, "Error toggling lantern, trying fallback: ${e.message}", e)
            return fallbackToggleLantern()
        }
    }

    /**
     * Fallback method to toggle lantern using Android's CameraManager
     * when Camera2Source is not available
     */
    private fun fallbackToggleLantern(): Boolean {
        try {
            Log.d(TAG, "Using fallback lantern toggle")
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager

            // Find a camera that supports flash
            val cameraId =
                camManager.cameraIdList.firstOrNull { id ->
                    val characteristics = camManager.getCameraCharacteristics(id)
                    val flashAvailable =
                        characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)
                    flashAvailable == true
                }

            if (cameraId != null) {
                lanternEnabled = !lanternEnabled
                Log.d(TAG, "Setting torch mode to $lanternEnabled for camera $cameraId")
                camManager.setTorchMode(cameraId, lanternEnabled)
                return lanternEnabled
            } else {
                Log.e(TAG, "No camera with flash found")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback lantern toggle", e)
            lanternEnabled = false
            return false
        }
    }

    /**
     * Handles zoom gestures
     */
    override fun setZoom(motionEvent: MotionEvent) {
        withCamera2Source({ camera2Source ->
            camera2Source.setZoom(motionEvent)
        }, Unit)
    }

    /**
     * Handles tap-to-focus gestures
     */
    override fun tapToFocus(motionEvent: MotionEvent) {
        withCamera2Source({ camera2Source ->
            camera2Source.tapToFocus(motionEvent)
        }, Unit)
    }

    /**
     * Gets the current zoom level
     * @return current zoom level or 0 if not available
     */
    override fun getZoom(): Float =
        withCamera2Source({ camera2Source ->
            Log.d(TAG, "Zoom is ${camera2Source.getZoom()}")
            val zoomRange = camera2Source.getZoomRange()
            if (camera2Source.getZoom() < zoomRange.lower || camera2Source.getZoom() > zoomRange.upper) {
                zoomRange.lower
            } else {
                camera2Source.getZoom()
            }
        }, 0f)

    /**
     * Gets the current camera ID
     */
    override fun getCurrentCameraId(): Int = currentCameraId

    /**
     * Checks if the lantern/flashlight is currently enabled
     */
    override fun isLanternEnabled(): Boolean = withCamera2Source({ it.isLanternEnabled() }, lanternEnabled)
}
