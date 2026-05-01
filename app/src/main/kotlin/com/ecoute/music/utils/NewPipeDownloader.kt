package com.ecoute.music.utils

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple HTTP downloader for NewPipe Extractor.
 * Uses plain HttpURLConnection — no extra dependencies needed.
 */
object NewPipeDownloader : Downloader() {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    override fun execute(request: Request): Response {
        val url = URL(request.url())
        val conn = url.openConnection() as HttpURLConnection

        conn.requestMethod = request.httpMethod()
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", USER_AGENT)

        request.headers().forEach { (key, values) ->
            values.forEach { value -> conn.setRequestProperty(key, value) }
        }

        val body = request.dataToSend()
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body) }
        }

        val responseCode = conn.responseCode
        val responseBody = runCatching {
            (if (responseCode < 400) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
        }.getOrDefault("")

        val responseHeaders: Map<String, List<String>> = conn.headerFields
            .filterKeys { it != null }
            .mapValues { it.value ?: emptyList() }

        conn.disconnect()

        return Response(responseCode, conn.responseMessage, responseHeaders, responseBody, request.url())
    }
}
