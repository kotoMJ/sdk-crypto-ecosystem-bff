package cz.kotox.crypto.sdk.service

import com.google.api.services.playintegrity.v1.PlayIntegrity
import com.google.api.services.playintegrity.v1.model.AccountDetails
import com.google.api.services.playintegrity.v1.model.AppAccessRiskVerdict
import com.google.api.services.playintegrity.v1.model.AppIntegrity
import com.google.api.services.playintegrity.v1.model.DecodeIntegrityTokenResponse
import com.google.api.services.playintegrity.v1.model.DeviceIntegrity
import com.google.api.services.playintegrity.v1.model.EnvironmentDetails
import com.google.api.services.playintegrity.v1.model.TokenPayloadExternal
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntegrityServiceTest {
    private val mockClient: PlayIntegrity = mock()
    private val mockV1: PlayIntegrity.V1 = mock()
    private val mockDecode: PlayIntegrity.V1.DecodeIntegrityToken = mock()

    private val integrityService = IntegrityService(mockClient)

    @Test
    fun `verifyToken returns true when all verdicts are healthy`() {
        val response =
            createMockResponse(
                appVerdict = "PLAY_RECOGNIZED",
                deviceVerdicts = listOf("MEETS_DEVICE_INTEGRITY"),
                risks = emptyList(),
                licensing = "LICENSED",
            )
        setupMockCall(response)

        val result = integrityService.verifyToken("valid_token", "cz.kotox.crypto")

        assertTrue(result)
    }

    @Test
    fun `verifyToken returns false when UNKNOWN_CAPTURING risk is detected`() {
        val response =
            createMockResponse(
                appVerdict = "PLAY_RECOGNIZED",
                deviceVerdicts = listOf("MEETS_DEVICE_INTEGRITY"),
                risks = listOf("UNKNOWN_CAPTURING"),
                licensing = "LICENSED",
            )
        setupMockCall(response)

        val result = integrityService.verifyToken("risky_token", "cz.kotox.crypto")

        assertFalse(result)
    }

    @Test
    fun `verifyToken returns false when app is UNRECOGNIZED_VERSION`() {
        val response =
            createMockResponse(
                appVerdict = "UNRECOGNIZED_VERSION",
                deviceVerdicts = listOf("MEETS_DEVICE_INTEGRITY"),
                risks = emptyList(),
                licensing = "LICENSED",
            )
        setupMockCall(response)

        val result = integrityService.verifyToken("sideloaded_token", "cz.kotox.crypto")

        assertFalse(result)
    }

    // Helper to mock the chain: v1().decodeIntegrityToken().execute()
    private fun setupMockCall(response: DecodeIntegrityTokenResponse) {
        whenever(mockClient.v1()).thenReturn(mockV1)
        whenever(mockV1.decodeIntegrityToken(any(), any())).thenReturn(mockDecode)
        whenever(mockDecode.execute()).thenReturn(response)
    }

    private fun createMockResponse(
        appVerdict: String,
        deviceVerdicts: List<String>,
        risks: List<String>,
        licensing: String,
    ): DecodeIntegrityTokenResponse {
        val payload =
            TokenPayloadExternal().apply {
                appIntegrity = AppIntegrity().setAppRecognitionVerdict(appVerdict)
                deviceIntegrity = DeviceIntegrity().setDeviceRecognitionVerdict(deviceVerdicts)
                accountDetails = AccountDetails().setAppLicensingVerdict(licensing)
                environmentDetails =
                    EnvironmentDetails().setAppAccessRiskVerdict(
                        AppAccessRiskVerdict().setAppsDetected(risks),
                    )
            }
        return DecodeIntegrityTokenResponse().setTokenPayloadExternal(payload)
    }
}
