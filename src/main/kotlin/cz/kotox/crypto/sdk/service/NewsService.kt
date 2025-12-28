package cz.kotox.crypto.sdk.service

import cz.kotox.crypto.sdk.model.NewsApiResult
import cz.kotox.crypto.sdk.model.NewsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class NewsService(private val httpClient: HttpClient) {
    private val apiKey: String by lazy {
        System.getenv("CRYPTO_SDK_NEWS_API_KEY") ?: error("CRYPTO_SDK_NEWS_API_KEY not set")
    }

    suspend fun fetchCryptoNews(): List<NewsResponse> {
        val apiResponse =
            httpClient.get("https://newsapi.org/v2/everything") {
                parameter("q", "Bitcoin")
                parameter("sortBy", "publishedAt")
                parameter("apiKey", apiKey)
            }.body<NewsApiResult>()

        // Map external API format to our internal App format
        return apiResponse.articles.map { article ->
            NewsResponse(
                title = article.title ?: "No Title Available",
                content = article.description ?: "No content available",
            )
        }
    }
}
