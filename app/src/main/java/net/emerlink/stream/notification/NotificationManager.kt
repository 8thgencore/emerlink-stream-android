package net.emerlink.stream.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import net.emerlink.stream.MainActivity
import net.emerlink.stream.R
import net.emerlink.stream.util.AppConstants

/**
 * Класс для управления уведомлениями приложения.
 * Реализует паттерн Singleton для обеспечения единой точки доступа к уведомлениям.
 */
class NotificationManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NotificationManager"

        // Идентификаторы каналов уведомлений
        const val NOTIFICATION_CHANNEL_ID = "StreamServiceChannel"
        const val ERROR_CHANNEL_ID = "StreamErrorChannel"
        const val PHOTO_CHANNEL_ID = "StreamPhotoChannel"

        // Идентификаторы уведомлений
        const val NOTIFICATION_ID = 3425
        const val ERROR_NOTIFICATION_ID = 3426
        const val PHOTO_NOTIFICATION_ID = 3427

        // Типы действий для уведомлений
        const val ACTION_NONE = 0
        const val ACTION_STOP_ONLY = 1
        const val ACTION_ALL = 2

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: NotificationManager? = null

        fun getInstance(context: Context): NotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    private val handler = Handler(Looper.getMainLooper())

    init {
        createNotificationChannels()
    }

    /**
     * Создает каналы уведомлений для Android 8.0 (API 26) и выше
     */
    private fun createNotificationChannels() {
        // Канал для сервиса стриминга
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.notification_channel_stream_service),
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_stream_service_description)
            setShowBadge(true)
        }

        // Канал для ошибок
        val errorChannel = NotificationChannel(
            ERROR_CHANNEL_ID,
            context.getString(R.string.notification_channel_errors),
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_errors_description)
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        // Канал для уведомлений о фото
        val photoChannel = NotificationChannel(
            PHOTO_CHANNEL_ID,
            context.getString(R.string.notification_channel_photos),
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_photos_description)
            setShowBadge(true)
        }

        // Регистрируем все каналы
        notificationManager.createNotificationChannels(listOf(serviceChannel, errorChannel, photoChannel))
    }

    /**
     * Создает объект уведомления с заданными параметрами
     */
    private fun createNotification(
        text: String,
        ongoing: Boolean,
        actionType: Int = ACTION_ALL,
        isError: Boolean = false,
        isPhoto: Boolean = false
    ): Notification {
        // Создаем Intent для открытия приложения при нажатии на уведомление
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // Настраиваем флаги для PendingIntent
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, flags)

        // Выбираем канал в зависимости от типа уведомления
        val channelId = when {
            isError -> ERROR_CHANNEL_ID
            isPhoto -> PHOTO_CHANNEL_ID
            else -> NOTIFICATION_CHANNEL_ID
        }

        // Строим базовое уведомление
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Настраиваем специфические параметры для ошибок
        if (isError) {
            builder.setCategory(NotificationCompat.CATEGORY_ERROR)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        // Добавляем действия в зависимости от типа
        when (actionType) {
            ACTION_ALL -> {
                // Действие "Стоп"
                val stopIntent = Intent(AppConstants.ACTION_STOP_STREAM).apply {
                    // Явно указываем компонент для Intent
                    setPackage(context.packageName)
                }
                val stopPendingIntent = PendingIntent.getBroadcast(
                    context, 0, stopIntent, 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )

                // Действие "Выход"
                val exitIntent = Intent(AppConstants.ACTION_EXIT_APP).apply {
                    // Явно указываем компонент для Intent
                    setPackage(context.packageName)
                }
                val exitPendingIntent = PendingIntent.getBroadcast(
                    context, 1, exitIntent, 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )

                // Добавляем обе кнопки
                builder.addAction(R.drawable.ic_stop, context.getString(R.string.stop), stopPendingIntent)
                builder.addAction(R.drawable.ic_exit, context.getString(R.string.exit), exitPendingIntent)
            }

            ACTION_STOP_ONLY -> {
                // Только действие "Стоп"
                val stopIntent = Intent(AppConstants.ACTION_STOP_STREAM).apply {
                    // Явно указываем компонент для Intent
                    setPackage(context.packageName)
                }
                val stopPendingIntent = PendingIntent.getBroadcast(
                    context, 0, stopIntent, 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )
                builder.addAction(R.drawable.ic_stop, context.getString(R.string.stop), stopPendingIntent)
            }

            ACTION_NONE -> {
                // Для ошибок добавляем кнопку "ОК" для скрытия уведомления
                if (isError) {
                    val dismissIntent = Intent(AppConstants.ACTION_DISMISS_ERROR).apply {
                        // Явно указываем компонент для Intent
                        setPackage(context.packageName)
                    }
                    val dismissPendingIntent = PendingIntent.getBroadcast(
                        context, 2, dismissIntent, 
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
            }
        }

        return builder.build()
    }

    /**
     * Показывает уведомление о стриминге
     */
    fun showStreamingNotification(text: String, ongoing: Boolean, actionType: Int = ACTION_ALL) {
        Log.d(TAG, "Показ уведомления о стриминге: $text")
        clearAllNotifications()

        handler.postDelayed({
            try {
                val notification = createNotification(text, ongoing, actionType)
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при отображении уведомления о стриминге: ${e.message}", e)
            }
        }, 100)
    }

    /**
     * Показывает уведомление об ошибке
     */
    fun showErrorNotification(text: String, actionType: Int = ACTION_NONE) {
        Log.d(TAG, "Показ уведомления об ошибке: $text")
        clearAllNotifications()

        handler.postDelayed({
            try {
                val notification = createNotification(text, false, actionType, true)
                notificationManager.notify(ERROR_NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при отображении уведомления об ошибке: ${e.message}", e)
            }
        }, 100)
    }

    /**
     * Показывает уведомление о фото
     */
    fun showPhotoNotification(text: String) {
        Log.d(TAG, "Показ уведомления о фото: $text")

        try {
            val notification = createNotification(text, false, ACTION_NONE, isError = false, isPhoto = true)
            notificationManager.notify(PHOTO_NOTIFICATION_ID, notification)

            // Автоматически скрываем уведомление о фото через 3 секунды
            handler.postDelayed({
                clearPhotoNotifications()
            }, 3000)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при отображении уведомления о фото: ${e.message}", e)
        }
    }

    /**
     * Очищает стандартные уведомления
     */
    fun clearStreamingNotifications() {
        try {
            Log.d(TAG, "Очистка уведомлений о стриминге")
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при очистке уведомлений о стриминге: ${e.message}", e)
        }
    }

    /**
     * Очищает уведомления об ошибках
     */
    private fun clearErrorNotifications() {
        try {
            Log.d(TAG, "Очистка уведомлений об ошибках")
            notificationManager.cancel(ERROR_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при очистке уведомлений об ошибках: ${e.message}", e)
        }
    }

    /**
     * Очищает уведомления о фото
     */
    private fun clearPhotoNotifications() {
        try {
            Log.d(TAG, "Очистка уведомлений о фото")
            notificationManager.cancel(PHOTO_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при очистке уведомлений о фото: ${e.message}", e)
        }
    }

    /**
     * Очищает все уведомления
     */
    fun clearAllNotifications() {
        try {
            Log.d(TAG, "Очистка всех уведомлений")
            notificationManager.cancel(NOTIFICATION_ID)
            notificationManager.cancel(ERROR_NOTIFICATION_ID)
            notificationManager.cancel(PHOTO_NOTIFICATION_ID)
            clearStreamingNotifications()
            clearErrorNotifications()
            clearPhotoNotifications()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при очистке всех уведомлений: ${e.message}", e)
        }
    }

}