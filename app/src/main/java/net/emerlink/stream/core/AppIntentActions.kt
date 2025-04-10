package net.emerlink.stream.core

/**
 * Constants used in the application
 */
object AppIntentActions {
    // Intent actions
    const val START_STREAM = "net.emerlink.stream.action.START_STREAM"
    const val STOP_STREAM = "net.emerlink.stream.action.STOP_STREAM"
    const val EXIT_APP = "net.emerlink.stream.action.EXIT_APP"
    const val FINISH_ACTIVITY = "net.emerlink.stream.action.FINISH_ACTIVITY"
    const val NEW_BITRATE = "net.emerlink.stream.action.NEW_BITRATE"

    // Broadcast events
    const val BROADCAST_AUDIO_LEVEL = "net.emerlink.stream.broadcast.AUDIO_LEVEL"
    const val BROADCAST_STREAM_STARTED = "net.emerlink.stream.broadcast.STREAM_STARTED"
    const val BROADCAST_STREAM_STOPPED = "net.emerlink.stream.broadcast.STREAM_STOPPED"
    const val BROADCAST_RECORD_STARTED = "net.emerlink.stream.broadcast.RECORD_STARTED"
    const val BROADCAST_RECORD_STOPPED = "net.emerlink.stream.broadcast.RECORD_STOPPED"

    // Extras for Intent
    const val EXTRA_AUDIO_LEVEL = "net.emerlink.stream.EXTRA_AUDIO_LEVEL"
    const val EXTRA_NEW_BITRATE = "net.emerlink.stream.EXTRA_NEW_BITRATE"
}
