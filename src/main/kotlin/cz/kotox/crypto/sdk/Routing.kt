package cz.kotox.crypto.sdk

import cz.kotox.crypto.sdk.exception.MissingConfigException
import cz.kotox.crypto.sdk.model.IntegrityCheckRequest
import cz.kotox.crypto.sdk.service.IntegrityService
import cz.kotox.crypto.sdk.service.NewsService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

@Suppress("LongMethod")
fun Application.configureRouting() {
    // INSTALL SERVER-SIDE JSON SUPPORT
    install(ServerContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(RateLimit) {
        register(RateLimitName("protect-news")) {
            rateLimiter(limit = 20, refillPeriod = 1.minutes)
        }
    }

    install(StatusPages) {
        exception<MissingConfigException> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Configuration Error")
        }
    }

    install(XForwardedHeaders) {
        // Optional: In Cloud Run, the first IP in the list is usually the client.
        // If you are behind multiple proxies, you might need extra configuration.
    }

    // To implement the real News API call, we need to: ... check gemini
    // Initialize HTTP Client (for fetching upstream news)
    // val httpClient =
    val httpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    val integrityService = IntegrityService()
    val newsService = NewsService(httpClient)

    val adminBypassSecret: String = System.getenv("BFF_CRYPTO_ADMIN_BYPASS_SECRET")

    val cloudProjectNumber: Long =
        System.getenv("GCP_PROJECT_NUMBER")?.toLongOrNull()
            ?: throw MissingConfigException("GCP_PROJECT_NUMBER is required")

    routing {
        get("/") {
            call.respondText("Kotox crypto BFF here!")
        }
        get("/test") {
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
                                cloudProjectNumber = cloudProjectNumber,
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
