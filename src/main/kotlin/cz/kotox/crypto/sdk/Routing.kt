package cz.kotox.crypto.sdk

import cz.kotox.crypto.sdk.model.IntegrityCheckRequest
import cz.kotox.crypto.sdk.service.IntegrityService
import cz.kotox.crypto.sdk.service.NewsService
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.header
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
            val dsn = Sentry.getCurrentHub().options.dsn
            application.log.info("DEBUG [Sentry]: SDK Enabled: $isInitialized, Using DSN: $dsn")

            Sentry.captureMessage("Direct test from BFF at ${java.time.Instant.now()}")
            // Flush is good, but let's try a very long wait for this test
            Sentry.flush(5000)
            call.respondText("Check your logs for: $dsn")
        }

        @Suppress("TooGenericExceptionCaught")
        get("/sentryraw") {
            val client = io.ktor.client.HttpClient()
            try {
                val response =
                    client.post("https://o4510727626883072.ingest.de.sentry.io/api/4510727742554192/envelope/") {
                        // Sentry requires a specific header for raw ingest
                        header("X-Sentry-Auth", "Sentry sentry_version=7, sentry_key=182bf04220e37772a37194d801c0624a")
                        setBody("{}") // Empty envelope
                    }
                call.respondText("Direct Post Status: ${response.status}")
            } catch (e: Exception) {
                call.respondText("Direct Post FAILED: ${e.message}")
            }
        }

        @Suppress("TooGenericExceptionCaught")
        // The secure endpoint
        rateLimit(RateLimitName("protect-news")) {
            post("/api/news") {
                try {
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
                                packageName = "cz.kotox.sdk.crypto.app",
                                remoteHost = call.request.origin.remoteHost,
                            )
                        }
                    // --- SECURE BYPASS LOGIC END ---

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
