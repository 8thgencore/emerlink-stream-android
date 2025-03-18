@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:function-naming")

package net.emerlink.stream.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import net.emerlink.stream.R
import net.emerlink.stream.data.model.ConnectionProfile
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.presentation.settings.components.PreferenceCategory
import net.emerlink.stream.presentation.settings.components.SwitchPreference
import net.emerlink.stream.presentation.settings.viewmodel.ConnectionProfilesViewModel
import net.emerlink.stream.presentation.settings.viewmodel.SettingsViewModel

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsScreen(
    onBackClick: () -> Unit,
    onEditProfile: (String) -> Unit,
    onCreateProfile: () -> Unit,
    connectionProfilesViewModel: ConnectionProfilesViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    LaunchedEffect(Unit) {
        connectionProfilesViewModel.refreshProfiles()
    }

    // Профили соединений
    val profiles by connectionProfilesViewModel.profiles.collectAsState()
    val activeProfile by connectionProfilesViewModel.activeProfile.collectAsState()

    // Video settings from SettingsViewModel
    val videoSettings by settingsViewModel.videoSettings.collectAsState()

    // Состояние для диалога удаления
    var showDeleteDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<ConnectionProfile?>(null) }

    // Stream Settings states
    var streamVideo by remember { mutableStateOf(videoSettings.streamVideo) }

    // Update UI when active profile changes
    LaunchedEffect(activeProfile) {
        activeProfile?.let { profile ->
            // Update preferences with active profile settings
            preferences.edit {
                putString(PreferenceKeys.STREAM_PROTOCOL, profile.settings.protocol.toString())
                putString(PreferenceKeys.STREAM_ADDRESS, profile.settings.address)
                putString(PreferenceKeys.STREAM_PORT, profile.settings.port.toString())
                putString(PreferenceKeys.STREAM_PATH, profile.settings.path)
                putString(PreferenceKeys.STREAM_KEY, profile.settings.streamKey)
                putBoolean(PreferenceKeys.STREAM_USE_TCP, profile.settings.tcp)
                putString(PreferenceKeys.STREAM_USERNAME, profile.settings.username)
                putString(PreferenceKeys.STREAM_PASSWORD, profile.settings.password)
                putBoolean(PreferenceKeys.STREAM_SELF_SIGNED_CERT, profile.settings.streamSelfSignedCert)
                profile.settings.certFile?.let { putString(PreferenceKeys.STREAM_CERTIFICATE, it) }
                putString(PreferenceKeys.STREAM_CERTIFICATE_PASSWORD, profile.settings.certPassword)
            }
        }
    }

    // Update local state when videoSettings changes
    LaunchedEffect(videoSettings) {
        streamVideo = videoSettings.streamVideo
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.connection_settings)) }, navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.back)
                    )
                }
            }, actions = {
                IconButton(onClick = onCreateProfile) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(id = R.string.add_profile)
                    )
                }
            })
        }
    ) { paddingValues ->
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            if (profiles.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(id = R.string.no_profiles),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onCreateProfile) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(id = R.string.add_profile))
                        }
                    }
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                ) {
                    // Stream Settings
                    PreferenceCategory(title = stringResource(id = R.string.stream_settings)) {
                        SwitchPreference(
                            title = stringResource(id = R.string.enable_streaming),
                            summary = stringResource(id = R.string.enable_streaming_summary),
                            checked = streamVideo,
                            onCheckedChange = { checked ->
                                streamVideo = checked
                                settingsViewModel.updateStreamVideo(checked)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Profiles section - use weight(1f) to make it take all available space
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        PreferenceCategory(title = stringResource(id = R.string.connection_profiles)) {
                            // Show active profile info
                            activeProfile?.let { profile ->
                                Text(
                                    text = stringResource(id = R.string.active_profile, profile.name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // List of profiles - will fill remaining space
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(profiles) { profile ->
                                    val isActive = profile.id == activeProfile?.id

                                    ConnectionProfileItem(
                                        profile = profile,
                                        isActive = isActive,
                                        onProfileClick = { connectionProfilesViewModel.setActiveProfile(profile.id) },
                                        onEditClick = { onEditProfile(profile.id) },
                                        onDeleteClick = {
                                            profileToDelete = profile
                                            showDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Delete confirmation dialog
            if (showDeleteDialog && profileToDelete != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text(text = stringResource(id = R.string.delete_profile)) },
                    text = {
                        Text(
                            text =
                                stringResource(
                                    id = R.string.delete_profile_confirmation,
                                    profileToDelete?.name ?: ""
                                )
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                profileToDelete?.id?.let {
                                    connectionProfilesViewModel.deleteProfile(it)
                                }
                                showDeleteDialog = false
                                profileToDelete = null
                            }
                        ) {
                            Text(text = stringResource(id = R.string.delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text(text = stringResource(id = R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ConnectionProfileItem(
    profile: ConnectionProfile,
    isActive: Boolean,
    onProfileClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val backgroundColor =
        if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    val textColor =
        if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onProfileClick),
        colors =
            CardDefaults.cardColors(
                containerColor = backgroundColor
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile icon and active indicator
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )

                if (isActive) {
                    Box(
                        modifier =
                            Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .align(Alignment.BottomEnd)
                    )
                }
            }

            // Profile details
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = textColor
                )

                Text(
                    text = "${
                        profile.settings.protocol.toString().lowercase()
                    }://${profile.settings.address}:${profile.settings.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (profile.isDefault) {
                    Text(
                        text = stringResource(id = R.string.default_profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Actions
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(id = R.string.edit_profile),
                        tint = textColor
                    )
                }

                // Only show delete button if it's not the only profile
                if (!profile.isDefault) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.delete_profile),
                            tint = textColor
                        )
                    }
                }
            }
        }
    }
}
