package com.ecoute.providers.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnail(
    val url: String,
    val height: Int?,
    val width: Int?
) {
    fun size(size: Int) = when {
        url.startsWith("https://lh3.googleusercontent.com") -> "$url-w$size-h$size"
        url.startsWith("https://yt3.ggpht.com") -> "$url-w$size-h$size-s$size"
        url.startsWith("https://yt3.googleusercontent.com") -> "$url-w$size-h$size-s$size"
        url.contains("ytimg.com") -> url.replace(Regex("=w[0-9]+-h[0-9]+"), "=w$size-h$size")
        else -> url
    }
}
