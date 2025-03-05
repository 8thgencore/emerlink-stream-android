package net.emerlink.stream.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import net.emerlink.stream.R
import net.emerlink.stream.data.preferences.PreferenceKeys
import net.emerlink.stream.ui.settings.components.DropdownPreference
import net.emerlink.stream.ui.settings.components.InputPreference
import net.emerlink.stream.ui.settings.components.PreferenceCategory
import net.emerlink.stream.ui.settings.components.SwitchPreference
import androidx.core.content.edit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.advanced_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            // Advanced Video Settings
            PreferenceCategory(title = stringResource(id = R.string.advanced_video_settings)) {
                InputPreference(
                    title = stringResource(id = R.string.keyframe_interval),
                    summary = stringResource(id = R.string.keyframe_interval_summary),
                    value = preferences.getString(
                        PreferenceKeys.VIDEO_KEYFRAME_INTERVAL,
                        PreferenceKeys.VIDEO_KEYFRAME_INTERVAL_DEFAULT
                    ) ?: PreferenceKeys.VIDEO_KEYFRAME_INTERVAL_DEFAULT,
                    onValueChange = { value ->
                        preferences.edit() {
                            putString(
                                PreferenceKeys.VIDEO_KEYFRAME_INTERVAL,
                                value
                            )
                        }
                    },
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )

                DropdownPreference(
                    title = stringResource(id = R.string.video_profile),
                    summary = stringResource(id = R.string.video_profile_summary),
                    selectedValue = preferences.getString(
                        PreferenceKeys.VIDEO_PROFILE,
                        PreferenceKeys.VIDEO_PROFILE_DEFAULT
                    ) ?: PreferenceKeys.VIDEO_PROFILE_DEFAULT,
                    options = listOf("baseline", "main", "high"),
                    onValueSelected = { value ->
                        preferences.edit() { putString(PreferenceKeys.VIDEO_PROFILE, value) }
                    }
                )

                DropdownPreference(
                    title = stringResource(id = R.string.video_level),
                    summary = stringResource(id = R.string.video_level_summary),
                    selectedValue = preferences.getString(
                        PreferenceKeys.VIDEO_LEVEL,
                        PreferenceKeys.VIDEO_LEVEL_DEFAULT
                    ) ?: PreferenceKeys.VIDEO_LEVEL_DEFAULT,
                    options = listOf("3.0", "3.1", "4.0", "4.1", "4.2"),
                    onValueSelected = { value ->
                        preferences.edit() { putString(PreferenceKeys.VIDEO_LEVEL, value) }
                    }
                )

                DropdownPreference(
                    title = stringResource(id = R.string.bitrate_mode),
                    summary = stringResource(id = R.string.bitrate_mode_summary),
                    selectedValue = preferences.getString(
                        PreferenceKeys.VIDEO_BITRATE_MODE,
                        PreferenceKeys.VIDEO_BITRATE_MODE_DEFAULT
                    ) ?: PreferenceKeys.VIDEO_BITRATE_MODE_DEFAULT,
                    options = listOf("vbr", "cbr", "cq"),
                    onValueSelected = { value ->
                        preferences.edit() {
                            putString(PreferenceKeys.VIDEO_BITRATE_MODE, value)
                        }
                    }
                )

                DropdownPreference(
                    title = stringResource(id = R.string.encoding_quality),
                    summary = stringResource(id = R.string.encoding_quality_summary),
                    selectedValue = preferences.getString(
                        PreferenceKeys.VIDEO_QUALITY,
                        PreferenceKeys.VIDEO_QUALITY_DEFAULT
                    ) ?: PreferenceKeys.VIDEO_QUALITY_DEFAULT,
                    options = listOf("fastest", "fast", "medium", "slow", "slowest"),
                    onValueSelected = { value ->
                        preferences.edit() { putString(PreferenceKeys.VIDEO_QUALITY, value) }
                    }
                )
            }

            // Network Settings
            PreferenceCategory(title = stringResource(id = R.string.network_settings)) {
                InputPreference(
                    title = stringResource(id = R.string.buffer_size),
                    summary = stringResource(id = R.string.buffer_size_summary),
                    value = preferences.getString(
                        PreferenceKeys.NETWORK_BUFFER_SIZE,
                        PreferenceKeys.NETWORK_BUFFER_SIZE_DEFAULT
                    ) ?: PreferenceKeys.NETWORK_BUFFER_SIZE_DEFAULT,
                    onValueChange = { value ->
                        preferences.edit() {
                            putString(PreferenceKeys.NETWORK_BUFFER_SIZE, value)
                        }
                    },
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )

                InputPreference(
                    title = stringResource(id = R.string.connection_timeout),
                    summary = stringResource(id = R.string.connection_timeout_summary),
                    value = preferences.getString(
                        PreferenceKeys.NETWORK_TIMEOUT,
                        PreferenceKeys.NETWORK_TIMEOUT_DEFAULT
                    ) ?: PreferenceKeys.NETWORK_TIMEOUT_DEFAULT,
                    onValueChange = { value ->
                        preferences.edit().putString(PreferenceKeys.NETWORK_TIMEOUT, value).apply()
                    },
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )

                SwitchPreference(
                    title = stringResource(id = R.string.auto_reconnect),
                    summary = stringResource(id = R.string.auto_reconnect_summary),
                    checked = preferences.getBoolean(
                        PreferenceKeys.NETWORK_RECONNECT,
                        PreferenceKeys.NETWORK_RECONNECT_DEFAULT
                    ),
                    onCheckedChange = { checked ->
                        preferences.edit().putBoolean(PreferenceKeys.NETWORK_RECONNECT, checked)
                            .apply()
                    }
                )

                InputPreference(
                    title = stringResource(id = R.string.reconnect_delay),
                    summary = stringResource(id = R.string.reconnect_delay_summary),
                    value = preferences.getString(
                        PreferenceKeys.NETWORK_RECONNECT_DELAY,
                        PreferenceKeys.NETWORK_RECONNECT_DELAY_DEFAULT
                    ) ?: PreferenceKeys.NETWORK_RECONNECT_DELAY_DEFAULT,
                    onValueChange = { value ->
                        preferences.edit().putString(PreferenceKeys.NETWORK_RECONNECT_DELAY, value)
                            .apply()
                    },
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )

                InputPreference(
                    title = stringResource(id = R.string.max_reconnect_attempts),
                    summary = stringResource(id = R.string.max_reconnect_attempts_summary),
                    value = preferences.getString(
                        PreferenceKeys.NETWORK_MAX_RECONNECT_ATTEMPTS,
                        PreferenceKeys.NETWORK_MAX_RECONNECT_ATTEMPTS_DEFAULT
                    ) ?: PreferenceKeys.NETWORK_MAX_RECONNECT_ATTEMPTS_DEFAULT,
                    onValueChange = { value ->
                        preferences.edit()
                            .putString(PreferenceKeys.NETWORK_MAX_RECONNECT_ATTEMPTS, value).apply()
                    },
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            }

            // Stability Settings
            PreferenceCategory(title = stringResource(id = R.string.stability_settings)) {
                SwitchPreference(
                    title = stringResource(id = R.string.low_latency_mode),
                    summary = stringResource(id = R.string.low_latency_mode_summary),
                    checked = preferences.getBoolean(
                        PreferenceKeys.STABILITY_LOW_LATENCY,
                        PreferenceKeys.STABILITY_LOW_LATENCY_DEFAULT
                    ),
                    onCheckedChange = { checked ->
                        preferences.edit().putBoolean(PreferenceKeys.STABILITY_LOW_LATENCY, checked)
                            .apply()
                    }
                )

                SwitchPreference(
                    title = stringResource(id = R.string.hardware_rotation),
                    summary = stringResource(id = R.string.hardware_rotation_summary),
                    checked = preferences.getBoolean(
                        PreferenceKeys.STABILITY_HARDWARE_ROTATION,
                        PreferenceKeys.STABILITY_HARDWARE_ROTATION_DEFAULT
                    ),
                    onCheckedChange = { checked ->
                        preferences.edit()
                            .putBoolean(PreferenceKeys.STABILITY_HARDWARE_ROTATION, checked).apply()
                    }
                )

                SwitchPreference(
                    title = stringResource(id = R.string.dynamic_fps),
                    summary = stringResource(id = R.string.dynamic_fps_summary),
                    checked = preferences.getBoolean(
                        PreferenceKeys.STABILITY_DYNAMIC_FPS,
                        PreferenceKeys.STABILITY_DYNAMIC_FPS_DEFAULT
                    ),
                    onCheckedChange = { checked ->
                        preferences.edit().putBoolean(PreferenceKeys.STABILITY_DYNAMIC_FPS, checked)
                            .apply()
                    }
                )
            }
        }
    }
} 