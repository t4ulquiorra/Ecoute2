package com.ecoute.music.utils.cipher

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CipherDeobfuscator {
    private const val TAG = "Ecoute_CipherDeobfusc"

    lateinit var appContext: Context
        private set

    private var cipherWebView: CipherWebView? = null
    private var currentPlayerHash: String? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        Log.d(TAG, "CipherDeobfuscator initialized")
    }

    suspend fun transformNParamInUrl(url: String): String {
        return try {
            val nMatch = Regex("[?&]n=([^&]+)").find(url) ?: return url
            val nValue = Uri.decode(nMatch.groupValues[1])
            val webView = getOrCreateWebView(forceRefresh = false) ?: return url
            if (!webView.nFunctionAvailable) return url
            val transformedN = webView.transformN(nValue)
            url.replaceFirst(Regex("([?&])n=[^&]+"), "$1n=${Uri.encode(transformedN)}")
        } catch (e: Exception) {
            Log.e(TAG, "transformNParamInUrl failed, returning original URL", e)
            try {
                PlayerJsFetcher.invalidateCache()
                closeWebView()
                val nMatch = Regex("[?&]n=([^&]+)").find(url) ?: return url
                val nValue = Uri.decode(nMatch.groupValues[1])
                val webView = getOrCreateWebView(forceRefresh = true) ?: return url
                if (!webView.nFunctionAvailable) return url
                val transformedN = webView.transformN(nValue)
                url.replaceFirst(Regex("([?&])n=[^&]+"), "$1n=${Uri.encode(transformedN)}")
            } catch (retryE: Exception) {
                Log.e(TAG, "transformNParamInUrl retry also failed, returning original URL", retryE)
                url
            }
        }
    }

    private suspend fun getOrCreateWebView(forceRefresh: Boolean): CipherWebView? {
        if (!forceRefresh && cipherWebView != null) return cipherWebView
        if (cipherWebView != null) closeWebView()
        val result = PlayerJsFetcher.getPlayerJs(forceRefresh) ?: return null
        val (playerJs, hash) = result
        val analysis = FunctionNameExtractor.analyzePlayerJs(playerJs, knownHash = hash)
        if (analysis.sigInfo == null) {
            Log.e(TAG, "Could not extract sig function info from player JS")
            return null
        }
        val webView = CipherWebView.create(
            context = appContext,
            playerJs = playerJs,
            sigInfo = analysis.sigInfo,
            nFuncInfo = analysis.nFuncInfo,
        )
        cipherWebView = webView
        currentPlayerHash = hash
        return webView
    }

    private suspend fun closeWebView() {
        withContext(Dispatchers.Main) { cipherWebView?.close() }
        cipherWebView = null
        currentPlayerHash = null
    }
}
