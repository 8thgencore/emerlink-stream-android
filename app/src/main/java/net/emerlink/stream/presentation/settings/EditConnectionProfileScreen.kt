@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:function-naming")

package net.emerlink.stream.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.emerlink.stream.R
import net.emerlink.stream.data.model.ConnectionProfile
import net.emerlink.stream.data.model.ConnectionSettings
import net.emerlink.stream.data.model.StreamType
import net.emerlink.stream.presentation.settings.components.DropdownPreference
import net.emerlink.stream.presentation.settings.components.InputPreference
import net.emerlink.stream.presentation.settings.components.PreferenceCategory
import net.emerlink.stream.presentation.settings.components.SwitchPreference
import net.emerlink.stream.presentation.settings.viewmodel.ConnectionProfilesViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditConnectionProfileScreen(
    profileId: String?,
    onBackClick: () -> Unit,
    viewModel: ConnectionProfilesViewModel = viewModel(),
) {
    val isNewProfile = profileId == null
    val title =
        stringResource(
            id = if (isNewProfile) R.string.create_profile else R.string.edit_profile
        )

    // Load existing profile or create a new one
    val existingProfile =
        if (!isNewProfile) {
            profileId.let { viewModel.getProfile(it) }
        } else {
            null
        }

    // State for profile fields
    var profileName by remember { mutableStateOf(existingProfile?.name ?: "") }

    // Connection settings states
    var streamProtocol by remember {
        mutableStateOf(existingProfile?.settings?.protocol?.toString() ?: StreamType.RTMP.toString())
    }
    var streamAddress by remember {
        mutableStateOf(existingProfile?.settings?.address ?: "127.0.0.1")
    }
    var streamPort by remember {
        mutableStateOf(existingProfile?.settings?.port?.toString() ?: "1935")
    }
    var streamPath by remember {
        mutableStateOf(existingProfile?.settings?.path ?: "live")
    }
    var streamKey by remember {
        mutableStateOf(existingProfile?.settings?.streamKey ?: "")
    }
    var streamUseTcp by remember {
        mutableStateOf(existingProfile?.settings?.tcp != false)
    }
    var streamUsername by remember {
        mutableStateOf(existingProfile?.settings?.username ?: "")
    }
    var streamPassword by remember {
        mutableStateOf(existingProfile?.settings?.password ?: "")
    }
    var streamSelfSignedCert by remember {
        mutableStateOf(existingProfile?.settings?.streamSelfSignedCert == true)
    }
    var streamCertificate by remember {
        mutableStateOf(existingProfile?.settings?.certFile ?: "")
    }
    var streamCertificatePassword by remember {
        mutableStateOf(existingProfile?.settings?.certPassword ?: "")
    }

    // SRT specific settings
    var srtMode by remember {
        mutableStateOf(existingProfile?.settings?.srtMode ?: "caller")
    }
    var srtLatency by remember {
        mutableStateOf(existingProfile?.settings?.srtLatency?.toString() ?: "2000")
    }
    var srtOverheadBw by remember {
        mutableStateOf(existingProfile?.settings?.srtOverheadBw?.toString() ?: "25")
    }
    var srtPassphrase by remember {
        mutableStateOf(existingProfile?.settings?.srtPassphrase ?: "")
    }

    // Get current stream type
    val currentStreamType =
        StreamType.entries.find {
            it.toString() == streamProtocol
        } ?: StreamType.RTMP

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                actions = {
                    // Save button
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                // Create ConnectionSettings object
                                val settings =
                                    ConnectionSettings(
                                        protocol = StreamType.fromString(streamProtocol),
                                        address = streamAddress,
                                        port = streamPort.toIntOrNull() ?: 0,
                                        path = streamPath,
                                        streamKey = streamKey,
                                        tcp = streamUseTcp,
                                        username = streamUsername,
                                        password = streamPassword,
                                        streamSelfSignedCert = streamSelfSignedCert,
                                        certFile = streamCertificate.takeIf { it.isNotEmpty() },
                                        certPassword = streamCertificatePassword,
                                        srtMode = srtMode,
                                        srtLatency = srtLatency.toIntOrNull() ?: 2000,
                                        srtOverheadBw = srtOverheadBw.toIntOrNull() ?: 25,
                                        srtPassphrase = srtPassphrase
                                    )

                                // Create or update profile
                                val newProfileId = existingProfile?.id ?: UUID.randomUUID().toString()
                                val profile =
                                    ConnectionProfile(
                                        id = newProfileId,
                                        name = profileName.takeIf { it.isNotEmpty() } ?: "Unnamed Profile",
                                        settings = settings
                                    )

                                // Save profile
                                viewModel.saveProfile(profile)

                                // Set as active profile
                                viewModel.setActiveProfile(newProfileId)

                                onBackClick()
                            }
                        },
                        enabled = profileName.isNotEmpty() && streamAddress.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(id = R.string.save)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
            ) {
                // Profile details
                PreferenceCategory(title = stringResource(id = R.string.profile_details)) {
                    // Profile name
                    InputPreference(
                        title = stringResource(id = R.string.profile_name),
                        summary = stringResource(id = R.string.profile_name_summary),
                        value = profileName,
                        onValueChange = { profileName = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Connection Settings
                PreferenceCategory(title = stringResource(id = R.string.connection_settings)) {
                    DropdownPreference(
                        title = stringResource(id = R.string.stream_protocol),
                        summary = stringResource(id = R.string.stream_protocol_summary),
                        selectedValue = streamProtocol,
                        options = StreamType.entries.filter { it != StreamType.UDP }.map { it.toString() },
                        onValueSelected = { value ->
                            streamProtocol = value
                            // Reset port to default for new protocol
                            val newType = StreamType.entries.find { it.toString() == value }
                            if (newType != null) {
                                streamPort = newType.defaultPort
                            }
                        }
                    )

                    InputPreference(
                        title = stringResource(id = R.string.stream_address),
                        summary = stringResource(id = R.string.stream_address_summary),
                        value = streamAddress,
                        onValueChange = { value ->
                            val filteredValue = value.filterNot { it.isWhitespace() }
                            streamAddress = filteredValue
                        },
                        keyboardType = KeyboardType.Text
                    )

                    InputPreference(
                        title = stringResource(id = R.string.stream_port),
                        summary = stringResource(id = R.string.stream_port_summary),
                        value = streamPort,
                        onValueChange = { value ->
                            val filteredValue = value.filterNot { it.isWhitespace() }
                            streamPort = filteredValue
                        },
                        keyboardType = KeyboardType.Number
                    )

                    InputPreference(
                        title = stringResource(id = R.string.stream_path),
                        summary = stringResource(id = R.string.stream_path_summary),
                        value = streamPath,
                        onValueChange = { value ->
                            val filteredValue = value.filterNot { it.isWhitespace() }
                            streamPath = filteredValue
                        }
                    )

                    // Show stream key field for protocols that support it (like RTMP)
                    if (currentStreamType.supportsStreamKey) {
                        InputPreference(
                            title = stringResource(id = R.string.stream_key),
                            summary = stringResource(id = R.string.stream_key_summary),
                            value = streamKey,
                            onValueChange = { value ->
                                val filteredValue = value.filterNot { it.isWhitespace() }
                                streamKey = filteredValue
                            }
                        )
                    }

                    // Show TCP switch only for RTSP/RTSPS
                    if (currentStreamType in listOf(StreamType.RTSP, StreamType.RTSPs)) {
                        SwitchPreference(
                            title = stringResource(id = R.string.use_tcp),
                            summary = stringResource(id = R.string.use_tcp_summary),
                            checked = streamUseTcp,
                            onCheckedChange = { checked ->
                                streamUseTcp = checked
                            }
                        )
                    }

                    // Show authentication fields only for protocols that support it
                    if (currentStreamType.supportAuth) {
                        InputPreference(
                            title = stringResource(id = R.string.username),
                            summary = stringResource(id = R.string.username_summary),
                            value = streamUsername,
                            onValueChange = { value ->
                                val filteredValue = value.filterNot { it.isWhitespace() }
                                streamUsername = filteredValue
                            }
                        )

                        InputPreference(
                            title = stringResource(id = R.string.password),
                            summary = stringResource(id = R.string.password_summary),
                            value = streamPassword,
                            isPassword = true,
                            onValueChange = { value ->
                                val filteredValue = value.filterNot { it.isWhitespace() }
                                streamPassword = filteredValue
                            }
                        )
                    }

                    // Show certificate settings only for secure protocols
                    if (currentStreamType in listOf(StreamType.RTMPs, StreamType.RTSPs)) {
                        SwitchPreference(
                            title = stringResource(id = R.string.self_signed_certificate),
                            summary = stringResource(id = R.string.self_signed_certificate_summary),
                            checked = streamSelfSignedCert,
                            onCheckedChange = { checked ->
                                streamSelfSignedCert = checked
                            }
                        )

                        if (streamSelfSignedCert) {
                            InputPreference(
                                title = stringResource(id = R.string.certificate),
                                summary = stringResource(id = R.string.certificate_summary),
                                value = streamCertificate,
                                onValueChange = { value ->
                                    streamCertificate = value
                                }
                            )

                            InputPreference(
                                title = stringResource(id = R.string.certificate_password),
                                summary = stringResource(id = R.string.certificate_password_summary),
                                value = streamCertificatePassword,
                                isPassword = true,
                                onValueChange = { value ->
                                    streamCertificatePassword = value
                                }
                            )
                        }
                    }
                }
                // SRT specific settings
                PreferenceCategory(title = stringResource(id = R.string.srt_settings)) {
                    if (currentStreamType == StreamType.SRT) {
                        // SRT Mode
                        DropdownPreference(
                            title = stringResource(id = R.string.srt_mode),
                            summary = stringResource(id = R.string.srt_mode_summary),
                            selectedValue = srtMode,
                            options = listOf("caller", "listener", "rendezvous"),
                            onValueSelected = { value ->
                                srtMode = value
                            }
                        )

                        // SRT Latency
                        InputPreference(
                            title = stringResource(id = R.string.srt_latency),
                            summary = stringResource(id = R.string.srt_latency_summary),
                            value = srtLatency,
                            onValueChange = { value ->
                                val filteredValue = value.filterNot { it.isWhitespace() }
                                srtLatency = filteredValue
                            },
                            keyboardType = KeyboardType.Number
                        )

                        // SRT Overhead Bandwidth
                        InputPreference(
                            title = stringResource(id = R.string.srt_overhead_bandwidth),
                            summary = stringResource(id = R.string.srt_overhead_bandwidth_summary),
                            value = srtOverheadBw,
                            onValueChange = { value ->
                                val filteredValue = value.filterNot { it.isWhitespace() }
                                srtOverheadBw = filteredValue
                            },
                            keyboardType = KeyboardType.Number
                        )

                        // SRT Passphrase
                        InputPreference(
                            title = stringResource(id = R.string.srt_passphrase),
                            summary = stringResource(id = R.string.srt_passphrase_summary),
                            value = srtPassphrase,
                            isPassword = true,
                            onValueChange = { value ->
                                srtPassphrase = value
                            }
                        )
                    }
                }
            }
        }
    }
}
