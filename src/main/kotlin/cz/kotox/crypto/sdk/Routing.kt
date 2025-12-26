package cz.kotox.crypto.sdk

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.playintegrity.v1.PlayIntegrity
import com.google.api.services.playintegrity.v1.PlayIntegrityScopes
import com.google.api.services.playintegrity.v1.model.DecodeIntegrityTokenRequest
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import cz.kotox.crypto.sdk.model.IntegrityCheckRequest
import cz.kotox.crypto.sdk.model.NewsResponse
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
import java.io.ByteArrayInputStream
import java.util.Base64
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

@Suppress("LongMethod")
fun Application.configureRouting() {
    // INSTALL SERVER-SIDE JSON SUPPORT
    install(ServerContentNegotiation) {
        json()
    }

    // Initialize HTTP Client (for fetching upstream news)
    // val httpClient =
    HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    // Initialize Google Play Integrity Service
    val playIntegrityService by lazy {
        val serviceAccountJson =
            System.getenv("GOOGLE_SERVICE_ACCOUNT_JSON")
                ?: error("Missing GOOGLE_SERVICE_ACCOUNT_JSON env var")

        val credentials =
            GoogleCredentials.fromStream(
                ByteArrayInputStream(Base64.getDecoder().decode(serviceAccountJson)),
            ).createScoped(listOf(PlayIntegrityScopes.PLAYINTEGRITY))

        PlayIntegrity.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials),
        ).setApplicationName("ConferenceDemo").build()
    }

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
                // A. Receive Token
                val request = call.receive<IntegrityCheckRequest>()

                if (request.integrityToken == "skip-verification") {
                    call.application.environment.log.info("Skipping verification for Dev testing")
                    call.respond(NewsResponse("Dev News", "Verification skipped for testing."))
                    return@post
                }

                // B. Verify with Google
                val decodeRequest = DecodeIntegrityTokenRequest().setIntegrityToken(request.integrityToken)
                val response =
                    playIntegrityService.v1()
                        .decodeIntegrityToken("cz.kotox.sdk.crypto.app", decodeRequest).execute()
                val verdict = response.tokenPayloadExternal

                // C. Check Verdict
                val isAppRecognized = verdict.appIntegrity.appRecognitionVerdict == "PLAY_RECOGNIZED"
                val isDeviceSecure =
                    verdict.deviceIntegrity.deviceRecognitionVerdict.contains("MEETS_DEVICE_INTEGRITY")

                if (isAppRecognized && isDeviceSecure) {
                    // D. Success: Return Data
                    call.respond(NewsResponse("Secret Key Content", "This data is only for genuine apps."))
                } else {
                    call.respond(HttpStatusCode.Forbidden, "Integrity check failed.")
                }
            } catch (e: Exception) {
                // Accessing the logger via application environment
                call.application.environment.log.error("Verification error", e)
                call.respond(HttpStatusCode.InternalServerError, "Verification failed")
            }
        }
    }
}
