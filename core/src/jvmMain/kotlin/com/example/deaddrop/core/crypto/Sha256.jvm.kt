package com.example.deaddrop.core.crypto

import java.security.MessageDigest

actual fun sha256(data: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(data)
}
