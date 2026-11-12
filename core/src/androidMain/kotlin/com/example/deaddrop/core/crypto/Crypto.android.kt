package com.example.deaddrop.core.crypto

import org.bouncycastle.crypto.KeyGenerationParameters
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

class AndroidCrypto : Crypto {
    private val secureRandom = SecureRandom()

    override fun generateKeyPair(): KeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.init(KeyGenerationParameters(secureRandom, 256))
        val keyPair = generator.generateKeyPair()
        val priv = (keyPair.private as Ed25519PrivateKeyParameters).encoded
        val pub = (keyPair.public as Ed25519PublicKeyParameters).encoded
        return KeyPair(publicKey = pub, privateKey = priv)
    }

    override fun sign(privateKey: ByteArray, data: ByteArray): ByteArray {
        val privParams = Ed25519PrivateKeyParameters(privateKey, 0)
        val signer = Ed25519Signer()
        signer.init(true, privParams)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    override fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val pubParams = Ed25519PublicKeyParameters(publicKey, 0)
            val signer = Ed25519Signer()
            signer.init(false, pubParams)
            signer.update(data, 0, data.size)
            signer.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }

    override fun generateSymmetricKey(): ByteArray {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        return key
    }

    override fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = GCMBlockCipher(AESEngine())
        val params = AEADParameters(KeyParameter(key), 128, iv)
        cipher.init(true, params)
        val output = ByteArray(cipher.getOutputSize(plaintext.size))
        val len = cipher.processBytes(plaintext, 0, plaintext.size, output, 0)
        cipher.doFinal(output, len)
        return iv + output
    }

    override fun decrypt(key: ByteArray, ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= 12) { "Invalid ciphertext" }
        val iv = ciphertext.copyOfRange(0, 12)
        val encryptedData = ciphertext.copyOfRange(12, ciphertext.size)
        val cipher = GCMBlockCipher(AESEngine())
        val params = AEADParameters(KeyParameter(key), 128, iv)
        cipher.init(false, params)
        val output = ByteArray(cipher.getOutputSize(encryptedData.size))
        val len = cipher.processBytes(encryptedData, 0, encryptedData.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        return output.copyOfRange(0, len + finalLen)
    }
}

actual fun createCrypto(): Crypto = AndroidCrypto()
