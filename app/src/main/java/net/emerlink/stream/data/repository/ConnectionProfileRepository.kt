package net.emerlink.stream.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.emerlink.stream.data.model.ConnectionProfile
import net.emerlink.stream.data.model.ConnectionSettings
import net.emerlink.stream.data.model.StreamType
import java.util.*

/**
 * Repository for managing connection profiles
 */
class ConnectionProfileRepository(
    context: Context,
) {
    companion object {
        private const val PREFS_NAME = "connection_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // StateFlow to observe profile changes
    private val _profilesFlow = MutableStateFlow<List<ConnectionProfile>>(emptyList())
    val profilesFlow: StateFlow<List<ConnectionProfile>> = _profilesFlow.asStateFlow()

    // StateFlow for the active profile
    private val _activeProfileFlow = MutableStateFlow<ConnectionProfile?>(null)
    val activeProfileFlow: StateFlow<ConnectionProfile?> = _activeProfileFlow.asStateFlow()

    init {
        loadProfiles()
    }

    /**
     * Load all profiles from SharedPreferences
     */
    private fun loadProfiles() {
        val json = prefs.getString(KEY_PROFILES, null)
        val profiles =
            if (json != null) {
                val type = object : TypeToken<List<ConnectionProfile>>() {}.type
                gson.fromJson<List<ConnectionProfile>>(json, type)
            } else {
                // Create a default profile if none exist
                listOf(createDefaultProfile())
            }

        _profilesFlow.value = profiles

        // Load active profile
        val activeProfileId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
        _activeProfileFlow.value = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()

        // If no active profile is set, set the first one as active
        if (_activeProfileFlow.value == null && profiles.isNotEmpty()) {
            setActiveProfile(profiles.first().id)
        }
    }

    /**
     * Save all profiles to SharedPreferences
     */
    private fun saveProfiles() {
        val json = gson.toJson(_profilesFlow.value)
        prefs.edit(commit = true) {
            putString(KEY_PROFILES, json)
        }
    }

    /**
     * Create a default connection profile
     */
    private fun createDefaultProfile(): ConnectionProfile =
        ConnectionProfile(
            id = UUID.randomUUID().toString(),
            name = "Default",
            settings =
                ConnectionSettings(
                    protocol = StreamType.RTMP,
                    address = "127.0.0.1",
                    port = 1935,
                    path = "live"
                )
        )

    /**
     * Add a new profile or update an existing one
     */
    fun saveProfile(profile: ConnectionProfile) {
        val currentProfiles = _profilesFlow.value.toMutableList()

        // Find and replace existing profile or add new one
        val index = currentProfiles.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            currentProfiles[index] = profile
        } else {
            currentProfiles.add(profile)
        }

        _profilesFlow.value = currentProfiles
        saveProfiles()

        if (currentProfiles.size == 1) {
            setActiveProfile(profile.id)
        }
    }

    /**
     * Delete a profile by ID
     */
    fun deleteProfile(profileId: String) {
        val currentProfiles = _profilesFlow.value.toMutableList()

        // Don't delete if it's the only profile
        if (currentProfiles.size <= 1) {
            return
        }

        currentProfiles.removeIf { it.id == profileId }
        _profilesFlow.value = currentProfiles
        saveProfiles()

        // If we deleted the active profile, switch to the first available
        if (_activeProfileFlow.value?.id == profileId) {
            val newActiveProfile = currentProfiles.firstOrNull()
            newActiveProfile?.let { setActiveProfile(it.id) }
        }
    }

    /**
     * Set a profile as the active one
     */
    fun setActiveProfile(profileId: String) {
        val profile = _profilesFlow.value.find { it.id == profileId }
        if (profile != null) {
            _activeProfileFlow.value = profile
            prefs.edit(commit = true) {
                putString(KEY_ACTIVE_PROFILE_ID, profileId)
            }
        }
    }

    /**
     * Get a profile by ID
     */
    fun getProfile(profileId: String): ConnectionProfile? = _profilesFlow.value.find { it.id == profileId }

    /**
     * Get the active profile
     */
    fun getActiveProfile(): ConnectionProfile? = _activeProfileFlow.value

    /**
     * Refresh profiles from SharedPreferences
     */
    fun refreshProfiles() {
        loadProfiles()
    }
}
