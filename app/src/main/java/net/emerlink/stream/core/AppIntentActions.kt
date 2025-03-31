package net.emerlink.stream.core

/**
 * Constants used in the application
 */
object AppIntentActions {
    // Intent actions
    const val START_STREAM = "start_stream"
    const val STOP_STREAM = "stop_stream"
    const val EXIT_APP = "exit_app"
    const val AUTH_ERROR = "auth_error"
    const val CONNECTION_FAILED = "connection_failed"
    const val TOOK_PICTURE = "took_picture"
    const val NEW_BITRATE = "new_bitrate"
    const val LOCATION_CHANGE = "location_change"
    const val DISMISS_ERROR = "dismiss_error"

    // Broadcast events
    const val BROADCAST_STREAM_STOPPED = "stream_stopped"
    const val BROADCAST_AUDIO_LEVEL = "audio_level"
    const val BROADCAST_PREVIEW_STATUS = "preview_status"
    const val BROADCAST_NEW_BITRATE = "new_bitrate"

    // Extras for Intent
    const val EXTRA_AUDIO_LEVEL = "audio_level"
    const val EXTRA_PREVIEW_ACTIVE = "preview_active"
    const val EXTRA_NEW_BITRATE = "new_bitrate"
}
