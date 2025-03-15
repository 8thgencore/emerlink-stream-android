package net.emerlink.stream.util

import android.os.Build
import android.os.Environment
import java.io.File

object PathUtils {
    fun getRecordPath(): File {
        // For Android 10+ we should use MediaStore API (handled in MediaManager)
        // but we'll provide a fallback path for direct file operations
        val folder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+, we'll still return a path, but MediaStore API should be used
                // This is used as a fallback or for file path references
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "EmerlinkStream"
                )
            } else {
                // For pre-Android 10, direct file access to public directory
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "EmerlinkStream"
                )
            }

        // Ensure directory exists
        if (!folder.exists()) {
            folder.mkdirs()
        }

        return folder
    }

    fun getPhotoPath(): File {
        val folder =
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "EmerlinkStream"
            )

        if (!folder.exists()) {
            folder.mkdirs()
        }

        return folder
    }
}
