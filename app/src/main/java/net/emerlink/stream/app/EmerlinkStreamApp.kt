package net.emerlink.stream.app

import android.app.Application
import net.emerlink.stream.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class EmerlinkStreamApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Koin
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@EmerlinkStreamApp)
            modules(appModule)
        }
    }
}
