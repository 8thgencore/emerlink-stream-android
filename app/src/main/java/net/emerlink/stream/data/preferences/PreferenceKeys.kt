package net.emerlink.stream.data.preferences

object PreferenceKeys {
    // Stream settings
    const val STREAM_PROTOCOL = "stream_protocol"
    const val STREAM_ADDRESS = "stream_address"
    const val STREAM_PORT = "stream_port"
    const val STREAM_PATH = "stream_path"
    const val STREAM_USE_TCP = "stream_use_tcp"
    const val STREAM_USERNAME = "stream_username"
    const val STREAM_PASSWORD = "stream_password"
    const val STREAM_SELF_SIGNED_CERT = "stream_self_signed_cert"
    const val STREAM_CERTIFICATE = "stream_certificate"
    const val STREAM_CERTIFICATE_PASSWORD = "stream_certificate_password"
    const val STREAM_VIDEO = "stream_video"
    const val STREAM_KEY = "stream_key"

    // Video settings
    const val SCREEN_ORIENTATION = "screen_orientation"
    const val SCREEN_ORIENTATION_DEFAULT = "landscape"

    const val VIDEO_RESOLUTION = "video_resolution"
    const val VIDEO_RESOLUTION_DEFAULT = "1920х1080"

    const val VIDEO_FPS = "video_fps"
    const val VIDEO_FPS_DEFAULT = "30"

    const val VIDEO_BITRATE = "video_bitrate"
    const val VIDEO_BITRATE_DEFAULT = "2500"

    const val VIDEO_CODEC = "video_codec"
    const val VIDEO_CODEC_DEFAULT = "H264"

    const val VIDEO_ADAPTIVE_BITRATE = "video_adaptive_bitrate"
    const val VIDEO_ADAPTIVE_BITRATE_DEFAULT = true

    const val VIDEO_SOURCE = "video_source"
    const val VIDEO_SOURCE_DEFAULT = "camera2"

    const val RECORD_VIDEO = "record_video"
    const val RECORD_VIDEO_DEFAULT = false

    // Audio settings
    const val ENABLE_AUDIO = "enable_audio"
    const val ENABLE_AUDIO_DEFAULT = true

    const val AUDIO_BITRATE = "audio_bitrate"
    const val AUDIO_BITRATE_DEFAULT = "128"

    const val AUDIO_SAMPLE_RATE = "audio_sample_rate"
    const val AUDIO_SAMPLE_RATE_DEFAULT = "44100"

    const val AUDIO_STEREO = "audio_stereo"
    const val AUDIO_STEREO_DEFAULT = true

    const val AUDIO_ECHO_CANCEL = "audio_echo_cancel"
    const val AUDIO_ECHO_CANCEL_DEFAULT = false

    const val AUDIO_NOISE_REDUCTION = "audio_noise_reduction"
    const val AUDIO_NOISE_REDUCTION_DEFAULT = false

    const val AUDIO_CODEC = "audio_codec"
    const val AUDIO_CODEC_DEFAULT = "AAC"

    // First run
    const val FIRST_RUN = "first_run"

    // Advanced video settings
    const val VIDEO_KEYFRAME_INTERVAL = "video_keyframe_interval"
    const val VIDEO_KEYFRAME_INTERVAL_DEFAULT = "2"
}
