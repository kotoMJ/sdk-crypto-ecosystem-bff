package cz.kotox.crypto.sdk

import cz.kotox.crypto.sdk.model.IntegrityCheckRequest
import cz.kotox.crypto.sdk.service.IntegrityService
import cz.kotox.crypto.sdk.service.NewsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.sentry.Sentry

@Suppress("LongMethod")
fun Application.configureRouting(
    newsService: NewsService,
    integrityService: IntegrityService,
    adminBypassSecret: String,
) {
    routing {
        get("/") {
            call.respondText("Kotox crypto BFF here!")
        }
        get("/test") {
            Sentry.captureMessage("BFF Test Route Accessed")
            call.respondText("Test accepted!")
        }

        @Suppress("TooGenericExceptionCaught")
        get("/network") {
            try {
                val stats = java.net.InetAddress.getByName("o4510727626883072.ingest.de.sentry.io")
                call.respondText("Network OK: Resolved Sentry IP to ${stats.hostAddress}")
            } catch (e: Exception) {
                call.respondText("Network BLOCKED: ${e.message}")
            }
        }

        @Suppress("MagicNumber")
        get("/sentry") {
            val isInitialized = Sentry.isEnabled()
            val dsn = Sentry.getCurrentScopes().options.dsn
            application.log.info("Sentry SDK Enabled: $isInitialized, DSN: $dsn")

            Sentry.captureMessage("Direct test from BFF at ${java.time.Instant.now()}")
            // Flush is good, but let's try a very long wait for this test
            Sentry.flush(5000)
            call.respondText("Check your logs for: $dsn")
        }

        @Suppress("TooGenericExceptionCaught")
        rateLimit(RateLimitName("protect-sentry-android")) {
            post("/sentry/android") {
                try {
                    val isValid = isRequestedByAuthorizedApp(adminBypassSecret, integrityService)
                    if (isValid) {
                        val dsnAndroid =
                            requireNotNull(System.getenv("SENTRY_DNS_CRYPTO_TRACKER_ANDROID_VALUE")) {
                                "SENTRY_DNS_CRYPTO_TRACKER_ANDROID_VALUE env is missing"
                            }

                        call.respond(dsnAndroid)
                    } else {
                        call.respond(HttpStatusCode.Forbidden, "Integrity check failed.")
                    }
                } catch (e: Exception) {
                    call.application.environment.log.error("API Error", e)
                    call.respond(HttpStatusCode.InternalServerError, "Server error")
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        // The secure endpoint
        rateLimit(RateLimitName("protect-news")) {
            post("/api/news") {
                try {
                    val isValid = isRequestedByAuthorizedApp(adminBypassSecret, integrityService)

                    if (isValid) {
                        // Step 2: Fetch Data
                        val news = newsService.fetchCryptoNews()
                        call.respond(news)
                    } else {
                        call.respond(HttpStatusCode.Forbidden, "Integrity check failed.")
                    }
                } catch (e: Exception) {
                    call.application.environment.log.error("API Error", e)
                    call.respond(HttpStatusCode.InternalServerError, "Server error")
                }
            }
        }
    }
}

private suspend fun RoutingContext.isRequestedByAuthorizedApp(
    adminBypassSecret: String,
    integrityService: IntegrityService,
): Boolean {
    val request = call.receive<IntegrityCheckRequest>()

    // --- SECURE BYPASS LOGIC START ---
    // We check for a specific HEADER, not the body content.
    val bypassHeader = call.request.headers["X-Kotox-Bypass-Key"]

    val isAuthorizedBypass =
        adminBypassSecret.isNotBlank() &&
            bypassHeader == adminBypassSecret

    val isValid =
        if (isAuthorizedBypass) {
            call.application.environment.log.warn(
                "Authorized Bypass Used by: ${call.request.origin.remoteHost}",
            )
            true
        } else {
            integrityService.verifyToken(
                token = request.integrityToken,
                // TODO MJ - add this to the configuration
                packageName = "cz.kotox.sdk.crypto.app",
                remoteHost = call.request.origin.remoteHost,
            )
        }
    // --- SECURE BYPASS LOGIC END ---
    return isValid
}
