package net.emerlink.stream.presentation.settings.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.emerlink.stream.data.model.ConnectionProfile
import net.emerlink.stream.data.repository.ConnectionProfileRepository
import org.koin.java.KoinJavaComponent.inject

/**
 * ViewModel for managing connection profiles
 */
class ConnectionProfilesViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository: ConnectionProfileRepository by inject(ConnectionProfileRepository::class.java)

    // StateFlow of all profiles
    val profiles: StateFlow<List<ConnectionProfile>> =
        repository.profilesFlow
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                emptyList()
            )

    // StateFlow of the active profile
    val activeProfile: StateFlow<ConnectionProfile?> =
        repository.activeProfileFlow
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null
            )

    /**
     * Refresh profiles data
     */
    fun refreshProfiles() {
        repository.refreshProfiles()
    }

    /**
     * Set a profile as active
     */
    fun setActiveProfile(profileId: String) {
        repository.setActiveProfile(profileId)
    }

    /**
     * Delete a profile
     */
    fun deleteProfile(profileId: String) {
        repository.deleteProfile(profileId)
    }

    /**
     * Save a profile
     */
    fun saveProfile(profile: ConnectionProfile) {
        repository.saveProfile(profile)
    }

    /**
     * Get a profile by ID
     */
    fun getProfile(profileId: String): ConnectionProfile? = repository.getProfile(profileId)

    /**
     * Get the active profile
     */
    fun getActiveProfile(): ConnectionProfile? = repository.getActiveProfile()
}
