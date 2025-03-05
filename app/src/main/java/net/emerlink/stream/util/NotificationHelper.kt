package net.emerlink.stream.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import net.emerlink.stream.MainActivity
import net.emerlink.stream.R
import net.emerlink.stream.service.StreamService

class NotificationHelper(private val context: Context) {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "StreamServiceChannel"
        const val NOTIFICATION_ID = 3425
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Stream Service",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
    }

    fun createNotification(text: String, ongoing: Boolean): Notification {
        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, StreamService::class.java)
        stopIntent.action = StreamService.ACTION_STOP_STREAM
        val stopPendingIntent = PendingIntent.getService(
            context, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val exitIntent = Intent(context, StreamService::class.java)
        exitIntent.action = StreamService.ACTION_EXIT_APP
        val exitPendingIntent = PendingIntent.getService(
            context, 2, exitIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .addAction(R.drawable.ic_stop, context.getString(R.string.stop), stopPendingIntent)
            .addAction(R.drawable.ic_exit, context.getString(R.string.exit), exitPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        return notification
    }

    fun updateNotification(text: String, ongoing: Boolean) {
        val notification = createNotification(text, ongoing)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
} 