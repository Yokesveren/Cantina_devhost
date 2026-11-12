package com.example.deaddrop.core.sync

import com.example.deaddrop.core.model.Op
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json

@Serializable
sealed class SyncMessage {
    @Serializable
    data class Hello(val deviceId: String, val summary: RangeSetSummary) : SyncMessage()

    @Serializable
    data class Ops(val batch: List<Op>) : SyncMessage()

    @Serializable
    data class Ack(val deviceId: String, val summary: RangeSetSummary) : SyncMessage()
}

@OptIn(ExperimentalSerializationApi::class)
object SyncSerializer {
    private val cbor = Cbor { ignoreUnknownKeys = true }
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(message: SyncMessage): ByteArray {
        return try {
            cbor.encodeToByteArray(message)
        } catch (e: Exception) {
            json.encodeToString(SyncMessage.serializer(), message).encodeToByteArray()
        }
    }

    fun decode(bytes: ByteArray): SyncMessage {
        return try {
            cbor.decodeFromByteArray<SyncMessage>(bytes)
        } catch (e: Exception) {
            json.decodeFromString(SyncMessage.serializer(), bytes.decodeToString())
        }
    }
}
