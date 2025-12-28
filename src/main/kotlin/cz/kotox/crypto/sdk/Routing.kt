package cz.kotox.crypto.sdk

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
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

@Suppress("LongMethod")
fun Application.configureRouting() {
    // INSTALL SERVER-SIDE JSON SUPPORT
    install(ServerContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
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

    routing {
        get("/") {
            call.respondText("Kotox crypto BFF here!")
        }
        get("/test") {
            call.respondText("Test accepted!")
        }

        @Suppress("TooGenericExceptionCaught")
        // The secure endpoint
        post("/api/news") {
            try {
                val request = call.receive<IntegrityCheckRequest>()

                // --- BYPASS LOGIC START ---
                // If the token matches our "magic string", we skip the Google check.
                // You can also add a check for a system property like: System.getenv("IS_DEV") == "true"
                val isBypass = request.integrityToken == "skip-verification"

                val isValid =
                    if (isBypass) {
                        call.application.environment.log.info("Bypassing Integrity Check for Development/Test")
                        true
                    } else {
                        integrityService.verifyToken(
                            token = request.integrityToken,
                            packageName = "cz.kotox.sdk.crypto.app",
                        )
                    }
                // --- BYPASS LOGIC END ---

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
