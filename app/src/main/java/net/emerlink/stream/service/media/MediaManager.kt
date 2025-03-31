@file:Suppress("ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.service.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.*
import android.util.Log
import androidx.lifecycle.MutableLiveData
import net.emerlink.stream.R
import net.emerlink.stream.core.AppIntentActions
import net.emerlink.stream.core.notification.AppNotificationManager
import net.emerlink.stream.service.StreamService
import net.emerlink.stream.util.PathUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages media operations like taking photos and recording videos
 */
class MediaManager(
    private val context: Context,
    private val streamService: StreamService,
    private val notificationManager: AppNotificationManager,
) {
    companion object {
        private const val TAG = "MediaManager"
        val screenshotTaken = MutableLiveData<Boolean>()
    }

    private val videoFolder: File = PathUtils.getRecordPath()
    private val photoFolder: File = PathUtils.getPhotoPath()
    private var currentDateAndTime: String = ""

    /**
     * Takes a photo using the current camera preview
     */
    fun takePhoto() {
        try {
            Log.d(TAG, "Taking photo")

            val glInterface = streamService.getGlInterface()

            glInterface.takePhoto { bitmap: Bitmap ->
                val handlerThread = HandlerThread("PhotoSaveThread")
                handlerThread.start()
                Handler(handlerThread.looper).post { saveBitmapToGallery(bitmap) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error taking photo: ${e.message}", e)
            val errorMessage = context.getString(R.string.saved_photo_failed) + ": " + e.message
            notificationManager.showErrorSafely(errorMessage)
        }
    }

    /**
     * Saves a bitmap to the gallery
     */
    private fun saveBitmapToGallery(bitmap: Bitmap) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
            val currentDateAndTime = dateFormat.format(Date())
            val filename = "EmerlinkStream_$currentDateAndTime.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore API for Android 10+
                savePhotoWithMediaStore(filename, bitmap)
            } else {
                // Use direct file access for older Android versions
                savePhotoWithDirectAccess(filename, bitmap)
            }

            // Notify UI about screenshot
            Handler(Looper.getMainLooper()).post {
                Log.d(TAG, "Notifying via LiveData about screenshot")
                screenshotTaken.value = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving photo: ${e.message}", e)
            val errorMessage = context.getString(R.string.saved_photo_failed) + ": " + e.message
            notificationManager.showErrorSafely(errorMessage)
        }
    }

    /**
     * Saves photo using MediaStore API (Android 10+)
     */
    private fun savePhotoWithMediaStore(
        filename: String,
        bitmap: Bitmap,
    ) {
        val contentValues =
            ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/EmerlinkStream"
                )
            }

        val resolver = context.contentResolver
        val uri =
            resolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                Log.d(TAG, "Sending broadcast ACTION_TOOK_PICTURE")
                context.sendBroadcast(Intent(AppIntentActions.TOOK_PICTURE))
                notificationManager.showPhotoNotification(context.getString(R.string.saved_photo))
            } ?: run {
                val errorMessage = context.getString(R.string.saved_photo_failed)
                notificationManager.showErrorSafely(errorMessage)
            }
        }
    }

    /**
     * Saves photo using direct file access (pre-Android 10)
     */
    private fun savePhotoWithDirectAccess(
        filename: String,
        bitmap: Bitmap,
    ) {
        val filePath = "${photoFolder.absolutePath}/$filename"
        val file = File(filePath)

        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
        }

        // Make the file visible in gallery
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg"),
            null
        )

        Log.d(TAG, "Saved photo to: $filePath")
        Log.d(TAG, "Sending broadcast ACTION_TOOK_PICTURE")
        context.sendBroadcast(Intent(AppIntentActions.TOOK_PICTURE))
        notificationManager.showPhotoNotification(context.getString(R.string.saved_photo))
    }

    /**
     * Starts video recording
     * @return true if recording started successfully, false otherwise
     */
    fun startRecording(): Boolean {
        Log.d(TAG, "Starting recording")

        try {
            // Generate filename with current date and time
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
            currentDateAndTime = dateFormat.format(Date())
            val filename = "EmerlinkStream_$currentDateAndTime.mp4"

            val filePath: String

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+, use app-specific directory for recording
                // We'll move it to MediaStore after recording is complete
                val appFolder =
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                        ?: File(context.filesDir, "recordings")

                if (!appFolder.exists()) {
                    appFolder.mkdirs()
                }

                filePath = "${appFolder.absolutePath}/$filename"
            } else {
                // For pre-Android 10, ensure the public folder exists
                if (!videoFolder.exists() && !videoFolder.mkdirs()) {
                    Log.e(TAG, "Failed to create folder: ${videoFolder.absolutePath}")
                    val errorMessage = context.getString(R.string.failed_to_record)
                    notificationManager.showErrorSafely(errorMessage)
                    return false
                }
                filePath = "${videoFolder.absolutePath}/$filename"
            }

            Log.d(TAG, "Recording to: $filePath")

            // Start recording
            streamService.startRecord(filePath)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            val errorMessage = context.getString(R.string.failed_to_record) + ": " + e.message
            notificationManager.showErrorSafely(errorMessage)
            return false
        }
    }

    /**
     * Stops video recording
     */
    fun stopRecording() {
        try {
            if (streamService.isRecording()) {
                Log.d(TAG, "Stopping recording")
                streamService.stopRecord()

                // Get the recorded file path
                val filename = "EmerlinkStream_$currentDateAndTime.mp4"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // For Android 10+, move the file to MediaStore
                    val appFolder =
                        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                            ?: File(context.filesDir, "recordings")
                    val recordedFile = File("${appFolder.absolutePath}/$filename")

                    if (recordedFile.exists()) {
                        // Add to MediaStore
                        addVideoToMediaStore(recordedFile, filename)
                    } else {
                        Log.e(TAG, "Recorded file not found: ${recordedFile.absolutePath}")
                    }
                } else {
                    // For pre-Android 10, scan the file to make it visible in gallery
                    val filePath = "${videoFolder.absolutePath}/$filename"
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(filePath),
                        arrayOf("video/mp4"),
                        null
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
        }
    }

    /**
     * Adds a video file to MediaStore (for Android 10+)
     */
    private fun addVideoToMediaStore(
        videoFile: File,
        displayName: String,
    ) {
        try {
            val contentValues =
                ContentValues().apply {
                    put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/EmerlinkStream")
                    put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
                }

            val resolver = context.contentResolver
            val uri =
                resolver.insert(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    videoFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // Update IS_PENDING flag
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)

                // Delete the original file from app-specific storage
                videoFile.delete()

                Log.d(TAG, "Video added to MediaStore: $displayName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding video to MediaStore: ${e.message}", e)
        }
    }
}
