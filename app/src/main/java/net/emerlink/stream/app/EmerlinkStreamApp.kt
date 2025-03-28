package net.emerlink.stream.app

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.util.Log
import net.emerlink.stream.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class EmerlinkStreamApp : Application() {
    companion object {
        private const val TAG = "EmerlinkStreamApp"
    }
    
    override fun onCreate() {
        super.onCreate()

        // Enable StrictMode for development
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy.Builder()
                        .detectAll()
                        .penaltyLog()
                        .build()
                )
                
                StrictMode.setVmPolicy(
                    StrictMode.VmPolicy.Builder()
                        .detectLeakedSqlLiteObjects()
                        .detectLeakedClosableObjects()
                        .penaltyLog()
                        .build()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error setting StrictMode policy", e)
            }
        }

        // Initialize Koin
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@EmerlinkStreamApp)
            modules(appModule)
        }
    }
}
