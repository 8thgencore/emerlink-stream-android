package net.emerlink.stream.core.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import net.emerlink.stream.R
import net.emerlink.stream.app.MainActivity
import net.emerlink.stream.core.AppIntentActions

/**
 * Класс для управления уведомлениями приложения. Реализует паттерн Singleton для обеспечения единой
 * точки доступа к уведомлениям.
 */
class NotificationManager
private constructor(
    private val context: Context,
) {
    companion object {
        private const val TAG = "NotificationManager"

        // Идентификаторы каналов уведомлений
        const val NOTIFICATION_CHANNEL_ID = "CameraServiceChannel"
        const val ERROR_CHANNEL_ID = "ErrorChannel"
        const val PHOTO_CHANNEL_ID = "PhotoChannel"

        // Идентификаторы уведомлений
        const val START_STREAM_NOTIFICATION_ID = 3425
        const val ERROR_STREAM_NOTIFICATION_ID = 3426
        const val TAKE_PHOTO_NOTIFICATION_ID = 3427

        // Типы действий для уведомлений
        const val ACTION_NONE = 0
        const val ACTION_STOP_ONLY = 1
        const val ACTION_ALL = 2

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: NotificationManager? = null

        fun getInstance(context: Context): NotificationManager =
            instance
                ?: synchronized(this) {
                    instance
                        ?: NotificationManager(context.applicationContext).also {
                            instance = it
                        }
                }
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as
            android.app.NotificationManager

    init {
        createNotificationChannels()
    }

    /** Создает каналы уведомлений для Android 8.0 (API 26) и выше */
    private fun createNotificationChannels() {
        // Канал для сервиса стриминга
        val serviceChannel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.notification_channel_stream_service),
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    context.getString(
                        R.string.notification_channel_stream_service_description
                    )
                setShowBadge(false)
            }

        // Канал для ошибок
        val errorChannel =
            NotificationChannel(
                ERROR_CHANNEL_ID,
                context.getString(R.string.notification_channel_errors),
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    context.getString(
                        R.string.notification_channel_errors_description
                    )
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

        // Канал для уведомлений о фото
        val photoChannel =
            NotificationChannel(
                PHOTO_CHANNEL_ID,
                context.getString(R.string.notification_channel_photos),
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    context.getString(
                        R.string.notification_channel_photos_description
                    )
                setShowBadge(true)
            }

        // Регистрируем все каналы
        notificationManager.createNotificationChannels(
            listOf(serviceChannel, errorChannel, photoChannel)
        )
    }

    /** Создает объект уведомления с заданными параметрами */
    fun createNotification(
        text: String,
        ongoing: Boolean,
        actionType: Int = ACTION_ALL,
        isError: Boolean = false,
        isPhoto: Boolean = false,
    ): Notification {
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            pendingIntentFlags
        )

        val channelId = when {
            isError -> ERROR_CHANNEL_ID
            isPhoto -> PHOTO_CHANNEL_ID
            else -> NOTIFICATION_CHANNEL_ID
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(!ongoing)

        // Add actions based on type
        when (actionType) {
            ACTION_ALL -> {
                addStopAction(builder)
                addExitAction(builder)
            }

            ACTION_STOP_ONLY -> {
                addStopAction(builder)
            }

            ACTION_NONE -> {
                if (isError) {
                    addDismissAction(builder)
                }
            }
        }

        return builder.build()
    }

    private fun addStopAction(builder: NotificationCompat.Builder) {
        val stopIntent = Intent(AppIntentActions.STOP_STREAM).apply {
            setPackage(context.packageName)
        }

        val stopPendingIntent = PendingIntent.getBroadcast(
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

    private fun addExitAction(builder: NotificationCompat.Builder) {
        val exitIntent = Intent(AppIntentActions.EXIT_APP).apply {
            setPackage(context.packageName)
        }

        val exitPendingIntent = PendingIntent.getBroadcast(
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

    private fun addDismissAction(builder: NotificationCompat.Builder) {
        val dismissIntent = Intent(AppIntentActions.DISMISS_ERROR).apply {
            setPackage(context.packageName)
        }

        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            dismissIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            context.getString(android.R.string.ok),
            dismissPendingIntent
        )
    }

    /**
     * Безопасно очищает уведомления о стриминге, учитывая состояние foreground сервиса.
     * @param service Сервис, который может быть в foreground состоянии
     */
    fun clearStreamingNotificationsSafely(service: Service?) {
        if (service != null) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            Log.d(TAG, "Foreground service stopped, notification removed")
        } else {
            notificationManager.cancel(START_STREAM_NOTIFICATION_ID)
        }
    }

    /** Показывает уведомление о стриминге */
    fun showStreamingNotification(
        text: String,
        ongoing: Boolean,
        actionType: Int = ACTION_ALL,
    ) {
        Log.d(TAG, "Showing streaming notification: $text")

        try {
            val notification = createNotification(text, ongoing, actionType)
            notificationManager.notify(START_STREAM_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing streaming notification: ${e.message}", e)
        }
    }

    /** Показывает уведомление об ошибке */
    private fun showErrorNotification(
        text: String,
        actionType: Int = ACTION_NONE,
    ) {
        val notification = createNotification(text, false, actionType, true)
        notificationManager.notify(ERROR_STREAM_NOTIFICATION_ID, notification)
    }

    /**
     * Безопасно показывает уведомление об ошибке, учитывая состояние foreground сервиса.
     * @param service Сервис, который может быть в foreground состоянии
     * @param text Текст уведомления
     * @param actionType Тип действия для уведомления
     */
    fun showErrorSafely(
        service: Service?,
        text: String,
        actionType: Int = ACTION_NONE,
    ) {
        Log.d(TAG, "Safely showing error notification with service: $text")

        try {
            service?.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            showErrorNotification(text, actionType)
        } catch (e: Exception) {
            Log.e(TAG, "Error in showErrorSafely with service: ${e.message}", e)
            showErrorNotification(text, actionType)
        }
    }

    /**
     * Безопасно показывает уведомление об ошибке без ссылки на сервис.
     * Используется в случаях, когда нет доступа к сервису или контекст не является сервисом.
     * @param text Текст уведомления
     * @param actionType Тип действия для уведомления
     */
    fun showErrorSafely(
        text: String,
        actionType: Int = ACTION_NONE,
    ) {
        Log.d(TAG, "Safely showing error notification without service: $text")

        try {
            // Попытка отменить существующее уведомление стриминга перед показом ошибки
            notificationManager.cancel(START_STREAM_NOTIFICATION_ID)

            // Показ уведомления об ошибке
            showErrorNotification(text, actionType)
        } catch (e: Exception) {
            Log.e(TAG, "Error in showErrorSafely without service: ${e.message}", e)
            showErrorNotification(text, actionType)
        }
    }

    /** Показывает уведомление о фото */
    fun showPhotoNotification(text: String) {
        Log.d(TAG, "Showing photo notification: $text")

        try {
            val notification =
                createNotification(text, false, ACTION_NONE, isError = false, isPhoto = true)
            notificationManager.notify(TAKE_PHOTO_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing photo notification: ${e.message}", e)
        }
    }

    // Add method for creating basic notification
    fun createBasicNotification(text: String, ongoing: Boolean): Notification {
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .build()
    }
}
