package com.ecoute.music.utils.cipher

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object PlayerJsFetcher {
    private const val TAG = "Ecoute_CipherFetcher"
    private const val IFRAME_API_URL = "https://www.youtube.com/iframe_api"
    private const val PLAYER_JS_URL_TEMPLATE = "https://www.youtube.com/s/player/%s/player_ias.vflset/en_GB/base.js"
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L

    private val PLAYER_HASH_REGEX = Regex("""\\?/s\\?/player\\?/([a-zA-Z0-9_-]+)\\?/""")

    private fun getCacheDir(): File = File(CipherDeobfuscator.appContext.filesDir, "cipher_cache")
    private fun getCacheFile(hash: String): File = File(getCacheDir(), "player_$hash.js")
    private fun getHashFile(): File = File(getCacheDir(), "current_hash.txt")

    suspend fun getPlayerJs(forceRefresh: Boolean = false): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = getCacheDir()
            if (!cacheDir.exists()) cacheDir.mkdirs()
            if (!forceRefresh) {
                val cached = readFromCache()
                if (cached != null) return@withContext cached
            }
            val hash = fetchPlayerHash() ?: return@withContext null
            val playerJs = downloadPlayerJs(hash) ?: return@withContext null
            writeToCache(hash, playerJs)
            Pair(playerJs, hash)
        } catch (e: Exception) {
            Log.e(TAG, "getPlayerJs failed", e)
            null
        }
    }

    fun invalidateCache() {
        try {
            getCacheDir().listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "invalidateCache failed", e)
        }
    }

    private fun readFromCache(): Pair<String, String>? {
        return try {
            val hashFile = getHashFile()
            if (!hashFile.exists()) return null
            val hashData = hashFile.readText().split("\n")
            if (hashData.size < 2) return null
            val hash = hashData[0]
            val timestamp = hashData[1].toLongOrNull() ?: return null
            if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) return null
            val cacheFile = getCacheFile(hash)
            if (!cacheFile.exists()) return null
            val playerJs = cacheFile.readText()
            if (playerJs.isEmpty()) return null
            Pair(playerJs, hash)
        } catch (e: Exception) {
            null
        }
    }

    private fun writeToCache(hash: String, playerJs: String) {
        try {
            val cacheDir = getCacheDir()
            cacheDir.listFiles()?.filter { it.name.startsWith("player_") }?.forEach { it.delete() }
            getCacheFile(hash).writeText(playerJs)
            getHashFile().writeText("$hash\n${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "writeToCache failed", e)
        }
    }

    private fun fetchPlayerHash(): String? {
        return try {
            val conn = URL(IFRAME_API_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode != 200) { conn.disconnect(); return null }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            PLAYER_HASH_REGEX.find(body)?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.e(TAG, "fetchPlayerHash failed", e)
            null
        }
    }

    private fun downloadPlayerJs(hash: String): String? {
        return try {
            val conn = URL(PLAYER_JS_URL_TEMPLATE.format(hash)).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            if (conn.responseCode != 200) { conn.disconnect(); return null }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            body
        } catch (e: Exception) {
            Log.e(TAG, "downloadPlayerJs failed", e)
            null
        }
    }
}
