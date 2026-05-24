package com.ecoute.providers.innertube.requests

import com.ecoute.providers.innertube.Innertube
import com.ecoute.providers.innertube.models.Context
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

suspend fun Innertube.accountInfo(): Result<AccountInfo> = runCatching {
    val response = client.post("/youtubei/v1/account/account_menu") {
        contentType(ContentType.Application.Json)
        setBody(AccountMenuBody(context = Context.DefaultWeb))
    }.body<AccountMenuResponse>()

    val header = response.header?.googleAccountHeaderRenderer
    AccountInfo(
        name = header?.personName ?: error("No account name"),
        email = header?.email,
        channelHandle = header?.channelHandle
    )
}

data class AccountInfo(
    val name: String,
    val email: String?,
    val channelHandle: String?
)

@Serializable
data class AccountMenuBody(
    val context: Context
)

@Serializable
data class AccountMenuResponse(
    val header: Header? = null
) {
    @Serializable
    data class Header(
        val googleAccountHeaderRenderer: GoogleAccountHeaderRenderer? = null
    )

    @Serializable
    data class GoogleAccountHeaderRenderer(
        val personName: String? = null,
        val email: String? = null,
        val channelHandle: String? = null
    )
}
