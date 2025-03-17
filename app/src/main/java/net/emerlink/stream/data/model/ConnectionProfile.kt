package net.emerlink.stream.data.model

/**
 * Class for storing a named connection profile
 */
data class ConnectionProfile(
    val id: String, // Unique identifier for the profile
    val name: String, // User-friendly name for the profile
    val settings: ConnectionSettings, // The actual connection settings
    val isDefault: Boolean = false, // Whether this is the default profile
)
