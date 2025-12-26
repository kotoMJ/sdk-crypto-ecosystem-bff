package cz.kotox.crypto.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class NewsResponse(val title: String, val content: String)
