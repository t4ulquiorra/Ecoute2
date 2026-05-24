package com.ecoute.music.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ecoute.music.preferences.AccountPreferences
import com.ecoute.music.ui.screens.Route
import com.ecoute.providers.innertube.Innertube
import com.ecoute.providers.innertube.requests.accountInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Route
@Composable
fun LoginScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var hasCompletedLogin by remember { mutableStateOf(false) }
    var webView: WebView? = null

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { webViewContext ->
            WebView(webViewContext).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loadUrl("javascript:Android.onRetrieveVisitorData(window.yt?.config_?.VISITOR_DATA)")
                        loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt?.config_?.DATASYNC_ID)")
                        if (url?.startsWith("https://music.youtube.com") == true && !hasCompletedLogin) {
                            val cookie = CookieManager.getInstance().getCookie(url)
                            if (!cookie.isNullOrEmpty()) {
                                hasCompletedLogin = true
                                AccountPreferences.innerTubeCookie = cookie
                                coroutineScope.launch {
                                    delay(500)
                                    Innertube.cookie = cookie
                                    Innertube.accountInfo().onSuccess { info ->
                                        AccountPreferences.accountName = info.name
                                        AccountPreferences.accountEmail = info.email.orEmpty()
                                        AccountPreferences.accountChannelHandle = info.channelHandle.orEmpty()
                                        webView?.apply {
                                            stopLoading()
                                            clearHistory()
                                            clearCache(true)
                                            clearFormData()
                                        }
                                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        context.startActivity(intent)
                                        Runtime.getRuntime().exit(0)
                                    }.onFailure {
                                        hasCompletedLogin = false
                                    }
                                }
                            }
                        }
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onRetrieveVisitorData(newVisitorData: String?) {
                        if (!newVisitorData.isNullOrEmpty()) {
                            AccountPreferences.visitorData = newVisitorData
                            Innertube.visitorData = newVisitorData
                        }
                    }
                    @JavascriptInterface
                    fun onRetrieveDataSyncId(newDataSyncId: String?) {
                        if (!newDataSyncId.isNullOrEmpty()) {
                            val syncId = newDataSyncId.substringBefore("||")
                            AccountPreferences.dataSyncId = syncId
                            Innertube.dataSyncId = syncId
                        }
                    }
                }, "Android")
                webView = this
                loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
            }
        }
    )

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}
