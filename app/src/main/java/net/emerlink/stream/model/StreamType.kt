package net.emerlink.stream.model

import net.emerlink.stream.model.StreamType.entries


enum class StreamType(
    val defaultPort: String, val supportAuth: Boolean, val supportsStreamKey: Boolean = false
) {
    RTMP("1935", supportAuth = true, supportsStreamKey = true),
    RTMPs("1936", supportAuth = true, supportsStreamKey = true),
    RTSP("554", supportAuth = true),
    RTSPs("322", supportAuth = true),
    SRT("9710", supportAuth = false),
    UDP("5600", supportAuth = false);

    companion object {
        fun fromString(value: String): StreamType {
            return entries.find { it.toString() == value }
                ?: throw IllegalArgumentException("Invalid stream type: $value")
        }
    }

    override fun toString(): String = name.lowercase()
} 
