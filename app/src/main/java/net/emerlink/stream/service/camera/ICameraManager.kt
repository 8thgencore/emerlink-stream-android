package net.emerlink.stream.service.camera

import android.view.MotionEvent

/**
 * Interface defining camera operations independent of the streaming functionality
 */
interface ICameraManager {
    /**
     * Switches between available cameras
     * @return true if camera switched successfully, false otherwise
     */
    fun switchCamera(): Boolean

    /**
     * Toggles the flashlight/lantern
     * @return true if lantern is enabled after toggle, false otherwise
     */
    fun toggleLantern(): Boolean

    /**
     * Handles zoom gestures
     */
    fun setZoom(motionEvent: MotionEvent)

    /**
     * Handles tap-to-focus gestures
     */
    fun tapToFocus(motionEvent: MotionEvent)

    /**
     * Gets the current zoom level
     * @return current zoom level or 0 if not available
     */
    fun getZoom(): Float

    /**
     * Gets the current camera ID
     */
    fun getCurrentCameraId(): Int

    /**
     * Checks if the lantern/flashlight is currently enabled
     */
    fun isLanternEnabled(): Boolean
}
