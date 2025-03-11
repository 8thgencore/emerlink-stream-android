package net.emerlink.stream.model

enum class StreamType(
    val requiresAuth: Boolean, val defaultPort: String
) {
    RTMP(true, "1935"),
    RTMPs(true, "1936"),
    RTSP(true, "554"),
    RTSPs(true, "322"),
    SRT(false, "9710"),
    UDP(false, "5600");

    companion object {
        fun fromString(value: String): StreamType {
            return entries.find { it.toString() == value }
                ?: throw IllegalArgumentException("Invalid stream type: $value")
        }
    }

    fun buildStreamUrl(
        address: String, port: String, path: String, username: String? = null, password: String? = null
    ): String {
        return when (this) {
            RTMP, RTMPs -> {
                val auth = if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                    "$username:$password@"
                } else ""
                "${toString().lowercase()}://$auth$address:$port/$path"
            }

            RTSP, RTSPs -> {
                val auth = if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                    "$username:$password@"
                } else ""
                "${toString().lowercase()}://$auth$address:$port/$path"
            }

            SRT -> {
                // Format the streamid according to the required format: action:pathname[:query] or
                // action:pathname:user:pass[:query]
                val streamId = if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                    // Format with credentials: action:pathname:user:pass[:query]
                    "publish:$path:$username:$password"
                } else {
                    // Format without credentials: action:pathname[:query]
                    "publish:$path"
                }

                val srtParams = StringBuilder()
                srtParams.append("streamid=$streamId")
                srtParams.append("&latency=2000")

                "srt://$address:$port?$srtParams"
            }

            UDP -> "udp://$address:$port"
        }
    }

    override fun toString(): String = name.lowercase()


} 
