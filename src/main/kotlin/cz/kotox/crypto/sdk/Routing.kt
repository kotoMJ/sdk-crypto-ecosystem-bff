package cz.kotox.crypto.sdk

import cz.kotox.crypto.sdk.model.IntegrityCheckRequest
import cz.kotox.crypto.sdk.service.IntegrityService
import cz.kotox.crypto.sdk.service.NewsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
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
