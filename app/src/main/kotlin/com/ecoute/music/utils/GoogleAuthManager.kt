package com.ecoute.music.utils

import android.content.Context
import android.content.Intent
import com.ecoute.music.models.Song
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

object GoogleAuthManager {
    const val CLIENT_ID = "858007978993-6ssuq2lq88j632hqmvdt47p3no497o08.apps.googleusercontent.com"
    private const val YOUTUBE_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
    private const val INNERTUBE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
    private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private val json = Json { ignoreUnknownKeys = true }

    fun signInOptions() = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(YOUTUBE_SCOPE))
        .build()

    fun getSignInIntent(context: Context): Intent =
        GoogleSignIn.getClient(context, signInOptions()).signInIntent

    fun getSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)?.takeIf {
            GoogleSignIn.hasPermissions(it, Scope(YOUTUBE_SCOPE))
        }

    fun signOut(context: Context) =
        GoogleSignIn.getClient(context, signInOptions()).signOut()

    suspend fun getAccessToken(context: Context, account: GoogleSignInAccount): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                GoogleAuthUtil.getToken(
                    context,
                    account.account!!,
                    "oauth2:$YOUTUBE_SCOPE"
                )
            }.onFailure {
                android.util.Log.e("GoogleAuth", "getToken failed: ${it::class.simpleName}: ${it.message}")
            }.getOrNull()
        }

    private fun innertubeBody(browseId: String, continuation: String? = null): String {
        return if (continuation != null) {
            """{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20241121.01.00","hl":"en"}},"continuation":"$continuation"}"""
        } else {
            """{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20241121.01.00","hl":"en"}},"browseId":"$browseId"}"""
        }
    }

    private suspend fun innertubeRequest(accessToken: String, browseId: String, continuation: String? = null): JsonObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL("$INNERTUBE_URL&key=$API_KEY").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.setRequestProperty("X-Goog-AuthUser", "0")
                conn.setRequestProperty("Origin", "https://music.youtube.com")
                conn.outputStream.write(innertubeBody(browseId, continuation).toByteArray())
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                android.util.Log.d("GoogleAuth", "Response(${body.length}): ${body.take(500)}")
                json.parseToJsonElement(body).jsonObject
            }.onFailure {
                android.util.Log.e("GoogleAuth", "innertubeRequest failed: ${it.message}")
            }.getOrNull()
        }

    private fun JsonElement.str(key: String) = runCatching {
        jsonObject[key]?.jsonPrimitive?.content
    }.getOrNull()

    private fun JsonObject.arr(key: String) = runCatching {
        get(key)?.jsonArray
    }.getOrNull()

    private fun JsonElement.obj() = runCatching { jsonObject }.getOrNull()

    private fun extractText(el: JsonElement?): String? = runCatching {
        el?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: el?.jsonObject?.get("simpleText")?.jsonPrimitive?.content
    }.getOrNull()

    private fun extractVideoId(renderer: JsonObject): String? = runCatching {
        renderer["overlay"]?.jsonObject
            ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("musicPlayButtonRenderer")?.jsonObject
            ?.get("playNavigationEndpoint")?.jsonObject
            ?.get("watchEndpoint")?.jsonObject
            ?.get("videoId")?.jsonPrimitive?.content
            ?: renderer["navigationEndpoint"]?.jsonObject
                ?.get("watchEndpoint")?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.content
    }.getOrNull()

    private fun extractThumbnail(renderer: JsonObject): String? = runCatching {
        renderer["thumbnail"]?.jsonObject
            ?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray
            ?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.content
    }.getOrNull()

    data class YTMPlaylist(
        val id: String,
        val title: String,
        val thumbnailUrl: String?
    )

    data class YTMArtist(
        val id: String,
        val name: String,
        val thumbnailUrl: String?
    )

    suspend fun fetchLikedSongs(accessToken: String): List<Song> {
        val songs = mutableListOf<Song>()
        val now = System.currentTimeMillis()
        var continuation: String? = null
        var isFirst = true

        withContext(Dispatchers.IO) {
            do {
                val response = if (isFirst) {
                    isFirst = false
                    innertubeRequest(accessToken, "FEmusic_liked_videos")
                } else {
                    innertubeRequest(accessToken, "", continuation)
                } ?: break

                continuation = null

                // Extract items from musicShelfRenderer contents
                val contents = runCatching {
                    response["contents"]?.jsonObject
                        ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                        ?.get("tabs")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("tabRenderer")?.jsonObject
                        ?.get("content")?.jsonObject
                        ?.get("sectionListRenderer")?.jsonObject
                        ?.get("contents")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("musicShelfRenderer")?.jsonObject
                        ?.get("contents")?.jsonArray
                }.getOrNull() ?: runCatching {
                    // continuation response format
                    response["continuationContents"]?.jsonObject
                        ?.get("musicShelfContinuation")?.jsonObject
                        ?.get("contents")?.jsonArray
                }.getOrNull() ?: break

                // Extract continuation token
                continuation = runCatching {
                    response["continuationContents"]?.jsonObject
                        ?.get("musicShelfContinuation")?.jsonObject
                        ?.get("continuations")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("nextContinuationData")?.jsonObject
                        ?.get("continuation")?.jsonPrimitive?.content
                }.getOrNull()

                contents.forEach { item ->
                    val renderer = runCatching {
                        item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject
                    }.getOrNull() ?: return@forEach

                    val flexColumns = renderer["flexColumns"]?.jsonArray ?: return@forEach
                    val title = extractText(
                        flexColumns.getOrNull(0)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")
                    ) ?: return@forEach

                    val artist = extractText(
                        flexColumns.getOrNull(1)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")
                    )

                    val videoId = runCatching {
                        renderer["overlay"]?.jsonObject
                            ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                            ?.get("content")?.jsonObject
                            ?.get("musicPlayButtonRenderer")?.jsonObject
                            ?.get("playNavigationEndpoint")?.jsonObject
                            ?.get("watchEndpoint")?.jsonObject
                            ?.get("videoId")?.jsonPrimitive?.content
                    }.getOrNull() ?: return@forEach

                    val thumbnail = extractThumbnail(renderer)

                    songs.add(Song(
                        id = videoId,
                        title = title,
                        artistsText = artist,
                        durationText = null,
                        thumbnailUrl = thumbnail,
                        likedAt = now
                    ))
                }
            } while (continuation != null)
        }
        return songs
    }

    suspend fun fetchPlaylists(accessToken: String): List<YTMPlaylist> {
        val playlists = mutableListOf<YTMPlaylist>()
        val response = innertubeRequest(accessToken, "FEmusic_liked_playlists") ?: return playlists

        runCatching {
            val items = response["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("gridRenderer")?.jsonObject
                ?.get("items")?.jsonArray ?: return@runCatching

            items.drop(1).forEach { item -> // drop first which is "New playlist"
                val renderer = item.jsonObject["musicTwoRowItemRenderer"]?.jsonObject ?: return@forEach
                val id = renderer["navigationEndpoint"]?.jsonObject
                    ?.get("browseEndpoint")?.jsonObject
                    ?.get("browseId")?.jsonPrimitive?.content ?: return@forEach
                val title = extractText(renderer["title"]) ?: return@forEach
                val thumbnail = runCatching {
                    renderer["thumbnailRenderer"]?.jsonObject
                        ?.get("musicThumbnailRenderer")?.jsonObject
                        ?.get("thumbnail")?.jsonObject
                        ?.get("thumbnails")?.jsonArray
                        ?.lastOrNull()?.jsonObject
                        ?.get("url")?.jsonPrimitive?.content
                }.getOrNull()
                playlists.add(YTMPlaylist(id.removePrefix("VL"), title, thumbnail))
            }
        }.onFailure { android.util.Log.e("GoogleAuth", "fetchPlaylists failed: ${it.message}") }

        return playlists
    }

    suspend fun fetchArtists(accessToken: String): List<YTMArtist> {
        val artists = mutableListOf<YTMArtist>()
        val response = innertubeRequest(accessToken, "FEmusic_library_corpus_artists") ?: return artists

        runCatching {
            val items = response["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("musicShelfRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: return@runCatching

            items.forEach { item ->
                val renderer = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: return@forEach
                val id = renderer["navigationEndpoint"]?.jsonObject
                    ?.get("browseEndpoint")?.jsonObject
                    ?.get("browseId")?.jsonPrimitive?.content ?: return@forEach
                val name = extractText(
                    renderer["flexColumns"]?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                        ?.get("text")
                ) ?: return@forEach
                val thumbnail = extractThumbnail(renderer)
                artists.add(YTMArtist(id, name, thumbnail))
            }
        }.onFailure { android.util.Log.e("GoogleAuth", "fetchArtists failed: ${it.message}") }

        return artists
    }
}
