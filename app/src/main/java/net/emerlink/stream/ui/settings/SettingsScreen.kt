package net.emerlink.stream.ui.settings

import android.content.Context
import android.content.pm.ActivityInfo
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
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
    onBackClick: () -> Unit, onAdvancedSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val scrollState = rememberScrollState()
    
    // List to store available camera resolutions
    val availableResolutions = remember { mutableStateListOf<String>() }

    // Get available camera resolutions when the screen is first composed
    LaunchedEffect(Unit) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_BACK
            }
            
            cameraId?.let { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                
                configs?.let { config ->
                    val sizes = config.getOutputSizes(android.graphics.ImageFormat.JPEG)
                    sizes?.let { 
                        // Sort resolutions by area (width * height) in descending order
                        val sortedSizes = sizes.sortedByDescending { it.width * it.height }
                        
                        // Add resolutions to the list
                        availableResolutions.clear()
                        sortedSizes.forEach { size ->
                            availableResolutions.add("${size.width}x${size.height}")
                        }
                        
                        // If no resolutions were found, add default ones
                        if (availableResolutions.isEmpty()) {
                            availableResolutions.addAll(listOf("1920x1080", "1280x720", "854x480", "640x360"))
                        }
                    }
                }
            }
            
            // If we couldn't get camera resolutions, use default ones
            if (availableResolutions.isEmpty()) {
                availableResolutions.addAll(listOf("1920x1080", "1280x720", "854x480", "640x360"))
            }
        } catch (e: Exception) {
            // Fallback to default resolutions if there's an error
            availableResolutions.clear()
            availableResolutions.addAll(listOf("1920x1080", "1280x720", "854x480", "640x360"))
        }
    }

    // Состояния для отслеживания изменений настроек
    // Stream Settings
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

    // Video Settings
    var videoResolution by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.VIDEO_RESOLUTION, PreferenceKeys.VIDEO_RESOLUTION_DEFAULT
            ) ?: PreferenceKeys.VIDEO_RESOLUTION_DEFAULT
        )
    }
    var videoFps by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.VIDEO_FPS, PreferenceKeys.VIDEO_FPS_DEFAULT
            ) ?: PreferenceKeys.VIDEO_FPS_DEFAULT
        )
    }
    var videoBitrate by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.VIDEO_BITRATE, PreferenceKeys.VIDEO_BITRATE_DEFAULT
            ) ?: PreferenceKeys.VIDEO_BITRATE_DEFAULT
        )
    }
    var videoCodec by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.VIDEO_CODEC, PreferenceKeys.VIDEO_CODEC_DEFAULT
            ) ?: PreferenceKeys.VIDEO_CODEC_DEFAULT
        )
    }
    var videoAdaptiveBitrate by remember {
        mutableStateOf(
            preferences.getBoolean(
                PreferenceKeys.VIDEO_ADAPTIVE_BITRATE, PreferenceKeys.VIDEO_ADAPTIVE_BITRATE_DEFAULT
            )
        )
    }
    var recordVideo by remember {
        mutableStateOf(
            preferences.getBoolean(
                PreferenceKeys.RECORD_VIDEO, PreferenceKeys.RECORD_VIDEO_DEFAULT
            )
        )
    }
    var screenOrientation by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.SCREEN_ORIENTATION, PreferenceKeys.SCREEN_ORIENTATION_DEFAULT
            ) ?: PreferenceKeys.SCREEN_ORIENTATION_DEFAULT
        )
    }

    // Audio Settings
    var enableAudio by remember {
        mutableStateOf(
            preferences.getBoolean(
                PreferenceKeys.ENABLE_AUDIO, PreferenceKeys.ENABLE_AUDIO_DEFAULT
            )
        )
    }
    var audioBitrate by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.AUDIO_BITRATE, PreferenceKeys.AUDIO_BITRATE_DEFAULT
            ) ?: PreferenceKeys.AUDIO_BITRATE_DEFAULT
        )
    }
    var audioSampleRate by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.AUDIO_SAMPLE_RATE, PreferenceKeys.AUDIO_SAMPLE_RATE_DEFAULT
            ) ?: PreferenceKeys.AUDIO_SAMPLE_RATE_DEFAULT
        )
    }
    var audioStereo by remember {
        mutableStateOf(
            preferences.getBoolean(
                PreferenceKeys.AUDIO_STEREO, PreferenceKeys.AUDIO_STEREO_DEFAULT
            )
        )
    }
    var audioEchoCancel by remember {
        mutableStateOf(
            preferences.getBoolean(
                PreferenceKeys.AUDIO_ECHO_CANCEL, PreferenceKeys.AUDIO_ECHO_CANCEL_DEFAULT
            )
        )
    }
    var audioNoiseReduction by remember {
        mutableStateOf(
            preferences.getBoolean(
                PreferenceKeys.AUDIO_NOISE_REDUCTION, PreferenceKeys.AUDIO_NOISE_REDUCTION_DEFAULT
            )
        )
    }
    var audioCodec by remember {
        mutableStateOf(
            preferences.getString(
                PreferenceKeys.AUDIO_CODEC, PreferenceKeys.AUDIO_CODEC_DEFAULT
            ) ?: PreferenceKeys.AUDIO_CODEC_DEFAULT
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.settings)) }, navigationIcon = {
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
                            preferences.edit { putBoolean(PreferenceKeys.STREAM_VIDEO, checked) }
                        })

                    DropdownPreference(
                        title = stringResource(id = R.string.stream_protocol),
                        summary = stringResource(id = R.string.stream_protocol_summary),
                        selectedValue = streamProtocol,
                        options = listOf("rtmp", "rtmps", "rtsp", "rtsps", "srt"),
                        onValueSelected = { value ->
                            streamProtocol = value
                            preferences.edit { putString(PreferenceKeys.STREAM_PROTOCOL, value) }
                        })

                    InputPreference(
                        title = stringResource(id = R.string.stream_address),
                        summary = stringResource(id = R.string.stream_address_summary),
                        value = streamAddress,
                        onValueChange = { value ->
                            streamAddress = value
                            preferences.edit { putString(PreferenceKeys.STREAM_ADDRESS, value) }
                        })

                    InputPreference(
                        title = stringResource(id = R.string.stream_port),
                        summary = stringResource(id = R.string.stream_port_summary),
                        value = streamPort,
                        onValueChange = { value ->
                            streamPort = value
                            preferences.edit { putString(PreferenceKeys.STREAM_PORT, value) }
                        })

                    InputPreference(
                        title = stringResource(id = R.string.stream_path),
                        summary = stringResource(id = R.string.stream_path_summary),
                        value = streamPath,
                        onValueChange = { value ->
                            streamPath = value
                            preferences.edit { putString(PreferenceKeys.STREAM_PATH, value) }
                        })

                    SwitchPreference(
                        title = stringResource(id = R.string.use_tcp),
                        summary = stringResource(id = R.string.use_tcp_summary),
                        checked = streamUseTcp,
                        onCheckedChange = { checked ->
                            streamUseTcp = checked
                            preferences.edit {
                                putBoolean(
                                    PreferenceKeys.STREAM_USE_TCP, checked
                                )
                            }
                        })

                    InputPreference(
                        title = stringResource(id = R.string.username),
                        summary = stringResource(id = R.string.username_summary),
                        value = streamUsername,
                        onValueChange = { value ->
                            streamUsername = value
                            preferences.edit { putString(PreferenceKeys.STREAM_USERNAME, value) }
                        })

                    InputPreference(
                        title = stringResource(id = R.string.password),
                        summary = stringResource(id = R.string.password_summary),
                        value = streamPassword,
                        isPassword = true,
                        onValueChange = { value ->
                            streamPassword = value
                            preferences.edit { putString(PreferenceKeys.STREAM_PASSWORD, value) }
                        })
                }

                // Video Settings
                PreferenceCategory(title = stringResource(id = R.string.video_settings)) {
                    DropdownPreference(
                        title = stringResource(id = R.string.video_resolution),
                        summary = stringResource(id = R.string.video_resolution_summary),
                        selectedValue = videoResolution,
                        options = availableResolutions.toList(),
                        onValueSelected = { value ->
                            videoResolution = value
                            preferences.edit { putString(PreferenceKeys.VIDEO_RESOLUTION, value) }
                        })

                    InputPreference(
                        title = stringResource(id = R.string.video_fps),
                        summary = stringResource(id = R.string.video_fps_summary),
                        value = videoFps,
                        onValueChange = { value ->
                            videoFps = value
                            preferences.edit { putString(PreferenceKeys.VIDEO_FPS, value) }
                        })

                    InputPreference(
                        title = stringResource(id = R.string.video_bitrate),
                        summary = stringResource(id = R.string.video_bitrate_summary),
                        value = videoBitrate,
                        onValueChange = { value ->
                            videoBitrate = value
                            preferences.edit { putString(PreferenceKeys.VIDEO_BITRATE, value) }
                        })

                    DropdownPreference(
                        title = stringResource(id = R.string.video_codec),
                        summary = stringResource(id = R.string.video_codec_summary),
                        selectedValue = videoCodec,
                        options = listOf("h264", "h265"),
                        onValueSelected = { value ->
                            videoCodec = value
                            preferences.edit { putString(PreferenceKeys.VIDEO_CODEC, value) }
                        })

                    SwitchPreference(
                        title = stringResource(id = R.string.adaptive_bitrate),
                        summary = stringResource(id = R.string.adaptive_bitrate_summary),
                        checked = videoAdaptiveBitrate,
                        onCheckedChange = { checked ->
                            videoAdaptiveBitrate = checked
                            preferences.edit {
                                putBoolean(
                                    PreferenceKeys.VIDEO_ADAPTIVE_BITRATE, checked
                                )
                            }
                        })

                    SwitchPreference(
                        title = stringResource(id = R.string.record_video),
                        summary = stringResource(id = R.string.record_video_summary),
                        checked = recordVideo,
                        onCheckedChange = { checked ->
                            recordVideo = checked
                            preferences.edit { putBoolean(PreferenceKeys.RECORD_VIDEO, checked) }
                        })

                    DropdownPreference(
                        title = stringResource(id = R.string.screen_orientation),
                        summary = stringResource(id = R.string.screen_orientation_summary),
                        selectedValue = screenOrientation,
                        options = listOf("landscape", "portrait", "auto"),
                        onValueSelected = { value ->
                            screenOrientation = value
                            preferences.edit { putString(PreferenceKeys.SCREEN_ORIENTATION, value) }
                        })
                }

                // Audio Settings
                PreferenceCategory(title = stringResource(id = R.string.audio_settings)) {
                    SwitchPreference(
                        title = stringResource(id = R.string.enable_audio),
                        summary = stringResource(id = R.string.enable_audio_summary),
                        checked = enableAudio,
                        onCheckedChange = { checked ->
                            enableAudio = checked
                            preferences.edit { putBoolean(PreferenceKeys.ENABLE_AUDIO, checked) }
                        })

                    InputPreference(
                        title = stringResource(id = R.string.audio_bitrate),
                        summary = stringResource(id = R.string.audio_bitrate_summary),
                        value = audioBitrate,
                        onValueChange = { value ->
                            audioBitrate = value
                            preferences.edit { putString(PreferenceKeys.AUDIO_BITRATE, value) }
                        })

                    DropdownPreference(
                        title = stringResource(id = R.string.audio_sample_rate),
                        summary = stringResource(id = R.string.audio_sample_rate_summary),
                        selectedValue = audioSampleRate,
                        options = listOf("8000", "16000", "22050", "32000", "44100", "48000"),
                        onValueSelected = { value ->
                            audioSampleRate = value
                            preferences.edit {
                                putString(
                                    PreferenceKeys.AUDIO_SAMPLE_RATE, value
                                )
                            }
                        })

                    SwitchPreference(
                        stringResource(id = R.string.stereo), stringResource(id = R.string.stereo_summary), audioStereo
                    ) { checked ->
                        audioStereo = checked
                        preferences.edit { putBoolean(PreferenceKeys.AUDIO_STEREO, checked) }
                    }

                    SwitchPreference(
                        stringResource(id = R.string.echo_cancellation),
                        stringResource(id = R.string.echo_cancellation_summary),
                        audioEchoCancel
                    ) { checked ->
                        audioEchoCancel = checked
                        preferences.edit {
                            putBoolean(
                                PreferenceKeys.AUDIO_ECHO_CANCEL, checked
                            )
                        }
                    }

                    SwitchPreference(
                        title = stringResource(id = R.string.noise_reduction),
                        summary = stringResource(id = R.string.noise_reduction_summary),
                        checked = audioNoiseReduction,
                        onCheckedChange = { checked ->
                            audioNoiseReduction = checked
                            preferences.edit {
                                putBoolean(
                                    PreferenceKeys.AUDIO_NOISE_REDUCTION, checked
                                )
                            }
                        })

                    DropdownPreference(
                        title = stringResource(id = R.string.audio_codec),
                        summary = stringResource(id = R.string.audio_codec_summary),
                        selectedValue = audioCodec,
                        options = listOf("aac", "opus"),
                        onValueSelected = { value ->
                            audioCodec = value
                            preferences.edit { putString(PreferenceKeys.AUDIO_CODEC, value) }
                        })
                }

                // Advanced Settings Button
                Button(
                    onClick = onAdvancedSettingsClick, modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Text(text = stringResource(id = R.string.advanced_settings))
                }
            }
        }
    }
} 