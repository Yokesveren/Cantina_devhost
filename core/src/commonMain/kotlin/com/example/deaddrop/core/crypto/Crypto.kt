package com.example.deaddrop.core.crypto

data class KeyPair(val publicKey: ByteArray, val privateKey: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyPair) return false
        return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

interface Crypto {
    fun generateKeyPair(): KeyPair
    fun sign(privateKey: ByteArray, data: ByteArray): ByteArray
    fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean
    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray
    fun decrypt(key: ByteArray, ciphertext: ByteArray): ByteArray
    fun generateSymmetricKey(): ByteArray
}

expect fun createCrypto(): Crypto
