package com.dnnypck.capacitiesquicknotepro.data.oauth

import java.security.MessageDigest
import java.security.SecureRandom

data class PkceCodes(
    val verifier: String,
    val challenge: String,
    val method: String = "S256"
)

object Pkce {
    private const val VERIFIER_BYTES = 32

    fun generate(random: SecureRandom = SecureRandom()): PkceCodes {
        val bytes = ByteArray(VERIFIER_BYTES)
        random.nextBytes(bytes)
        val verifier = Base64Url.encode(bytes)
        val challenge = Base64Url.encode(sha256(verifier.toByteArray(Charsets.US_ASCII)))
        return PkceCodes(verifier = verifier, challenge = challenge)
    }

    private fun sha256(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(input)
    }
}

/**
 * RFC 7636 base64url without padding. Implemented in pure Kotlin so PKCE
 * generation can be unit-tested on the JVM (minSdk 24 cannot assume java.util.Base64).
 */
object Base64Url {
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray()

    fun encode(data: ByteArray): String {
        val out = StringBuilder((data.size * 4 + 2) / 3)
        var index = 0
        while (index + 3 <= data.size) {
            val n = ((data[index].toInt() and 0xFF) shl 16) or
                ((data[index + 1].toInt() and 0xFF) shl 8) or
                (data[index + 2].toInt() and 0xFF)
            out.append(alphabet[(n shr 18) and 63])
            out.append(alphabet[(n shr 12) and 63])
            out.append(alphabet[(n shr 6) and 63])
            out.append(alphabet[n and 63])
            index += 3
        }
        when (data.size - index) {
            1 -> {
                val n = (data[index].toInt() and 0xFF) shl 16
                out.append(alphabet[(n shr 18) and 63])
                out.append(alphabet[(n shr 12) and 63])
            }
            2 -> {
                val n = ((data[index].toInt() and 0xFF) shl 16) or
                    ((data[index + 1].toInt() and 0xFF) shl 8)
                out.append(alphabet[(n shr 18) and 63])
                out.append(alphabet[(n shr 12) and 63])
                out.append(alphabet[(n shr 6) and 63])
            }
        }
        return out.toString()
    }
}
