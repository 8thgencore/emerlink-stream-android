package net.emerlink.stream.util

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

object PathUtils {
    fun getRecordPath(context: Context): File =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: File(context.filesDir, "recordings")
        } else {
            val folder =
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "EmerlinkStream",
                )
            if (!folder.exists()) {
                folder.mkdirs()
            }
            folder
        }
}
