package cz.kotox.crypto.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class NewsApiResult(
    val status: String,
    // totalResults and articles are mandatory on "ok", but missing on "error"
    val totalResults: Int = 0,
    val articles: List<NewsApiArticle> = emptyList(),
    // code and message are present only when status is "error"
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class NewsApiArticle(
    val source: NewsApiSource,
    val author: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String,
    val urlToImage: String? = null,
    val publishedAt: String,
    val content: String? = null,
)

@Serializable
data class NewsApiSource(
    val id: String? = null,
    val name: String,
)
