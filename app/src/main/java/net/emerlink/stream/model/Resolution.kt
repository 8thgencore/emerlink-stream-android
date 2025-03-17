package net.emerlink.stream.model

import android.content.SharedPreferences
import android.util.Log
import android.util.Size
import net.emerlink.stream.data.preferences.PreferenceKeys
import java.util.Locale

data class Resolution(
    val width: Int = 1920,
    val height: Int = 1080,
) {
    companion object {
        private const val TAG = "Resolution"
        private const val DEFAULT_WIDTH = 1920
        private const val DEFAULT_HEIGHT = 1080

        fun parseFromPreferences(preferences: SharedPreferences): Resolution =
            try {
                val videoResolution =
                    preferences.getString(
                        PreferenceKeys.VIDEO_RESOLUTION,
                        PreferenceKeys.VIDEO_RESOLUTION_DEFAULT
                    ) ?: "1920x1080"
                parseFromString(videoResolution)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing resolution from preferences: ${e.message}")
                Resolution()
            }

        fun parseFromString(resolution: String): Resolution =
            try {
                val dimensions =
                    resolution
                        .lowercase(Locale.getDefault())
                        .replace("х", "x")
                        .split("x")

                val width = dimensions.getOrNull(0)?.toIntOrNull() ?: DEFAULT_WIDTH
                val height = dimensions.getOrNull(1)?.toIntOrNull() ?: DEFAULT_HEIGHT

                Resolution(width, height)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing resolution string: ${e.message}")
                Resolution()
            }

        fun parseFromSize(size: Size?): Resolution = size?.let { Resolution(it.width, it.height) } ?: Resolution()
    }
}
