package net.emerlink.stream.data.preferences

object PreferenceKeys {
    // Stream settings
    const val STREAM_PROTOCOL = "stream_protocol"
    const val STREAM_PROTOCOL_DEFAULT = "rtmp"

    const val STREAM_ADDRESS = "stream_address"
    const val STREAM_ADDRESS_DEFAULT = "127.0.0.1"

    const val STREAM_PORT = "stream_port"
    const val STREAM_PORT_DEFAULT = "1935"

    const val STREAM_PATH = "stream_path"
    const val STREAM_PATH_DEFAULT = "live"

    const val STREAM_USE_TCP = "stream_use_tcp"
    const val STREAM_USE_TCP_DEFAULT = true

    const val STREAM_USERNAME = "stream_username"
    const val STREAM_USERNAME_DEFAULT = ""

    const val STREAM_PASSWORD = "stream_password"
    const val STREAM_PASSWORD_DEFAULT = ""

    const val STREAM_SELF_SIGNED_CERT = "stream_self_signed_cert"
    const val STREAM_SELF_SIGNED_CERT_DEFAULT = false

    const val STREAM_CERTIFICATE = "stream_certificate"
    const val STREAM_CERTIFICATE_DEFAULT = ""

    const val STREAM_CERTIFICATE_PASSWORD = "stream_certificate_password"
    const val STREAM_CERTIFICATE_PASSWORD_DEFAULT = ""

    const val STREAM_VIDEO = "stream_video"
    const val STREAM_VIDEO_DEFAULT = true

    const val STREAM_KEY = "stream_key"
    const val STREAM_KEY_DEFAULT = ""

    // Video settings
    const val VIDEO_RESOLUTION = "video_resolution"
    const val VIDEO_RESOLUTION_DEFAULT = "1920х1080"

    const val VIDEO_FPS = "video_fps"
    const val VIDEO_FPS_DEFAULT = "30"

    const val VIDEO_BITRATE = "video_bitrate"
    const val VIDEO_BITRATE_DEFAULT = "2500"

    const val VIDEO_CODEC = "video_codec"
    const val VIDEO_CODEC_DEFAULT = "h264"

    const val VIDEO_ADAPTIVE_BITRATE = "video_adaptive_bitrate"
    const val VIDEO_ADAPTIVE_BITRATE_DEFAULT = true

    const val VIDEO_SOURCE = "video_source"
    const val VIDEO_SOURCE_DEFAULT = "camera2"
    const val VIDEO_SOURCE_UVC = "uvc"

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
    const val AUDIO_CODEC_DEFAULT = "aac"

    // Overlay settings
    const val TEXT_OVERLAY = "text_overlay"
    const val TEXT_OVERLAY_DEFAULT = ""

    // User settings
    const val UID = "uid"
    const val UID_DEFAULT = "emerlink-stream"

    // First run
    const val FIRST_RUN = "first_run"

    // Advanced video settings
    const val VIDEO_KEYFRAME_INTERVAL = "video_keyframe_interval"
    const val VIDEO_KEYFRAME_INTERVAL_DEFAULT = "2"

    const val VIDEO_PROFILE = "video_profile"
    const val VIDEO_PROFILE_DEFAULT = "baseline"

    const val VIDEO_LEVEL = "video_level"
    const val VIDEO_LEVEL_DEFAULT = "3.1"

    const val VIDEO_BITRATE_MODE = "video_bitrate_mode"
    const val VIDEO_BITRATE_MODE_DEFAULT = "vbr" // vbr, cbr, cq

    const val VIDEO_QUALITY = "video_quality"
    const val VIDEO_QUALITY_DEFAULT = "medium" // fastest, fast, medium, slow, slowest

    // Network settings
    const val NETWORK_BUFFER_SIZE = "network_buffer_size"
    const val NETWORK_BUFFER_SIZE_DEFAULT = "0" // 0 = auto

    const val NETWORK_TIMEOUT = "network_timeout"
    const val NETWORK_TIMEOUT_DEFAULT = "5000" // ms

    const val NETWORK_RECONNECT = "network_reconnect"
    const val NETWORK_RECONNECT_DEFAULT = true

    const val NETWORK_RECONNECT_DELAY = "network_reconnect_delay"
    const val NETWORK_RECONNECT_DELAY_DEFAULT = "3000" // ms

    const val NETWORK_MAX_RECONNECT_ATTEMPTS = "network_max_reconnect_attempts"
    const val NETWORK_MAX_RECONNECT_ATTEMPTS_DEFAULT = "5"

    // Stability settings
    const val STABILITY_LOW_LATENCY = "stability_low_latency"
    const val STABILITY_LOW_LATENCY_DEFAULT = false

    const val STABILITY_HARDWARE_ROTATION = "stability_hardware_rotation"
    const val STABILITY_HARDWARE_ROTATION_DEFAULT = true

    const val STABILITY_DYNAMIC_FPS = "stability_dynamic_fps"
    const val STABILITY_DYNAMIC_FPS_DEFAULT = false

    // New constants
    const val SCREEN_ORIENTATION = "screen_orientation"
    const val SCREEN_ORIENTATION_DEFAULT = "landscape"
}
