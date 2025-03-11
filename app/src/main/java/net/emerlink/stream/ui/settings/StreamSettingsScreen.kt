package net.emerlink.stream.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import net.emerlink.stream.R
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.model.StreamType
import net.emerlink.stream.ui.settings.components.DropdownPreference
import net.emerlink.stream.ui.settings.components.InputPreference
import net.emerlink.stream.ui.settings.components.PreferenceCategory
import net.emerlink.stream.ui.settings.components.SwitchPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val scrollState = rememberScrollState()

    // Stream Settings states
    var streamVideo by remember {
        mutableStateOf(
            preferences.getBoolean(
                PreferenceKeys.STREAM_VIDEO, PreferenceKeys.STREAM_VIDEO_DEFAULT
            )
        )
    }
    var streamProtocol by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.STREAM_PROTOCOL, PreferenceKeys.STREAM_PROTOCOL_DEFAULT
            ) ?: PreferenceKeys.STREAM_PROTOCOL_DEFAULT
        )
    }
    var streamAddress by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.STREAM_ADDRESS, PreferenceKeys.STREAM_ADDRESS_DEFAULT
            ) ?: PreferenceKeys.STREAM_ADDRESS_DEFAULT
        )
    }
    var streamPort by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.STREAM_PORT, PreferenceKeys.STREAM_PORT_DEFAULT
            ) ?: PreferenceKeys.STREAM_PORT_DEFAULT
        )
    }
    var streamPath by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.STREAM_PATH, PreferenceKeys.STREAM_PATH_DEFAULT
            ) ?: PreferenceKeys.STREAM_PATH_DEFAULT
        )
    }
    var streamUseTcp by remember {
        mutableStateOf(
            preferences.getBoolean(
                PreferenceKeys.STREAM_USE_TCP, PreferenceKeys.STREAM_USE_TCP_DEFAULT
            )
        )
    }
    var streamUsername by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.STREAM_USERNAME, PreferenceKeys.STREAM_USERNAME_DEFAULT
            ) ?: PreferenceKeys.STREAM_USERNAME_DEFAULT
        )
    }
    var streamPassword by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.STREAM_PASSWORD, PreferenceKeys.STREAM_PASSWORD_DEFAULT
            ) ?: PreferenceKeys.STREAM_PASSWORD_DEFAULT
        )
    }
    var streamSelfSignedCert by remember {
        mutableStateOf(
            preferences.getBoolean(
                PreferenceKeys.STREAM_SELF_SIGNED_CERT, PreferenceKeys.STREAM_SELF_SIGNED_CERT_DEFAULT
            )
        )
    }
    var streamCertificate by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.STREAM_CERTIFICATE, PreferenceKeys.STREAM_CERTIFICATE_DEFAULT
            ) ?: PreferenceKeys.STREAM_CERTIFICATE_DEFAULT
        )
    }
    var streamCertificatePassword by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.STREAM_CERTIFICATE_PASSWORD, PreferenceKeys.STREAM_CERTIFICATE_PASSWORD_DEFAULT
            ) ?: PreferenceKeys.STREAM_CERTIFICATE_PASSWORD_DEFAULT
        )
    }

    // Get current stream type
    val currentStreamType = StreamType.entries.find {
        it.toString() == streamProtocol
    } ?: StreamType.RTMP

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.stream_settings)) }, navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.back)
                    )
                }
            })
        }) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues), color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
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
                            preferences.edit {
                                putBoolean(PreferenceKeys.STREAM_VIDEO, checked)
                            }
                        })

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
                                preferences.edit {
                                    putString(PreferenceKeys.STREAM_PORT, newType.defaultPort)
                                }
                            }
                            preferences.edit {
                                putString(PreferenceKeys.STREAM_PROTOCOL, value)
                            }
                        },
                        enabled = streamVideo
                    )

                    InputPreference(
                        title = stringResource(id = R.string.stream_address),
                        summary = stringResource(id = R.string.stream_address_summary),
                        value = streamAddress,
                        onValueChange = { value ->
                            val filteredValue = value.filterNot { it.isWhitespace() }
                            streamAddress = filteredValue
                            preferences.edit {
                                putString(PreferenceKeys.STREAM_ADDRESS, filteredValue)
                            }
                        },
                        keyboardType = KeyboardType.Text,
                        enabled = streamVideo
                    )

                    InputPreference(
                        title = stringResource(id = R.string.stream_port),
                        summary = stringResource(id = R.string.stream_port_summary),
                        value = streamPort,
                        onValueChange = { value ->
                            val filteredValue = value.filterNot { it.isWhitespace() }
                            streamPort = filteredValue
                            preferences.edit {
                                putString(PreferenceKeys.STREAM_PORT, filteredValue)
                            }
                        },
                        keyboardType = KeyboardType.Number,
                        enabled = streamVideo
                    )

                    InputPreference(
                        title = stringResource(id = R.string.stream_path),
                        summary = stringResource(id = R.string.stream_path_summary),
                        value = streamPath,
                        onValueChange = { value ->
                            val filteredValue = value.filterNot { it.isWhitespace() }
                            streamPath = filteredValue
                            preferences.edit {
                                putString(PreferenceKeys.STREAM_PATH, filteredValue)
                            }
                        },
                        enabled = streamVideo
                    )

                    // Show TCP switch only for RTSP/RTSPS
                    if (currentStreamType in listOf(StreamType.RTSP, StreamType.RTSPs)) {
                        SwitchPreference(
                            title = stringResource(id = R.string.use_tcp),
                            summary = stringResource(id = R.string.use_tcp_summary),
                            checked = streamUseTcp,
                            onCheckedChange = { checked ->
                                streamUseTcp = checked
                                preferences.edit {
                                    putBoolean(PreferenceKeys.STREAM_USE_TCP, checked)
                                }
                            },
                            enabled = streamVideo
                        )
                    }

                    // Show authentication fields only for protocols that support it
                    if (currentStreamType.requiresAuth) {
                        InputPreference(
                            title = stringResource(id = R.string.username),
                            summary = stringResource(id = R.string.username_summary),
                            value = streamUsername,
                            onValueChange = { value ->
                                val filteredValue = value.filterNot { it.isWhitespace() }
                                streamUsername = filteredValue
                                preferences.edit {
                                    putString(PreferenceKeys.STREAM_USERNAME, filteredValue)
                                }
                            },
                            enabled = streamVideo
                        )

                        InputPreference(
                            title = stringResource(id = R.string.password),
                            summary = stringResource(id = R.string.password_summary),
                            value = streamPassword,
                            isPassword = true,
                            onValueChange = { value ->
                                val filteredValue = value.filterNot { it.isWhitespace() }
                                streamPassword = filteredValue
                                preferences.edit {
                                    putString(PreferenceKeys.STREAM_PASSWORD, filteredValue)
                                }
                            },
                            enabled = streamVideo
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
                                preferences.edit {
                                    putBoolean(PreferenceKeys.STREAM_SELF_SIGNED_CERT, checked)
                                }
                            },
                            enabled = streamVideo
                        )

                        if (streamSelfSignedCert && streamVideo) {
                            InputPreference(
                                title = stringResource(id = R.string.certificate),
                                summary = stringResource(id = R.string.certificate_summary),
                                value = streamCertificate,
                                onValueChange = { value ->
                                    streamCertificate = value
                                    preferences.edit {
                                        putString(PreferenceKeys.STREAM_CERTIFICATE, value)
                                    }
                                },
                                enabled = streamVideo
                            )

                            InputPreference(
                                title = stringResource(id = R.string.certificate_password),
                                summary = stringResource(id = R.string.certificate_password_summary),
                                value = streamCertificatePassword,
                                isPassword = true,
                                onValueChange = { value ->
                                    streamCertificatePassword = value
                                    preferences.edit {
                                        putString(PreferenceKeys.STREAM_CERTIFICATE_PASSWORD, value)
                                    }
                                },
                                enabled = streamVideo
                            )
                        }
                    }
                }
            }
        }
    }
}
