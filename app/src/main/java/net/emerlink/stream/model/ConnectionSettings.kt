package net.emerlink.stream.model

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
    var certPassword: String = ""
) {
    /**
     * Builds a stream URL based on the connection settings
     */
    fun buildStreamUrl(): String {
        // Check if address is provided
        val formattedAddress = if (address.isNotEmpty()) address else return ""

        // Format port only if it's not empty
        val formattedPort = if (port != 0) ":$port" else ""

        // Fields that can be empty:
        // - path: Use empty string if null or empty
        val safePath = path.ifEmpty { "" }

        // - streamKey: Only use if not null or empty
        // - username/password: Only use if both are provided

        return when (protocol) {
            StreamType.RTMP, StreamType.RTMPs -> {
                val auth = if (username.isNotEmpty() && password.isNotEmpty()) {
                    "$username:$password@"
                } else ""

                // Append stream key to path if provided
                val fullPath = if (streamKey.isNotEmpty()) {
                    if (safePath.endsWith("/")) "$safePath$streamKey" else "$safePath/$streamKey"
                } else {
                    safePath
                }

                // Handle empty path
                val pathSegment = if (fullPath.isNotEmpty()) "/$fullPath" else ""

                "${protocol.toString().lowercase()}://$auth$formattedAddress$formattedPort$pathSegment"
            }

            StreamType.RTSP, StreamType.RTSPs -> {
                val auth = if (username.isNotEmpty() && password.isNotEmpty()) {
                    "$username:$password@"
                } else ""

                // Handle empty path
                val pathSegment = if (safePath.isNotEmpty()) "/$safePath" else ""

                "${protocol.toString().lowercase()}://$auth$formattedAddress$formattedPort$pathSegment"
            }

            StreamType.SRT -> {
                // Format the streamid according to the required format
                val streamId = if (username.isNotEmpty() && password.isNotEmpty()) {
                    "publish:$safePath:$username:$password"
                } else {
                    "publish:$safePath"
                }

                val srtParams = StringBuilder()
                srtParams.append("streamid=$streamId")
                srtParams.append("&latency=2000")

                "srt://$formattedAddress$formattedPort?$srtParams"
            }

            StreamType.UDP -> "udp://$formattedAddress$formattedPort"
        }
    }
} 