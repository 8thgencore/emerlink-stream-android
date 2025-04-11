package net.emerlink.stream.data.model

/**
 * Class for storing stream connection settings
 */
data class ConnectionSettings(
    var protocol: StreamType = StreamType.RTMP,
    var address: String = "",
    var port: Int = 0,
    var path: String = "",
    var streamKey: String = "",
    var tcp: Boolean = false,
    var username: String = "",
    var password: String = "",
    var streamSelfSignedCert: Boolean = false,
    var certFile: String? = null,
    var certPassword: String = "",
    // SRT specific parameters
    var srtMode: String = "caller",
    var srtLatency: Int = 2000,
    var srtOverheadBw: Int = 25,
    var srtPassphrase: String = "",
) {
    /**
     * Builds a stream URL based on the connection settings
     */
    fun buildStreamUrl(): String {
        // Check if address is provided
        val formattedAddress = address.ifEmpty { return "" }

        // Format port only if it's not empty
        val formattedPort = if (port != 0) ":$port" else ""

        // Fields that can be empty:
        // - path: Use empty string if null or empty
        val safePath = path.ifEmpty { "" }

        // - streamKey: Only use if not null or empty
        // - username/password: Only use if both are provided

        return when (protocol) {
            StreamType.RTMP, StreamType.RTMPs -> {
                val auth =
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        "$username:$password@"
                    } else {
                        ""
                    }

                // Append stream key to path if provided
                val fullPath =
                    if (streamKey.isNotEmpty()) {
                        if (safePath.endsWith("/")) "$safePath$streamKey" else "$safePath/$streamKey"
                    } else {
                        safePath
                    }

                // Handle empty path
                val pathSegment = if (fullPath.isNotEmpty()) "/$fullPath" else ""

                "${protocol.toString().lowercase()}://$auth$formattedAddress$formattedPort$pathSegment"
            }

            StreamType.RTSP, StreamType.RTSPs -> {
                val auth =
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        "$username:$password@"
                    } else {
                        ""
                    }

                // Handle empty path
                val pathSegment = if (safePath.isNotEmpty()) "/$safePath" else ""

                "${protocol.toString().lowercase()}://$auth$formattedAddress$formattedPort$pathSegment"
            }

            StreamType.SRT -> {
                // Build SRT parameters
                val srtParams = StringBuilder()

                // Add streamid parameter (format depends on authentication)
                val streamId =
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        "publish:$safePath:$username:$password"
                    } else {
                        "publish:$safePath"
                    }
                srtParams.append("streamid=$streamId")

                // Add mode parameter
                srtParams.append("&mode=$srtMode")

                // Add latency parameter
                srtParams.append("&latency=$srtLatency")

                // Add overhead bandwidth parameter
                srtParams.append("&oheadbw=$srtOverheadBw")

                // Add passphrase if it's not empty
                if (srtPassphrase.isNotEmpty()) {
                    srtParams.append("&passphrase=$srtPassphrase")
                }

                "srt://$formattedAddress$formattedPort?$srtParams"
            }

            StreamType.UDP -> "udp://$formattedAddress$formattedPort"
        }
    }
}
