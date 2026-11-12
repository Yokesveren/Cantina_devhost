package com.example.deaddrop.core.crypto

expect fun sha256(data: ByteArray): ByteArray

fun sha256Hex(data: ByteArray): String {
    return sha256(data).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
