package net.emerlink.stream.model

import android.content.SharedPreferences
import android.util.Log
import net.emerlink.stream.data.preferences.PreferenceKeys
import java.util.Locale

data class Resolution(
    val width: Int = 1920,
    val height: Int = 1080
) {
    companion object {
        private const val TAG = "Resolution"

        fun parseFromPreferences(preferences: SharedPreferences): Resolution {
            return try {
                val videoResolution = preferences.getString(
                    PreferenceKeys.VIDEO_RESOLUTION,
                    PreferenceKeys.VIDEO_RESOLUTION_DEFAULT
                ) ?: "1920x1080"

                parseFromString(videoResolution)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing resolution from preferences: ${e.message}")
                Resolution()
            }
        }

        private fun parseFromString(resolution: String): Resolution {
            return try {
                val dimensions = resolution
                    .lowercase(Locale.getDefault())
                    .replace("х", "x")
                    .split("x")

                val width = if (dimensions.isNotEmpty()) {
                    dimensions[0].toIntOrNull() ?: 1920
                } else 1920

                val height = if (dimensions.size >= 2) {
                    dimensions[1].toIntOrNull() ?: 1080
                } else 1080

                Resolution(width, height)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing resolution string: ${e.message}")
                Resolution()
            }
        }
    }
} 