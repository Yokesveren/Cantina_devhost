package com.example.deaddrop.core.model

import kotlinx.serialization.Serializable

enum class Kind { SET, MINT, MOVE }

@Serializable
data class Op(
    val dev: String,          // device id, e.g. "A3"
    val seq: Long,            // this device's own counter: 1, 2, 3, no gaps, never reused
    val kind: Kind,           // SET, MINT, MOVE
    val region: String,       // which region key encrypts the body
    val priority: Int,        // 0 = critical, 1 = normal, 2 = low (plaintext, used for sync order)
    val hlc: Long,            // hybrid logical clock, for human display only, never for correctness
    val body: ByteArray,      // CBOR of SetBody/MintBody/MoveBody, AES-GCM encrypted or plaintext
    val sig: ByteArray        // Ed25519 over all other fields, by the device key
) {
    val id get() = "$dev:$seq"

    fun signaturePayload(): ByteArray {
        val header = "$dev|$seq|${kind.name}|$region|$priority|$hlc|"
        return header.encodeToByteArray() + body
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Op) return false
        return dev == other.dev && seq == other.seq && kind == other.kind &&
                region == other.region && priority == other.priority &&
                hlc == other.hlc && body.contentEquals(other.body) &&
                sig.contentEquals(other.sig)
    }

    override fun hashCode(): Int {
        var result = dev.hashCode()
        result = 31 * result + seq.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + region.hashCode()
        result = 31 * result + priority
        result = 31 * result + hlc.hashCode()
        result = 31 * result + body.contentHashCode()
        result = 31 * result + sig.contentHashCode()
        return result
    }
}

@Serializable
data class SetBody(
    val entity: String,
    val field: String,
    val value: String,
    val replaces: List<String> = emptyList()
)

@Serializable
data class MintBody(
    val item: String,
    val to: String,
    val n: Long
)

@Serializable
data class MoveBody(
    val item: String,
    val from: String,
    val to: String,
    val n: Long
)
