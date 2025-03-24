package net.emerlink.stream.core

/**
 * Константы, используемые в приложении
 */
object AppIntentActions {
    // Действия для Intent
    const val ACTION_START_STREAM = "net.emerlink.stream.START_STREAM"
    const val ACTION_STOP_STREAM = "net.emerlink.stream.STOP_STREAM"
    const val ACTION_EXIT_APP = "net.emerlink.stream.EXIT_APP"
    const val ACTION_AUTH_ERROR = "net.emerlink.stream.AUTH_ERROR"
    const val ACTION_CONNECTION_FAILED = "net.emerlink.stream.CONNECTION_FAILED"
    const val ACTION_TOOK_PICTURE = "net.emerlink.stream.TOOK_PICTURE"
    const val ACTION_NEW_BITRATE = "net.emerlink.stream.NEW_BITRATE"
    const val ACTION_LOCATION_CHANGE = "net.emerlink.stream.LOCATION_CHANGE"
    const val ACTION_DISMISS_ERROR = "net.emerlink.stream.DISMISS_ERROR"

    // Broadcast события
    const val BROADCAST_STREAM_STOPPED = "net.emerlink.stream.STREAM_STOPPED"
    const val BROADCAST_AUDIO_LEVEL = "net.emerlink.stream.AUDIO_LEVEL"
    
    // Extras для Intent
    const val EXTRA_AUDIO_LEVEL = "audio_level"
}
