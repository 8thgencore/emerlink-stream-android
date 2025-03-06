package net.emerlink.stream.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import net.emerlink.stream.R
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.ui.settings.components.DropdownPreference
import net.emerlink.stream.ui.settings.components.InputPreference
import net.emerlink.stream.ui.settings.components.PreferenceCategory
import net.emerlink.stream.ui.settings.components.SwitchPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onAdvancedSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
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
                        checked = preferences.getBoolean(
                            PreferenceKeys.STREAM_VIDEO,
                            PreferenceKeys.STREAM_VIDEO_DEFAULT
                        ),
                        onCheckedChange = { checked ->
                            preferences.edit { putBoolean(PreferenceKeys.STREAM_VIDEO, checked) }
                        }
                    )

                    DropdownPreference(
                        title = stringResource(id = R.string.stream_protocol),
                        summary = stringResource(id = R.string.stream_protocol_summary),
                        selectedValue = preferences.getString(
                            PreferenceKeys.STREAM_PROTOCOL,
                            PreferenceKeys.STREAM_PROTOCOL_DEFAULT
                        ) ?: PreferenceKeys.STREAM_PROTOCOL_DEFAULT,
                        options = listOf("rtmp", "rtmps", "rtsp", "rtsps", "srt"),
                        onValueSelected = { value ->
                            preferences.edit { putString(PreferenceKeys.STREAM_PROTOCOL, value) }
                        }
                    )

                    InputPreference(
                        title = stringResource(id = R.string.stream_address),
                        summary = stringResource(id = R.string.stream_address_summary),
                        value = preferences.getString(
                            PreferenceKeys.STREAM_ADDRESS,
                            PreferenceKeys.STREAM_ADDRESS_DEFAULT
                        ) ?: PreferenceKeys.STREAM_ADDRESS_DEFAULT,
                        onValueChange = { value ->
                            preferences.edit { putString(PreferenceKeys.STREAM_ADDRESS, value) }
                        }
                    )

                    InputPreference(
                        title = stringResource(id = R.string.stream_port),
                        summary = stringResource(id = R.string.stream_port_summary),
                        value = preferences.getString(
                            PreferenceKeys.STREAM_PORT,
                            PreferenceKeys.STREAM_PORT_DEFAULT
                        ) ?: PreferenceKeys.STREAM_PORT_DEFAULT,
                        onValueChange = { value ->
                            preferences.edit { putString(PreferenceKeys.STREAM_PORT, value) }
                        }
                    )

                    InputPreference(
                        title = stringResource(id = R.string.stream_path),
                        summary = stringResource(id = R.string.stream_path_summary),
                        value = preferences.getString(
                            PreferenceKeys.STREAM_PATH,
                            PreferenceKeys.STREAM_PATH_DEFAULT
                        ) ?: PreferenceKeys.STREAM_PATH_DEFAULT,
                        onValueChange = { value ->
                            preferences.edit { putString(PreferenceKeys.STREAM_PATH, value) }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(id = R.string.use_tcp),
                        summary = stringResource(id = R.string.use_tcp_summary),
                        checked = preferences.getBoolean(
                            PreferenceKeys.STREAM_USE_TCP,
                            PreferenceKeys.STREAM_USE_TCP_DEFAULT
                        ),
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(
                                    PreferenceKeys.STREAM_USE_TCP,
                                    checked
                                )
                            }
                        }
                    )

                    InputPreference(
                        title = stringResource(id = R.string.username),
                        summary = stringResource(id = R.string.username_summary),
                        value = preferences.getString(
                            PreferenceKeys.STREAM_USERNAME,
                            PreferenceKeys.STREAM_USERNAME_DEFAULT
                        ) ?: PreferenceKeys.STREAM_USERNAME_DEFAULT,
                        onValueChange = { value ->
                            preferences.edit { putString(PreferenceKeys.STREAM_USERNAME, value) }
                        }
                    )

                    InputPreference(
                        title = stringResource(id = R.string.password),
                        summary = stringResource(id = R.string.password_summary),
                        value = preferences.getString(
                            PreferenceKeys.STREAM_PASSWORD,
                            PreferenceKeys.STREAM_PASSWORD_DEFAULT
                        ) ?: PreferenceKeys.STREAM_PASSWORD_DEFAULT,
                        isPassword = true,
                        onValueChange = { value ->
                            preferences.edit { putString(PreferenceKeys.STREAM_PASSWORD, value) }
                        }
                    )
                }

                // Video Settings
                PreferenceCategory(title = stringResource(id = R.string.video_settings)) {
                    DropdownPreference(
                        title = stringResource(id = R.string.video_resolution),
                        summary = stringResource(id = R.string.video_resolution_summary),
                        selectedValue = preferences.getString(
                            PreferenceKeys.VIDEO_RESOLUTION,
                            PreferenceKeys.VIDEO_RESOLUTION_DEFAULT
                        ) ?: PreferenceKeys.VIDEO_RESOLUTION_DEFAULT,
                        options = listOf("1920x1080", "1280x720", "854x480", "640x360"),
                        onValueSelected = { value ->
                            preferences.edit { putString(PreferenceKeys.VIDEO_RESOLUTION, value) }
                        }
                    )

                    InputPreference(
                        title = stringResource(id = R.string.video_fps),
                        summary = stringResource(id = R.string.video_fps_summary),
                        value = preferences.getString(
                            PreferenceKeys.VIDEO_FPS,
                            PreferenceKeys.VIDEO_FPS_DEFAULT
                        ) ?: PreferenceKeys.VIDEO_FPS_DEFAULT,
                        onValueChange = { value ->
                            preferences.edit { putString(PreferenceKeys.VIDEO_FPS, value) }
                        }
                    )

                    InputPreference(
                        title = stringResource(id = R.string.video_bitrate),
                        summary = stringResource(id = R.string.video_bitrate_summary),
                        value = preferences.getString(
                            PreferenceKeys.VIDEO_BITRATE,
                            PreferenceKeys.VIDEO_BITRATE_DEFAULT
                        ) ?: PreferenceKeys.VIDEO_BITRATE_DEFAULT,
                        onValueChange = { value ->
                            preferences.edit { putString(PreferenceKeys.VIDEO_BITRATE, value) }
                        }
                    )

                    DropdownPreference(
                        title = stringResource(id = R.string.video_codec),
                        summary = stringResource(id = R.string.video_codec_summary),
                        selectedValue = preferences.getString(
                            PreferenceKeys.VIDEO_CODEC,
                            PreferenceKeys.VIDEO_CODEC_DEFAULT
                        ) ?: PreferenceKeys.VIDEO_CODEC_DEFAULT,
                        options = listOf("h264", "h265"),
                        onValueSelected = { value ->
                            preferences.edit { putString(PreferenceKeys.VIDEO_CODEC, value) }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(id = R.string.adaptive_bitrate),
                        summary = stringResource(id = R.string.adaptive_bitrate_summary),
                        checked = preferences.getBoolean(
                            PreferenceKeys.VIDEO_ADAPTIVE_BITRATE,
                            PreferenceKeys.VIDEO_ADAPTIVE_BITRATE_DEFAULT
                        ),
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(
                                    PreferenceKeys.VIDEO_ADAPTIVE_BITRATE,
                                    checked
                                )
                            }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(id = R.string.record_video),
                        summary = stringResource(id = R.string.record_video_summary),
                        checked = preferences.getBoolean(
                            PreferenceKeys.RECORD_VIDEO,
                            PreferenceKeys.RECORD_VIDEO_DEFAULT
                        ),
                        onCheckedChange = { checked ->
                            preferences.edit { putBoolean(PreferenceKeys.RECORD_VIDEO, checked) }
                        }
                    )

                    DropdownPreference(
                        title = stringResource(id = R.string.screen_orientation),
                        summary = stringResource(id = R.string.screen_orientation_summary),
                        selectedValue = preferences.getString(
                            PreferenceKeys.SCREEN_ORIENTATION,
                            PreferenceKeys.SCREEN_ORIENTATION_DEFAULT
                        ) ?: PreferenceKeys.SCREEN_ORIENTATION_DEFAULT,
                        options = listOf("landscape", "portrait", "auto"),
                        onValueSelected = { value ->
                            preferences.edit { putString(PreferenceKeys.SCREEN_ORIENTATION, value) }
                            // Apply orientation change immediately
                            when (value) {
                                "landscape" -> (context as? android.app.Activity)?.requestedOrientation =
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

                                "portrait" -> (context as? android.app.Activity)?.requestedOrientation =
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

                                "auto" -> (context as? android.app.Activity)?.requestedOrientation =
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                            }
                        }
                    )
                }

                // Audio Settings
                PreferenceCategory(title = stringResource(id = R.string.audio_settings)) {
                    SwitchPreference(
                        title = stringResource(id = R.string.enable_audio),
                        summary = stringResource(id = R.string.enable_audio_summary),
                        checked = preferences.getBoolean(
                            PreferenceKeys.ENABLE_AUDIO,
                            PreferenceKeys.ENABLE_AUDIO_DEFAULT
                        ),
                        onCheckedChange = { checked ->
                            preferences.edit { putBoolean(PreferenceKeys.ENABLE_AUDIO, checked) }
                        }
                    )

                    InputPreference(
                        title = stringResource(id = R.string.audio_bitrate),
                        summary = stringResource(id = R.string.audio_bitrate_summary),
                        value = preferences.getString(
                            PreferenceKeys.AUDIO_BITRATE,
                            PreferenceKeys.AUDIO_BITRATE_DEFAULT
                        ) ?: PreferenceKeys.AUDIO_BITRATE_DEFAULT,
                        onValueChange = { value ->
                            preferences.edit { putString(PreferenceKeys.AUDIO_BITRATE, value) }
                        }
                    )

                    DropdownPreference(
                        title = stringResource(id = R.string.audio_sample_rate),
                        summary = stringResource(id = R.string.audio_sample_rate_summary),
                        selectedValue = preferences.getString(
                            PreferenceKeys.AUDIO_SAMPLE_RATE,
                            PreferenceKeys.AUDIO_SAMPLE_RATE_DEFAULT
                        ) ?: PreferenceKeys.AUDIO_SAMPLE_RATE_DEFAULT,
                        options = listOf("8000", "16000", "22050", "32000", "44100", "48000"),
                        onValueSelected = { value ->
                            preferences.edit {
                                putString(
                                    PreferenceKeys.AUDIO_SAMPLE_RATE,
                                    value
                                )
                            }
                        }
                    )

                    SwitchPreference(
                        stringResource(id = R.string.stereo),
                        stringResource(id = R.string.stereo_summary),
                        preferences.getBoolean(
                            PreferenceKeys.AUDIO_STEREO,
                            PreferenceKeys.AUDIO_STEREO_DEFAULT
                        )
                    ) { checked ->
                        preferences.edit { putBoolean(PreferenceKeys.AUDIO_STEREO, checked) }
                    }

                    SwitchPreference(
                        stringResource(id = R.string.echo_cancellation),
                        stringResource(id = R.string.echo_cancellation_summary),
                        preferences.getBoolean(
                            PreferenceKeys.AUDIO_ECHO_CANCEL,
                            PreferenceKeys.AUDIO_ECHO_CANCEL_DEFAULT
                        )
                    ) { checked ->
                        preferences.edit {
                            putBoolean(
                                PreferenceKeys.AUDIO_ECHO_CANCEL,
                                checked
                            )
                        }
                    }

                    SwitchPreference(
                        title = stringResource(id = R.string.noise_reduction),
                        summary = stringResource(id = R.string.noise_reduction_summary),
                        checked = preferences.getBoolean(
                            PreferenceKeys.AUDIO_NOISE_REDUCTION,
                            PreferenceKeys.AUDIO_NOISE_REDUCTION_DEFAULT
                        ),
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(
                                    PreferenceKeys.AUDIO_NOISE_REDUCTION,
                                    checked
                                )
                            }
                        }
                    )

                    DropdownPreference(
                        title = stringResource(id = R.string.audio_codec),
                        summary = stringResource(id = R.string.audio_codec_summary),
                        selectedValue = preferences.getString(
                            PreferenceKeys.AUDIO_CODEC,
                            PreferenceKeys.AUDIO_CODEC_DEFAULT
                        ) ?: PreferenceKeys.AUDIO_CODEC_DEFAULT,
                        options = listOf("aac", "opus"),
                        onValueSelected = { value ->
                            preferences.edit { putString(PreferenceKeys.AUDIO_CODEC, value) }
                        }
                    )
                }

                // Advanced Settings Button
                Button(
                    onClick = onAdvancedSettingsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Text(text = stringResource(id = R.string.advanced_settings))
                }
            }
        }
    }
} 