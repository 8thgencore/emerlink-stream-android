package net.emerlink.stream.data.model

import net.emerlink.stream.data.model.StreamType.entries

/**
 * Enum for different stream protocols
 */
enum class StreamType {
    RTMP {
        override val defaultPort: String = "1935"
        override val supportsStreamKey: Boolean = true
        override val supportAuth: Boolean = true
    },
    RTMPs {
        override val defaultPort: String = "443"
        override val supportsStreamKey: Boolean = true
        override val supportAuth: Boolean = true
    },
    RTSP {
        override val defaultPort: String = "554"
        override val supportsStreamKey: Boolean = false
        override val supportAuth: Boolean = true
    },
    RTSPs {
        override val defaultPort: String = "322"
        override val supportsStreamKey: Boolean = false
        override val supportAuth: Boolean = true
    },
    SRT {
        override val defaultPort: String = "9710"
        override val supportsStreamKey: Boolean = false
        override val supportAuth: Boolean = true
    },
    UDP {
        override val defaultPort: String = "5000"
        override val supportsStreamKey: Boolean = false
        override val supportAuth: Boolean = false
    }, ;

    abstract val defaultPort: String
    abstract val supportsStreamKey: Boolean
    abstract val supportAuth: Boolean

    companion object {
        /**
         * Convert string to StreamType
         */
        fun fromString(value: String): StreamType = entries.find { it.name.equals(value, ignoreCase = true) } ?: RTMP
    }

    override fun toString(): String = name.lowercase()
}
