package net.emerlink.stream

import android.app.Application
import android.util.Log
import net.emerlink.stream.di.appModule
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
        
        Log.d(TAG, "Application started")
    }
    
    companion object {
        private const val TAG = "EmerlinkStreamApp"
    }
} 