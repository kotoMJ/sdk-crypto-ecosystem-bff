package cz.kotox.crypto.sdk.service

import cz.kotox.crypto.sdk.model.NewsApiResult
import cz.kotox.crypto.sdk.model.NewsArticle
import cz.kotox.crypto.sdk.model.NewsResponse
import cz.kotox.crypto.sdk.model.NewsSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class NewsService(private val httpClient: HttpClient) {
    private val apiKey: String by lazy {
        System.getenv("CRYPTO_SDK_NEWS_API_KEY") ?: error("CRYPTO_SDK_NEWS_API_KEY not set")
    }

    suspend fun fetchCryptoNews(): NewsResponse {
        val apiResponse =
            httpClient.get("https://newsapi.org/v2/everything") {
                parameter("q", "Bitcoin")
                parameter("sortBy", "publishedAt")
                parameter("language", "en") // "sv","es"
                parameter("apiKey", apiKey)
            }.body<NewsApiResult>()

        // Map external API format to our internal App format
        return NewsResponse(
            status = apiResponse.status,
            totalResults = apiResponse.totalResults,
            code = apiResponse.code,
            message = apiResponse.message,
            articles =
                apiResponse.articles.map { article ->
                    NewsArticle(
                        source =
                            NewsSource(
                                id = article.source.id,
                                name = article.source.name,
                            ),
                        author = article.author,
                        // Handle nulls here because the outgoing 'NewsArticle' requires non-null strings
                        // for title and url, but the incoming DTO might have them as null.
                        title = article.title ?: "No Title Available",
                        description = article.description,
                        url = article.url ?: "",
                        urlToImage = article.urlToImage,
                        publishedAt = article.publishedAt,
                        content = article.content,
                    )
                },
        )
    }
}
