package cz.kotox.crypto.sdk

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.sentry.ktorClient.SentryKtorClientPlugin
import kotlinx.serialization.json.Json

/**
 * Extension property to provide a pre-configured HttpClient within the Application context.
 * This ensures the client is initialized once and managed by the Ktor application.
 */
fun Application.createHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }

        install(SentryKtorClientPlugin) {
            // Automatically captures 5xx errors and attaches sentry-trace headers
            captureFailedRequests = true
        }
    }
}
