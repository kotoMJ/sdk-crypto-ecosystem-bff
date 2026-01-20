package cz.kotox.crypto.sdk

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

fun Application.configurePlugins() {
    // INSTALL SERVER-SIDE JSON SUPPORT
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(RateLimit) {
        register(RateLimitName("protect-news")) {
            rateLimiter(limit = 20, refillPeriod = 1.minutes)
        }
    }

    install(XForwardedHeaders) {
        // Essential for Cloud Run to see original client IPs
    }
}
