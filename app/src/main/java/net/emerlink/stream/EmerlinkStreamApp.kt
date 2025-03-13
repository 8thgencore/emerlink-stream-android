package net.emerlink.stream

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import net.emerlink.stream.di.appModule
import net.emerlink.stream.service.StreamService
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class EmerlinkStreamApp : Application() {
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                Log.d(TAG, "Service connected")
                val binder = service as StreamService.LocalBinder
                streamService = binder.getService()
                isServiceBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "Service disconnected")
                streamService = null
                isServiceBound = false
            }
        }

    override fun onCreate() {
        super.onCreate()

        // Initialize Koin
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@EmerlinkStreamApp)
            modules(appModule)
        }

        // Привязываем сервис при запуске приложения
        bindStreamService()

        Log.d(TAG, "Application started")
    }

    private fun bindStreamService() {
        val intent = Intent(this, StreamService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onTerminate() {
        if (isServiceBound) {
            try {
                unbindService(serviceConnection)
                isServiceBound = false
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при отключении сервиса: ${e.message}")
            }
        }
        super.onTerminate()
    }

    companion object {
        private const val TAG = "EmerlinkStreamApp"

        // Переменные для доступа к сервису
        private var streamService: StreamService? = null
        private var isServiceBound = false

        fun getStreamService(): StreamService? = streamService
    }
}
