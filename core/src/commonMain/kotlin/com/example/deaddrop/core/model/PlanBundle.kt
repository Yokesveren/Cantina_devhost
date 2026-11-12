package com.example.deaddrop.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RegionConfig(
    val id: String,
    val key: String, // Base64 key
    val pools: Map<String, Long> = emptyMap()
)

@Serializable
data class DeviceConfig(
    val id: String,
    val publicKey: String, // Base64 public key
    val region: String,
    val role: String = "rescuer"
)

@Serializable
data class PlanBundle(
    val authorityPublicKey: String,
    val regions: List<RegionConfig> = emptyList(),
    val devices: List<DeviceConfig> = emptyList(),
    val shares: Map<String, Map<String, Long>> = emptyMap()
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonString: String): PlanBundle {
            return json.decodeFromString(jsonString)
        }
    }
}
