package cz.kotox.crypto.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class NewsApiResult(
    val status: String,
    val totalResults: Int,
    val articles: List<NewsApiArticle>,
)

@Serializable
data class NewsApiArticle(
    val title: String?,
    val description: String? = null,
    val url: String? = null,
)
