package net.emerlink.stream.service.location

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.util.Log

class StreamLocationListener(private val context: Context) : LocationListener {
    companion object {
        private const val TAG = "StreamLocationListener"
        const val ACTION_LOCATION_CHANGE = "net.emerlink.stream.LOCATION_CHANGE"
    }

    override fun onLocationChanged(location: Location) {
        Log.d(TAG, "Location changed: ${location.latitude}, ${location.longitude}")
        val intent = Intent(ACTION_LOCATION_CHANGE)
        intent.putExtra("latitude", location.latitude)
        intent.putExtra("longitude", location.longitude)
        intent.putExtra("altitude", location.altitude)
        intent.putExtra("speed", location.speed)
        intent.putExtra("bearing", location.bearing)
        intent.putExtra("accuracy", location.accuracy)
        context.sendBroadcast(intent)
    }

    // Необходимые методы для совместимости со всеми версиями Android
    override fun onProviderDisabled(provider: String) {
        Log.d(TAG, "Provider disabled: $provider")
    }

    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "Provider enabled: $provider")
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
        Log.d(TAG, "Provider status changed: $provider, status: $status")
    }
}
