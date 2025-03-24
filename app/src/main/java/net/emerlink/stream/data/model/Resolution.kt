package net.emerlink.stream.data.model

import android.util.Size

data class Resolution(
    val width: Int = 1920,
    val height: Int = 1080,
) {
    companion object {
        fun parseFromSize(size: Size?): Resolution = size?.let { Resolution(it.width, it.height) } ?: Resolution()
    }
}
