package com.ecoute.providers.innertube.utils

import java.security.MessageDigest

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split("; ").associate { part ->
        val index = part.indexOf('=')
        if (index == -1) part.trim() to ""
        else part.substring(0, index).trim() to part.substring(index + 1).trim()
    }

fun sha1(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
