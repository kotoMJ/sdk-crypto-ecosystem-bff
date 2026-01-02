package cz.kotox.crypto.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class NewsResponse(
    val status: String,
    val totalResults: Int = 0,
    val articles: List<NewsArticle> = emptyList(),
    // Error fields are optional, present only if status != "ok"
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class NewsArticle(
    val source: NewsSource,
    val author: String? = null,
    val title: String,
    val description: String? = null,
    val url: String,
    val urlToImage: String? = null,
    val publishedAt: String,
    val content: String? = null,
)

@Serializable
data class NewsSource(
    val id: String? = null,
    val name: String,
)
