package net.emerlink.stream.service.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.pedro.library.view.GlInterface
import net.emerlink.stream.R
import net.emerlink.stream.notification.NotificationManager
import net.emerlink.stream.service.stream.StreamManager
import net.emerlink.stream.util.AppIntentActions
import net.emerlink.stream.util.PathUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages media operations like taking photos and recording videos
 */
class MediaManager(
    private val context: Context,
    private val streamManager: StreamManager,
    private val notificationManager: NotificationManager,
) {
    companion object {
        private const val TAG = "MediaManager"
        val screenshotTaken = MutableLiveData<Boolean>()
    }

    private val folder: File = PathUtils.getRecordPath(context)
    private var currentDateAndTime: String = ""

    /**
     * Takes a photo using the current camera preview
     */
    fun takePhoto() {
        try {
            Log.d(TAG, "Taking photo")

            val glInterface = streamManager.getGlInterface()

            if (glInterface is GlInterface) {
                glInterface.takePhoto { bitmap ->
                    val handlerThread = HandlerThread("PhotoSaveThread")
                    handlerThread.start()
                    Handler(handlerThread.looper).post { saveBitmapToGallery(bitmap) }
                }
            } else {
                Log.e(TAG, "glInterface is of wrong type: ${glInterface.javaClass.name}")
                notificationManager.showErrorNotification(
                    context.getString(R.string.saved_photo_failed)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error taking photo: ${e.message}", e)
            notificationManager.showErrorNotification(
                context.getString(R.string.saved_photo_failed) + ": " + e.message
            )
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

            // Ensure the folder exists
            if (!folder.exists() && !folder.mkdirs()) {
                Log.e(TAG, "Failed to create folder: ${folder.absolutePath}")
                notificationManager.showErrorNotification(
                    context.getString(R.string.saved_photo_failed)
                )
                return
            }

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
            notificationManager.showErrorNotification(
                context.getString(R.string.saved_photo_failed) + ": " + e.message
            )
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
                context.sendBroadcast(Intent(AppIntentActions.ACTION_TOOK_PICTURE))
                notificationManager.showPhotoNotification(context.getString(R.string.saved_photo))
            } ?: run {
                notificationManager.showErrorNotification(
                    context.getString(R.string.saved_photo_failed)
                )
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
        val filePath = "${folder.absolutePath}/$filename"
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

        Log.d(TAG, "Sending broadcast ACTION_TOOK_PICTURE")
        context.sendBroadcast(Intent(AppIntentActions.ACTION_TOOK_PICTURE))
        notificationManager.showPhotoNotification(context.getString(R.string.saved_photo))
    }

    /**
     * Starts video recording
     * @return true if recording started successfully, false otherwise
     */
    fun startRecording(): Boolean {
        Log.d(TAG, "Starting recording")

        try {
            // Ensure the folder exists
            if (!folder.exists() && !folder.mkdirs()) {
                Log.e(TAG, "Failed to create folder: ${folder.absolutePath}")
                notificationManager.showErrorNotification(context.getString(R.string.failed_to_record))
                return false
            }

            // Generate filename with current date and time
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
            currentDateAndTime = dateFormat.format(Date())
            val filename = "EmerlinkStream_$currentDateAndTime.mp4"
            val filePath = "${folder.absolutePath}/$filename"

            Log.d(TAG, "Recording to: $filePath")

            // Start recording
            streamManager.startRecord(filePath)

            // Show notification
            notificationManager.showStreamingNotification(
                context.getString(R.string.recording),
                true,
                NotificationManager.ACTION_STOP_ONLY
            )

            // Make the file visible in gallery after recording
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    arrayOf("video/mp4"),
                    null
                )
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            notificationManager.showErrorNotification(
                context.getString(R.string.failed_to_record) + ": " + e.message
            )
            return false
        }
    }

    /**
     * Stops video recording
     */
    fun stopRecording() {
        try {
            if (streamManager.isRecording()) {
                Log.d(TAG, "Stopping recording")
                streamManager.stopRecord()

                // Scan the recorded file to make it visible in gallery
                val filename = "EmerlinkStream_$currentDateAndTime.mp4"
                val filePath = "${folder.absolutePath}/$filename"

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
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
}
