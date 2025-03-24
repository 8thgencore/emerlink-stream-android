@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:function-naming")

package net.emerlink.stream.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.emerlink.stream.R
import net.emerlink.stream.presentation.settings.components.DropdownPreference
import net.emerlink.stream.presentation.settings.components.InputPreference
import net.emerlink.stream.presentation.settings.components.PreferenceCategory
import net.emerlink.stream.presentation.settings.components.SwitchPreference
import net.emerlink.stream.presentation.settings.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onStreamSettingsClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val scrollState = rememberScrollState()

    // Collect state flows
    val videoSettings by viewModel.videoSettings.collectAsState()
    val audioSettings by viewModel.audioSettings.collectAsState()
    val availableResolutions by viewModel.availableResolutions.collectAsState()

    // Video Settings state
    var videoResolution by remember {
        mutableStateOf(
            videoSettings.resolution.width.toString() + "x" + videoSettings.resolution.height.toString()
        )
    }
    var screenOrientation by remember { mutableStateOf(videoSettings.screenOrientation) }
    var videoFps by remember { mutableStateOf(videoSettings.fps.toString()) }
    var videoBitrate by remember { mutableStateOf(videoSettings.bitrate.toString()) }
    var videoCodec by remember { mutableStateOf(videoSettings.codec) }
    var videoAdaptiveBitrate by remember { mutableStateOf(videoSettings.adaptiveBitrate) }
    var recordVideo by remember { mutableStateOf(videoSettings.recordVideo) }
    var keyframeInterval by remember { mutableStateOf(videoSettings.keyframeInterval.toString()) }

    // Audio Settings state
    var enableAudio by remember { mutableStateOf(audioSettings.enabled) }
    var audioBitrate by remember { mutableStateOf(audioSettings.bitrate.toString()) }
    var audioSampleRate by remember { mutableStateOf(audioSettings.sampleRate.toString()) }
    var audioStereo by remember { mutableStateOf(audioSettings.stereo) }
    var audioEchoCancel by remember { mutableStateOf(audioSettings.echoCancel) }
    var audioNoiseReduction by remember { mutableStateOf(audioSettings.noiseReduction) }
    var audioCodec by remember { mutableStateOf(audioSettings.codec) }

    // Update local state when viewModel state changes
    LaunchedEffect(videoSettings) {
        videoResolution = videoSettings.resolution.width.toString() + "x" + videoSettings.resolution.height.toString()
        screenOrientation = videoSettings.screenOrientation
        videoFps = videoSettings.fps.toString()
        videoBitrate = videoSettings.bitrate.toString()
        videoCodec = videoSettings.codec
        videoAdaptiveBitrate = videoSettings.adaptiveBitrate
        recordVideo = videoSettings.recordVideo
        keyframeInterval = videoSettings.keyframeInterval.toString()
    }

    LaunchedEffect(audioSettings) {
        enableAudio = audioSettings.enabled
        audioBitrate = audioSettings.bitrate.toString()
        audioSampleRate = audioSettings.sampleRate.toString()
        audioStereo = audioSettings.stereo
        audioEchoCancel = audioSettings.echoCancel
        audioNoiseReduction = audioSettings.noiseReduction
        audioCodec = audioSettings.codec
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
                // Stream Settings Navigation
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onStreamSettingsClick)
                            .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoCameraBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(id = R.string.stream_settings),
                        style = MaterialTheme.typography.titleMedium,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Video Settings
                PreferenceCategory(title = stringResource(id = R.string.video_settings)) {
                    DropdownPreference(
                        title = stringResource(id = R.string.video_resolution),
                        summary = stringResource(id = R.string.video_resolution_summary),
                        selectedValue = videoResolution,
                        options =
                            if (availableResolutions.isNotEmpty()) {
                                availableResolutions
                            } else {
                                listOf(
                                    "1920x1080",
                                    "1280x720",
                                    "854x480",
                                    "640x360"
                                )
                            },
                        onValueSelected = { value ->
                            videoResolution = value
                            viewModel.updateVideoResolution(value)
                        }
                    )

                    DropdownPreference(
                        title = stringResource(id = R.string.screen_orientation),
                        summary = stringResource(id = R.string.screen_orientation_summary),
                        selectedValue = screenOrientation,
                        options = listOf("landscape", "portrait"),
                        onValueSelected = { value ->
                            screenOrientation = value
                            viewModel.updateScreenOrientation(value)
                        }
                    )

                    InputPreference(
                        title = stringResource(id = R.string.video_fps),
                        summary = stringResource(id = R.string.video_fps_summary),
                        value = videoFps,
                        keyboardType = KeyboardType.Number,
                        onValueChange = { value ->
                            val filteredValue = value.filterNot { it.isWhitespace() }
                            videoFps = filteredValue
                            viewModel.updateVideoFps(filteredValue)
                        }
                    )

                    InputPreference(
                        title = stringResource(id = R.string.video_bitrate),
                        summary = stringResource(id = R.string.video_bitrate_summary),
                        value = videoBitrate,
                        keyboardType = KeyboardType.Number,
                        onValueChange = { value ->
                            val filteredValue = value.filterNot { it.isWhitespace() }
                            videoBitrate = filteredValue
                            viewModel.updateVideoBitrate(filteredValue)
                        }
                    )

                    DropdownPreference(
                        title = stringResource(id = R.string.video_codec),
                        summary = stringResource(id = R.string.video_codec_summary),
                        selectedValue = videoCodec,
                        options = listOf("h264", "h265"),
                        onValueSelected = { value ->
                            videoCodec = value
                            viewModel.updateVideoCodec(value)
                        }
                    )

                    SwitchPreference(
                        title = stringResource(id = R.string.adaptive_bitrate),
                        summary = stringResource(id = R.string.adaptive_bitrate_summary),
                        checked = videoAdaptiveBitrate,
                        onCheckedChange = { checked ->
                            videoAdaptiveBitrate = checked
                            viewModel.updateAdaptiveBitrate(checked)
                        }
                    )

                    InputPreference(
                        title = stringResource(id = R.string.keyframe_interval),
                        summary = stringResource(id = R.string.keyframe_interval_summary),
                        value = keyframeInterval,
                        onValueChange = { value ->
                            val filteredValue = value.filterNot { it.isWhitespace() }
                            keyframeInterval = filteredValue
                            viewModel.updateKeyframeInterval(filteredValue)
                        },
                        keyboardType = KeyboardType.Number
                    )

                    SwitchPreference(
                        title = stringResource(id = R.string.record_video),
                        summary = stringResource(id = R.string.record_video_summary),
                        checked = recordVideo,
                        onCheckedChange = { checked ->
                            recordVideo = checked
                            viewModel.updateRecordVideo(checked)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Audio Settings
                PreferenceCategory(title = stringResource(id = R.string.audio_settings)) {
                    SwitchPreference(
                        title = stringResource(id = R.string.enable_audio),
                        summary = stringResource(id = R.string.enable_audio_summary),
                        checked = enableAudio,
                        onCheckedChange = { checked ->
                            enableAudio = checked
                            viewModel.updateEnableAudio(checked)
                        }
                    )

                    InputPreference(
                        title = stringResource(id = R.string.audio_bitrate),
                        summary = stringResource(id = R.string.audio_bitrate_summary),
                        value = audioBitrate,
                        keyboardType = KeyboardType.Number,
                        onValueChange = { value ->
                            val filteredValue = value.filterNot { it.isWhitespace() }
                            audioBitrate = filteredValue
                            viewModel.updateAudioBitrate(filteredValue)
                        }
                    )

                    DropdownPreference(
                        title = stringResource(id = R.string.audio_sample_rate),
                        summary = stringResource(id = R.string.audio_sample_rate_summary),
                        selectedValue = audioSampleRate,
                        options = listOf("8000", "16000", "22050", "32000", "44100", "48000"),
                        onValueSelected = { value ->
                            audioSampleRate = value
                            viewModel.updateAudioSampleRate(value)
                        }
                    )

                    SwitchPreference(
                        title = stringResource(id = R.string.stereo),
                        summary = stringResource(id = R.string.stereo_summary),
                        checked = audioStereo,
                        onCheckedChange = { checked ->
                            audioStereo = checked
                            viewModel.updateAudioStereo(checked)
                        }
                    )

                    SwitchPreference(
                        title = stringResource(id = R.string.echo_cancellation),
                        summary = stringResource(id = R.string.echo_cancellation_summary),
                        checked = audioEchoCancel,
                        onCheckedChange = { checked ->
                            audioEchoCancel = checked
                            viewModel.updateAudioEchoCancel(checked)
                        }
                    )

                    SwitchPreference(
                        title = stringResource(id = R.string.noise_reduction),
                        summary = stringResource(id = R.string.noise_reduction_summary),
                        checked = audioNoiseReduction,
                        onCheckedChange = { checked ->
                            audioNoiseReduction = checked
                            viewModel.updateAudioNoiseReduction(checked)
                        }
                    )

                    DropdownPreference(
                        title = stringResource(id = R.string.audio_codec),
                        summary = stringResource(id = R.string.audio_codec_summary),
                        selectedValue = audioCodec,
                        options = listOf("aac", "opus"),
                        onValueSelected = { value ->
                            audioCodec = value
                            viewModel.updateAudioCodec(value)
                        }
                    )
                }
            }
        }
    }
}
