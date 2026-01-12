package cz.kotox.crypto.sdk.service

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.playintegrity.v1.PlayIntegrity
import com.google.api.services.playintegrity.v1.PlayIntegrityScopes
import com.google.api.services.playintegrity.v1.model.DecodeIntegrityTokenRequest
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import org.slf4j.LoggerFactory

class IntegrityService(
    private val injectedClient: PlayIntegrity? = null,
) {
    private val logger = LoggerFactory.getLogger(IntegrityService::class.java)

    // Initialize the Google Client lazily
    private val googleClient: PlayIntegrity by lazy {
        injectedClient ?: createDefaultClient()
    }

    private fun createDefaultClient(): PlayIntegrity {
        /**
         * This automatically finds credentials based on the environment
         * No need for GOOGLE_SERVICE_ACCOUNT_JSON env var on GCP!
         * For deploy on AWS, Azure, On-prem, ... use Workload Identity Federation
         */
        val credentials =
            GoogleCredentials.getApplicationDefault()
                .createScoped(listOf(PlayIntegrityScopes.PLAYINTEGRITY))

        return PlayIntegrity.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials),
        ).setApplicationName("ConferenceDemo").build()
    }

    @Suppress("TooGenericExceptionCaught", "MaxLineLength", "ktlint:standard:max-line-length", "MagicNumber")
    fun verifyToken(
        token: String,
        packageName: String,
        remoteHost: String,
    ): Boolean {
        return try {
            val decodeRequest = DecodeIntegrityTokenRequest().setIntegrityToken(token)
            val response =
                googleClient.v1()
                    .decodeIntegrityToken(packageName, decodeRequest)
                    .execute()

            val verdict = response.tokenPayloadExternal

            /**
             * PLAY_RECOGNIZED - Official Play Store version - Proceed with sensitive actions.
             * UNRECOGNIZED_VERSION - Modified or sideloaded versio - Restrict access or show a remediation dialog.
             * UNEVALUATED - Integrity check was skipped - Retry later or treat as high risk.
             */
            val isAppRecognized = verdict.appIntegrity.appRecognitionVerdict == "PLAY_RECOGNIZED"

            /**
             * MEETS_VIRTUAL_INTEGRITY - The app is running on an Android-powered emulator that passes system
             * integrity checks and meets core compatibility requirements.
             * MEETS_BASIC_INTEGRITY - The device passes basic system integrity checks. However, it may be rooted,
             * have an unlocked bootloader, or be a non-certified device where Google cannot provide security assurances.
             * MEETS_DEVICE_INTEGRITY - The device is a genuine, certified Android device powered by Google Play services.
             * It passes system integrity checks and meets Android compatibility requirements.
             * This is the standard requirement for most banking and high-security apps.
             * MEETS_STRONG_INTEGRITY - The highest level of trust.
             * The device has a hardware-backed guarantee of integrity (such as a Trusted Execution Environment or HSM).
             * On Android 13+, this also requires a security update released within the last year.
             */
            val isDeviceSecure = verdict.deviceIntegrity.deviceRecognitionVerdict.contains("MEETS_DEVICE_INTEGRITY")

            /**
             * The App Access Risk verdict consists of two parts:
             * the Source (Known vs. Unknown)
             * and the Action (Installed, Capturing, Overlays, or Controlling).
             * (UN)KNOWN_INSTALLED - LOW RISK - A potentially risky app is present on the device but is not active.,
             * (UN)KNOWN_CAPTURING - HIGH RISK - An app is actively recording or streaming the screen.
             * Can steal credentials or sensitive data as they are typed/displayed,
             * (UN)KNOWN_OVERLAYS - HIGH RISK - An app is drawing a window on top of your app,
             * Susceptible to "Tapjacking," where a user thinks they are clicking one thing
             * but are actually clicking a hidden layer.
             * (UN)KNOWN_CONTROLLING - CRITICAL - An app is using Accessibility Services to control the UI or intercept user inputs.
             * The other app can potentially "drive" your app, click buttons, or read text fields.
             *
             * environmentDetails may be null if not enabled/supported
             */
            val riskVerdict = verdict.environmentDetails?.appAccessRiskVerdict
            val riskyApps = riskVerdict?.appsDetected ?: emptyList()

            val hasNoActiveRisk =
                riskyApps.none {
                    it.contains("CAPTURING") || it.contains("OVERLAYS") || it.contains("CONTROLLING")
                }

            /**
             * LICENSED - The user has an app entitlement; they installed/updated it from Google Play.
             * UNLICENSED - The user does not have an entitlement (e.g., they sideloaded the app).
             * UNEVALUATED - Licensing wasn't checked (e.g., device not trustworthy or user not signed into Play).
             */
            val isAppLicensed = verdict.accountDetails.appLicensingVerdict == "LICENSED"

            val finalResult = isAppRecognized && isDeviceSecure && isAppLicensed && hasNoActiveRisk

            if (!finalResult) {
                logger.warn(
                    "Integrity Check FAILED for package: $packageName. " +
                        "Reason: AppRecognized=$isAppRecognized, DeviceSecure=$isDeviceSecure, Licensed=$isAppLicensed, NoRisk=$hasNoActiveRisk",
                )
                logger.warn(
                    """
                    [Integrity Failure Detail]
                    User/IP: $remoteHost
                    Package: ${verdict.requestDetails.requestPackageName}
                    Cert Digest: ${verdict.appIntegrity.certificateSha256Digest?.take(10)}...
                    Timestamp: ${verdict.requestDetails.timestampMillis}

                    Verdicts:
                    - App: ${verdict.appIntegrity.appRecognitionVerdict}
                    - Device: ${verdict.deviceIntegrity.deviceRecognitionVerdict}
                    - Account: ${verdict.accountDetails.appLicensingVerdict}
                    - Environment Risks: ${verdict.environmentDetails?.appAccessRiskVerdict?.appsDetected ?: "None"}
                    """.trimIndent(),
                )
            }

            return finalResult
        } catch (e: Exception) {
            logger.error("Integrity check failed", e)
            false
        }
    }
}
