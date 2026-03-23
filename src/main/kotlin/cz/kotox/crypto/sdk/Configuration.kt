package cz.kotox.crypto.sdk

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.uri
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.minutes

@Suppress("MaxLineLength")
fun Application.configurePlugins() {
    // INSTALL SERVER-SIDE JSON SUPPORT
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(RateLimit) {
        register(RateLimitName("protect-news")) {
            rateLimiter(limit = 20, refillPeriod = 1.minutes)
        }
        register(RateLimitName("protect-sentry-android")) {
            rateLimiter(limit = 20, refillPeriod = 1.minutes)
        }
    }

    install(XForwardedHeaders) {
        // Essential for Cloud Run to see original client IPs
    }

    install(CallLogging) {
        level = Level.INFO // Sets the log level

        // Filter out noisy health checks if needed
        filter { call -> call.request.path().startsWith("/") }

        // This is the part that helps you verify stitching
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.headers["User-Agent"]

            // Extract Sentry headers to see if they are reaching your BFF
            val sentryTrace = call.request.headers["sentry-trace"] ?: "no-sentry-trace"
            val baggage = call.request.headers["baggage"] ?: "no-baggage"

            // This will appear in your GCP "textPayload"
            "HTTP $status: $httpMethod ${call.request.uri} | SentryTrace: $sentryTrace | Baggage: $baggage | UA: $userAgent"
        }
    }
}
