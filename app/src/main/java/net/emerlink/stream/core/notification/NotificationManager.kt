@file:Suppress("ktlint:standard:filename", "ktlint:standard:no-wildcard-imports")

package net.emerlink.stream.core.notification

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import net.emerlink.stream.R
import net.emerlink.stream.app.MainActivity
import net.emerlink.stream.core.AppIntentActions

/**
 * Manages notifications for the application. Implements the Singleton pattern
 * to provide a single point of access to notifications.
 */
class AppNotificationManager
    private constructor(
        private val context: Context,
    ) {
        companion object {
            private const val TAG = "AppNotificationManager"

            // Channel IDs
            const val NOTIFICATION_CHANNEL_ID = "CameraServiceChannel"
            const val ERROR_CHANNEL_ID = "ErrorChannel"
            const val PHOTO_CHANNEL_ID = "PhotoChannel"

            // Notification IDs
            const val START_STREAM_NOTIFICATION_ID = 3425
            const val ERROR_STREAM_NOTIFICATION_ID = 3426
            const val TAKE_PHOTO_NOTIFICATION_ID = 3427

            @SuppressLint("StaticFieldLeak")
            @Volatile
            private var instance: AppNotificationManager? = null

            fun getInstance(context: Context): AppNotificationManager =
                instance
                    ?: synchronized(this) {
                        instance
                            ?: AppNotificationManager(context.applicationContext).also {
                                instance = it
                            }
                    }
        }

        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

        init {
            createNotificationChannels()
        }

        /**
         * Creates notification channels for Android 8.0 (API 26) and above
         */
        private fun createNotificationChannels() {
            // Service channel
            val serviceChannel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    TAG,
                    NotificationManager.IMPORTANCE_HIGH
                )

            // Error channel
            val errorChannel =
                NotificationChannel(
                    ERROR_CHANNEL_ID,
                    "Error Channel",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableLights(true)
                    enableVibration(true)
                }

            // Photo channel
            val photoChannel =
                NotificationChannel(
                    PHOTO_CHANNEL_ID,
                    "Photo Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
                )

            // Register all channels
            notificationManager.createNotificationChannels(
                listOf(serviceChannel, errorChannel, photoChannel)
            )
        }

        /**
         * Shows a notification with the specified text
         * Similar to showNotification in OpenTAK_ICU
         */
        fun showNotification(
            text: String,
            ongoing: Boolean,
            hasActions: Boolean = true,
        ) {
            val notification = createNotification(text, ongoing, hasActions)
            notificationManager.notify(START_STREAM_NOTIFICATION_ID, notification)
        }

        /**
         * Creates a notification with the specified text
         * @param text Text to display in the notification
         * @param ongoing Whether the notification is ongoing
         * @param hasActions Whether to include action buttons
         * @return The created notification
         */
        fun createNotification(
            text: String,
            ongoing: Boolean,
            hasActions: Boolean = true,
        ): Notification {
            // Create a pending intent to launch the main activity when notification is tapped
            val notificationIntent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

            val pendingIntentFlags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    notificationIntent,
                    pendingIntentFlags
                )

            // Build the notification
            val builder =
                NotificationCompat
                    .Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(text)
                    .setContentIntent(pendingIntent)
                    .setOngoing(ongoing)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)

            // Add actions based on whether we're streaming or ready to stream
            if (hasActions) {
                // Check if the notification text is "Ready to Stream"
                val isReadyToStream = text == context.getString(R.string.ready_to_stream)

                if (isReadyToStream) {
                    // Add START button for "Ready to Stream" notification
                    addStartAction(builder)
                } else {
                    // Add STOP button for streaming notification
                    addStopAction(builder)
                }

                // Always add EXIT action
                addExitAction(builder)
            }

            return builder.build()
        }

        /**
         * Adds a start streaming action to the notification
         */
        private fun addStartAction(builder: NotificationCompat.Builder) {
            val startIntent =
                Intent(AppIntentActions.START_STREAM).apply {
                    setPackage(context.packageName)
                }

            val startPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    startIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )

            builder.addAction(
                R.drawable.ic_start,
                context.getString(R.string.start),
                startPendingIntent
            )
        }

        /**
         * Adds a stop streaming action to the notification
         */
        private fun addStopAction(builder: NotificationCompat.Builder) {
            val stopIntent =
                Intent(AppIntentActions.STOP_STREAM).apply {
                    setPackage(context.packageName)
                }

            val stopPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    stopIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )

            builder.addAction(
                R.drawable.ic_stop,
                context.getString(R.string.stop),
                stopPendingIntent
            )
        }

        /**
         * Adds an exit action to the notification
         */
        private fun addExitAction(builder: NotificationCompat.Builder) {
            val exitIntent =
                Intent(AppIntentActions.EXIT_APP).apply {
                    setPackage(context.packageName)
                }

            val exitPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    1,
                    exitIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )

            builder.addAction(
                R.drawable.ic_exit,
                context.getString(R.string.exit),
                exitPendingIntent
            )
        }

        /**
         * Shows error notification
         */
        fun showErrorNotification(text: String) {
            // For error notifications, create a special notification
            val notificationIntent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

            val pendingIntentFlags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    notificationIntent,
                    pendingIntentFlags
                )

            val builder =
                NotificationCompat
                    .Builder(context, ERROR_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(text)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)

            notificationManager.notify(ERROR_STREAM_NOTIFICATION_ID, builder.build())
        }

        /**
         * Safely shows an error notification
         */
        fun showErrorSafely(
            service: Service?,
            text: String,
        ) {
            try {
                service?.stopForeground(Service.STOP_FOREGROUND_REMOVE)
                showErrorNotification(text)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing error notification: ${e.message}", e)
                showErrorNotification(text)
            }
        }

        /**
         * Safely shows an error notification without a service reference
         */
        fun showErrorSafely(text: String) {
            try {
                showErrorNotification(text)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing error notification: ${e.message}", e)
            }
        }

        /**
         * Shows a photo notification
         */
        fun showPhotoNotification(text: String) {
            val notification =
                NotificationCompat
                    .Builder(context, PHOTO_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()

            notificationManager.notify(TAKE_PHOTO_NOTIFICATION_ID, notification)
        }
    }
