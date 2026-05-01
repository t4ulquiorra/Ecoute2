package com.ecoute.music.utils.cipher

import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CipherWebView private constructor(
    context: Context,
    private val playerJs: String,
    private val sigInfo: FunctionNameExtractor.SigFunctionInfo?,
    private val nFuncInfo: FunctionNameExtractor.NFunctionInfo?,
    private val initContinuation: Continuation<CipherWebView>,
) {
    private val webView = WebView(context)
    private var sigContinuation: Continuation<String>? = null
    private var nContinuation: Continuation<String>? = null

    @Volatile var nFunctionAvailable: Boolean = false
        private set
    @Volatile var sigFunctionAvailable: Boolean = false
        private set
    @Volatile var discoveredNFuncName: String? = null
        private set
    @Volatile var usingHardcodedMode: Boolean = false
        private set

    init {
        val settings = webView.settings
        @Suppress("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true
        settings.allowFileAccess = true
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = true
        settings.blockNetworkLoads = true

        webView.addJavascriptInterface(this, JS_INTERFACE)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Log.e(TAG, "JS: ${m.message()}")
                }
                return super.onConsoleMessage(m)
            }
        }
    }

    private fun loadPlayerJsFromFile() {
        val sigFuncName = sigInfo?.name
        val nFuncName = nFuncInfo?.name
        val nArrayIdx = nFuncInfo?.arrayIndex
        val isHardcoded = sigInfo?.isHardcoded == true || nFuncInfo?.isHardcoded == true
        usingHardcodedMode = isHardcoded

        val exports = buildList {
            if (sigFuncName != null) {
                val sigConstArgs = sigInfo.constantArgs
                val preprocessFunc = sigInfo.preprocessFunc
                val preprocessArgs = sigInfo.preprocessArgs
                if (!sigConstArgs.isNullOrEmpty() && preprocessFunc != null && !preprocessArgs.isNullOrEmpty()) {
                    val mainArgsStr = sigConstArgs.joinToString(", ")
                    val prepArgsStr = preprocessArgs.joinToString(", ")
                    add("window._cipherSigFunc = function(sig) { return $sigFuncName($mainArgsStr, $preprocessFunc($prepArgsStr, sig)); };")
                } else if (!sigConstArgs.isNullOrEmpty()) {
                    val argsStr = sigConstArgs.joinToString(", ")
                    add("window._cipherSigFunc = function(sig) { return $sigFuncName($argsStr, sig); };")
                } else {
                    add("window._cipherSigFunc = typeof $sigFuncName !== 'undefined' ? $sigFuncName : null;")
                }
            }
            if (nFuncName != null) {
                val nConstArgs = nFuncInfo.constantArgs
                if (!nConstArgs.isNullOrEmpty()) {
                    val argsStr = nConstArgs.joinToString(", ")
                    add("window._nTransformFunc = function(n) { return $nFuncName($argsStr, n); };")
                } else {
                    val nExpr = if (nArrayIdx != null) "$nFuncName[$nArrayIdx]" else nFuncName
                    add("window._nTransformFunc = typeof $nFuncName !== 'undefined' ? $nExpr : null;")
                }
            }
        }

        val modifiedJs = if (exports.isNotEmpty()) {
            val exportCode = "; " + exports.joinToString(" ")
            val modified = playerJs.replace("})(_yt_player);", "$exportCode })(_yt_player);")
            if (modified == playerJs) playerJs + "\n" + exportCode else modified
        } else playerJs

        val cacheDir = File(webView.context.cacheDir, "cipher")
        cacheDir.mkdirs()
        val playerJsFile = File(cacheDir, "player.js")
        playerJsFile.writeText(modifiedJs)

        val html = buildDiscoveryHtml()
        webView.loadDataWithBaseURL(
            "file://${cacheDir.absolutePath}/",
            html, "text/html", "utf-8", null
        )
    }

    private fun buildDiscoveryHtml(): String = """<!DOCTYPE html>
<html><head><script>
function deobfuscateSig(funcName, constantArg, obfuscatedSig) {
    try {
        var func = window._cipherSigFunc;
        if (typeof func !== 'function') { CipherBridge.onSigError("Sig func not found"); return; }
        var result;
        if (func.length === 1) { result = func(obfuscatedSig); }
        else if (constantArg !== null && constantArg !== undefined) { result = func(constantArg, obfuscatedSig); }
        else { result = func(obfuscatedSig); }
        if (result === undefined || result === null) { CipherBridge.onSigError("Function returned null/undefined"); return; }
        CipherBridge.onSigResult(String(result));
    } catch (error) { CipherBridge.onSigError(error + "\n" + (error.stack || "")); }
}

function transformN(nValue) {
    try {
        var func = window._nTransformFunc;
        if (typeof func !== 'function') { CipherBridge.onNError("N-transform func not available"); return; }
        var result = func(nValue);
        if (result === undefined || result === null) { CipherBridge.onNError("N-transform returned null/undefined"); return; }
        var resultStr = String(result);
        CipherBridge.onNResult(resultStr);
    } catch (error) { CipherBridge.onNError(error + "\n" + (error.stack || "")); }
}

function discoverAndInit() {
    var nFuncName = "";
    var sigFuncName = "";
    var info = "";

    if (typeof window._cipherSigFunc === 'function') {
        sigFuncName = "exported_sig_func";
    }

    if (typeof window._nTransformFunc === 'function') {
        try {
            var testInput = "KdrqFlzJXl9EcCwlmEy";
            var testResult = window._nTransformFunc(testInput);
            if (typeof testResult === 'string' && testResult !== testInput && testResult.length >= 5) {
                if (/^[a-zA-Z0-9_-]+${'$'}/.test(testResult)) {
                    nFuncName = "exported_n_func";
                    info = "export_valid,test=" + testResult.substring(0, 20);
                } else {
                    window._nTransformFunc = null;
                    info = "export_bad_chars";
                }
            } else {
                window._nTransformFunc = null;
                info = "export_bad_result";
            }
        } catch(e) {
            window._nTransformFunc = null;
            info = "export_threw:" + e;
        }
    }

    if (!nFuncName) {
        try {
            var testInput = "T2Xw3pWQ_Wk0xbOg";
            var keys = Object.getOwnPropertyNames(window);
            var tested = 0;
            for (var i = 0; i < keys.length; i++) {
                try {
                    var key = keys[i];
                    if (key.startsWith("webkit") || key.startsWith("on") ||
                        key === "CipherBridge" || key === "_cipherSigFunc" ||
                        key === "_nTransformFunc" || key === "window" || key === "self") continue;
                    var fn = window[key];
                    if (typeof fn !== 'function' || fn.length !== 1) continue;
                    tested++;
                    var result = fn(testInput);
                    if (typeof result === 'string' && result !== testInput && result.length >= 5) {
                        if (/^[a-zA-Z0-9_-]+${'$'}/.test(result)) {
                            window._nTransformFunc = fn;
                            nFuncName = key;
                            info = "brute_force:tested=" + tested;
                            break;
                        }
                    }
                } catch(e) {}
            }
            if (!nFuncName) info = "brute_force_failed:tested=" + tested;
        } catch(e) {
            info = "brute_force_error:" + e;
        }
    }

    CipherBridge.onDiscoveryDone(sigFuncName, nFuncName, info);
    CipherBridge.onPlayerJsLoaded();
}
</script>
<script src="player.js"
    onload="discoverAndInit()"
    onerror="CipherBridge.onPlayerJsError('Failed to load player.js from file')">
</script>
</head><body></body></html>"""

    @JavascriptInterface
    fun logDebug(message: String) { Log.d(TAG, "JS: $message") }

    @JavascriptInterface
    fun onDiscoveryDone(sigFuncName: String, nFuncName: String, info: String) {
        sigFunctionAvailable = sigFuncName.isNotEmpty()
        if (nFuncName.isNotEmpty()) {
            discoveredNFuncName = nFuncName
            nFunctionAvailable = true
        } else {
            nFunctionAvailable = false
        }
        Log.d(TAG, "Discovery done: sig=$sigFuncName, n=$nFuncName, info=$info")
    }

    @JavascriptInterface
    fun onNDiscoveryDone(funcName: String, info: String) {
        if (funcName.isNotEmpty()) { discoveredNFuncName = funcName; nFunctionAvailable = true }
    }

    @JavascriptInterface
    fun onPlayerJsLoaded() {
        Log.d(TAG, "Player.js loaded: sig=$sigFunctionAvailable, n=$nFunctionAvailable")
        initContinuation.resume(this)
    }

    @JavascriptInterface
    fun onPlayerJsError(error: String) {
        Log.e(TAG, "Player.js load failed: $error")
        initContinuation.resumeWithException(CipherException("Player JS load failed: $error"))
    }

    suspend fun deobfuscateSignature(obfuscatedSig: String): String {
        if (sigInfo == null) throw CipherException("Signature function info not available")
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                sigContinuation = cont
                val constArgJs = if (sigInfo.constantArg != null) "${sigInfo.constantArg}" else "null"
                webView.evaluateJavascript("deobfuscateSig('${sigInfo.name}', $constArgJs, '${escapeJsString(obfuscatedSig)}')", null)
            }
        }
    }

    @JavascriptInterface
    fun onSigResult(result: String) { sigContinuation?.resume(result); sigContinuation = null }

    @JavascriptInterface
    fun onSigError(error: String) { sigContinuation?.resumeWithException(CipherException(error)); sigContinuation = null }

    suspend fun transformN(nValue: String): String {
        if (!nFunctionAvailable) throw CipherException("N-transform function not discovered")
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                nContinuation = cont
                webView.evaluateJavascript("transformN('${escapeJsString(nValue)}')", null)
            }
        }
    }

    @JavascriptInterface
    fun onNResult(result: String) { nContinuation?.resume(result); nContinuation = null }

    @JavascriptInterface
    fun onNError(error: String) { nContinuation?.resumeWithException(CipherException(error)); nContinuation = null }

    fun close() {
        webView.clearHistory()
        webView.clearCache(true)
        webView.loadUrl("about:blank")
        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }

    private fun escapeJsString(s: String): String = s
        .replace("\\", "\\\\").replace("'", "\\'")
        .replace("\"", "\\\"").replace("\n", "\\n")
        .replace("\r", "\\r").replace("\t", "\\t")

    companion object {
        private const val TAG = "Ecoute_CipherWebView"
        private const val JS_INTERFACE = "CipherBridge"

        suspend fun create(
            context: Context,
            playerJs: String,
            sigInfo: FunctionNameExtractor.SigFunctionInfo?,
            nFuncInfo: FunctionNameExtractor.NFunctionInfo? = null,
        ): CipherWebView = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val wv = CipherWebView(context, playerJs, sigInfo, nFuncInfo, cont)
                wv.loadPlayerJsFromFile()
            }
        }
    }
}

class CipherException(message: String) : Exception(message)
