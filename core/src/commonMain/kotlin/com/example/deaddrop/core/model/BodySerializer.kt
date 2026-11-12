package com.example.deaddrop.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
object BodySerializer {
    private val cbor = Cbor { ignoreUnknownKeys = true }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encodeSetBody(body: SetBody): ByteArray {
        return cbor.encodeToByteArray(body)
    }

    fun decodeSetBody(bytes: ByteArray): SetBody {
        return try {
            cbor.decodeFromByteArray<SetBody>(bytes)
        } catch (e: Exception) {
            json.decodeFromString<SetBody>(bytes.decodeToString())
        }
    }

    fun encodeMintBody(body: MintBody): ByteArray {
        return cbor.encodeToByteArray(body)
    }

    fun decodeMintBody(bytes: ByteArray): MintBody {
        return try {
            cbor.decodeFromByteArray<MintBody>(bytes)
        } catch (e: Exception) {
            json.decodeFromString<MintBody>(bytes.decodeToString())
        }
    }

    fun encodeMoveBody(body: MoveBody): ByteArray {
        return cbor.encodeToByteArray(body)
    }

    fun decodeMoveBody(bytes: ByteArray): MoveBody {
        return try {
            cbor.decodeFromByteArray<MoveBody>(bytes)
        } catch (e: Exception) {
            json.decodeFromString<MoveBody>(bytes.decodeToString())
        }
    }
}
