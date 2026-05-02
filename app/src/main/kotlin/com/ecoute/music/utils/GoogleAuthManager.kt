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
import java.net.URL

object GoogleAuthManager {
    const val CLIENT_ID = "858007978993-6ssuq2lq88j632hqmvdt47p3no497o08.apps.googleusercontent.com"
    private const val YOUTUBE_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
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

    @Serializable
    data class PlaylistItemsResponse(
        val items: List<PlaylistItem>? = null,
        @SerialName("nextPageToken") val nextPageToken: String? = null
    )
    @Serializable data class PlaylistItem(val snippet: Snippet? = null)
    @Serializable data class Snippet(
        val title: String? = null,
        @SerialName("resourceId") val resourceId: ResourceId? = null,
        val thumbnails: Thumbnails? = null,
        @SerialName("videoOwnerChannelTitle") val channelTitle: String? = null
    )
    @Serializable data class ResourceId(@SerialName("videoId") val videoId: String? = null)
    @Serializable data class Thumbnails(val high: Thumb? = null, val medium: Thumb? = null)
    @Serializable data class Thumb(val url: String? = null)

    suspend fun getAccessToken(context: Context, account: GoogleSignInAccount): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                GoogleAuthUtil.getToken(
                    context,
                    account.account!!,
                    "oauth2:$YOUTUBE_SCOPE"
                )
            }.getOrNull()
        }

    suspend fun fetchLikedSongs(accessToken: String): List<Song> {
        val songs = mutableListOf<Song>()
        var pageToken: String? = null
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            do {
                val url = buildString {
                    append("https://www.googleapis.com/youtube/v3/playlistItems")
                    append("?playlistId=LL&part=snippet&maxResults=50")
                    if (pageToken != null) append("&pageToken=$pageToken")
                }
                val response = runCatching {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.setRequestProperty("Authorization", "Bearer $accessToken")
                    val body = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    json.decodeFromString<PlaylistItemsResponse>(body)
                }.getOrNull() ?: break
                response.items?.forEach { item ->
                    val videoId = item.snippet?.resourceId?.videoId ?: return@forEach
                    val title = item.snippet.title ?: return@forEach
                    songs.add(Song(
                        id = videoId,
                        title = title,
                        artistsText = item.snippet.channelTitle,
                        durationText = null,
                        thumbnailUrl = item.snippet.thumbnails?.high?.url
                            ?: item.snippet.thumbnails?.medium?.url,
                        likedAt = now
                    ))
                }
                pageToken = response.nextPageToken
            } while (pageToken != null)
        }
        return songs
    }
}
