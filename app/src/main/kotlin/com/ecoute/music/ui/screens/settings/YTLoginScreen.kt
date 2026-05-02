package com.ecoute.music.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ecoute.music.ui.screens.Route
import com.ecoute.music.utils.YTMusicCookieManager

@Route
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YTLoginScreen(onLoginSuccess: () -> Unit = {}) {
    val ctx = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            if (url?.contains("music.youtube.com") == true &&
                                !url.contains("accounts.google.com")) {
                                val cookie = CookieManager.getInstance()
                                    .getCookie("https://music.youtube.com")
                                if (!cookie.isNullOrBlank() &&
                                    (cookie.contains("SAPISID") || cookie.contains("__Secure-3PAPISID"))) {
                                    YTMusicCookieManager.saveCookie(ctx, cookie)
                                    onLoginSuccess()
                                }
                            }
                        }
                    }
                    loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
        }
    }
}
