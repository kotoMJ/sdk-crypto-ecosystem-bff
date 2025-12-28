package cz.kotox.crypto.sdk.service

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.playintegrity.v1.PlayIntegrity
import com.google.api.services.playintegrity.v1.PlayIntegrityScopes
import com.google.api.services.playintegrity.v1.model.DecodeIntegrityTokenRequest
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.util.Base64

class IntegrityService {
    private val logger = LoggerFactory.getLogger(IntegrityService::class.java)

    // Initialize the Google Client lazily
    private val googleClient: PlayIntegrity by lazy {
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

    @Suppress("TooGenericExceptionCaught")
    fun verifyToken(
        token: String,
        packageName: String,
    ): Boolean {
        // Dev Bypass
        if (token == "skip-verification") {
            logger.info("Skipping verification for Dev testing")
            return true
        }

        return try {
            val decodeRequest = DecodeIntegrityTokenRequest().setIntegrityToken(token)
            val response =
                googleClient.v1()
                    .decodeIntegrityToken(packageName, decodeRequest)
                    .execute()

            val verdict = response.tokenPayloadExternal
            val isAppRecognized = verdict.appIntegrity.appRecognitionVerdict == "PLAY_RECOGNIZED"
            val isDeviceSecure = verdict.deviceIntegrity.deviceRecognitionVerdict.contains("MEETS_DEVICE_INTEGRITY")

            isAppRecognized && isDeviceSecure
        } catch (e: Exception) {
            logger.error("Integrity check failed", e)
            false
        }
    }
}
