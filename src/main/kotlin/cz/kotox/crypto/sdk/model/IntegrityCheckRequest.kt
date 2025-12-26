package cz.kotox.crypto.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class IntegrityCheckRequest(val integrityToken: String)
