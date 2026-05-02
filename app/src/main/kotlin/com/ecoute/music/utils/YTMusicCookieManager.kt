package com.ecoute.music.utils

import android.content.Context
import android.content.SharedPreferences
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object YTMusicCookieManager {
    private const val PREFS = "ytmusic_auth"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_EMAIL = "email"
    private const val YTM_URL = "https://music.youtube.com"

    private val json = Json { ignoreUnknownKeys = true }

    fun saveCookie(context: Context, cookie: String) {
        prefs(context).edit().putString(KEY_COOKIE, cookie).apply()
    }

    fun getCookie(context: Context): String? =
        prefs(context).getString(KEY_COOKIE, null)

    fun saveEmail(context: Context, email: String) {
        prefs(context).edit().putString(KEY_EMAIL, email).apply()
    }

    fun getEmail(context: Context): String? =
        prefs(context).getString(KEY_EMAIL, null)

    fun isLoggedIn(context: Context) = getCookie(context) != null

    fun logout(context: Context) =
        prefs(context).edit().clear().apply()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun buildSapiSidHash(sapiSid: String, origin: String = YTM_URL): String {
        val timestamp = System.currentTimeMillis() / 1000
        val input = "$timestamp $sapiSid $origin"
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hash"
    }

    fun extractSapiSid(cookie: String): String? =
        cookie.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") }
            ?.substringAfter("=")

    @Serializable data class LikedSongsResponse(
        val contents: Contents? = null
    )
    @Serializable data class Contents(
        val singleColumnBrowseResultsRenderer: SingleColumn? = null
    )
    @Serializable data class SingleColumn(val tabs: List<Tab>? = null)
    @Serializable data class Tab(val tabRenderer: TabRenderer? = null)
    @Serializable data class TabRenderer(val content: TabContent? = null)
    @Serializable data class TabContent(
        val sectionListRenderer: SectionList? = null
    )
    @Serializable data class SectionList(val contents: List<SectionContent>? = null)
    @Serializable data class SectionContent(
        val musicShelfRenderer: MusicShelf? = null
    )
    @Serializable data class MusicShelf(val contents: List<ShelfItem>? = null)
    @Serializable data class ShelfItem(
        val musicResponsiveListItemRenderer: ListItemRenderer? = null
    )
    @Serializable data class ListItemRenderer(
        val flexColumns: List<FlexColumn>? = null,
        val thumbnail: ThumbnailWrapper? = null,
        val navigationEndpoint: NavEndpoint? = null
    )
    @Serializable data class FlexColumn(
        val musicResponsiveListItemFlexColumnRenderer: FlexColumnRenderer? = null
    )
    @Serializable data class FlexColumnRenderer(val text: TextRuns? = null)
    @Serializable data class TextRuns(val runs: List<Run>? = null)
    @Serializable data class Run(
        val text: String? = null,
        val navigationEndpoint: NavEndpoint? = null
    )
    @Serializable data class NavEndpoint(
        val watchEndpoint: WatchEndpoint? = null,
        val browseEndpoint: BrowseEndpoint? = null
    )
    @Serializable data class WatchEndpoint(@SerialName("videoId") val videoId: String? = null)
    @Serializable data class BrowseEndpoint(@SerialName("browseId") val browseId: String? = null)
    @Serializable data class ThumbnailWrapper(
        val musicThumbnailRenderer: MusicThumbnailRenderer? = null
    )
    @Serializable data class MusicThumbnailRenderer(val thumbnail: ThumbList? = null)
    @Serializable data class ThumbList(val thumbnails: List<Thumb>? = null)
    @Serializable data class Thumb(val url: String? = null)

    data class TrackInfo(
        val videoId: String,
        val title: String,
        val artist: String?,
        val thumbnailUrl: String?
    )

    suspend fun fetchLikedSongs(context: Context): List<TrackInfo> {
        val cookie = getCookie(context) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val module = com.ecoute.music.Dependencies.py.getModule("liked_songs")
                val result = module.callAttr("get_liked_songs", cookie).toString()
                json.decodeFromString<List<TrackInfoJson>>(result).map {
                    TrackInfo(it.id, it.title, it.artist, it.thumbnail)
                }
            }.onFailure { it.printStackTrace() }.getOrElse { emptyList() }
        }
    }

    @Serializable data class TrackInfoJson(
        val id: String,
        val title: String,
        val artist: String? = null,
        val thumbnail: String? = null
    )


    data class TrackInfo(
        val videoId: String,
        val title: String,
        val artist: String?,
        val thumbnailUrl: String?
    )

    suspend fun fetchLikedSongs(context: Context): List<TrackInfo> {
        val cookie = getCookie(context) ?: return emptyList()
        val sapiSid = extractSapiSid(cookie) ?: return emptyList()
        val auth = buildSapiSidHash(sapiSid)
        val tracks = mutableListOf<TrackInfo>()
        try {
            val body = """{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20241121.01.00","hl":"en"}},"browseId":"FEmusic_liked_videos"}"""
            val conn = URL("https://music.youtube.com/youtubei/v1/browse?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30&prettyPrint=false")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Cookie", cookie)
            conn.setRequestProperty("Authorization", auth)
            conn.setRequestProperty("X-Origin", "https://music.youtube.com")
            conn.setRequestProperty("Origin", "https://music.youtube.com")
            conn.setRequestProperty("Referer", "https://music.youtube.com/")
            conn.setRequestProperty("X-Goog-Visitor-Id", "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D")
            conn.outputStream.write(body.toByteArray())
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            // Parse items from musicShelfRenderer
            val items = extractVideoIds(response)
            tracks.addAll(items)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tracks
    }

    private fun extractVideoIds(response: String): List<TrackInfo> {
        val tracks = mutableListOf<TrackInfo>()
        val videoIdRegex = """"videoId"\s*:\s*"([^"]+)"""".toRegex()
        val titleRegex = """"text"\s*:\s*"([^"]+)"""".toRegex()
        // Simple extraction - find videoIds and nearby titles
        val videoIds = videoIdRegex.findAll(response).map { it.groupValues[1] }.toList()
        val titles = titleRegex.findAll(response).map { it.groupValues[1] }.toList()
        videoIds.take(200).forEachIndexed { i, id ->
            tracks.add(TrackInfo(
                videoId = id,
                title = titles.getOrNull(i * 2) ?: "Unknown",
                artist = titles.getOrNull(i * 2 + 1),
                thumbnailUrl = null
            ))
        }
        return tracks.distinctBy { it.videoId }
    }
}
