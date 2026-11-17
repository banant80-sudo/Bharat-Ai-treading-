package com.example.data.smartapi

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * RFC 6238 HMAC-SHA1 TOTP generator for Angel One SmartAPI Base32 TOTP Secrets.
 */
object TOTPGenerator {
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun generateTotp(base32Secret: String, timeMillis: Long = System.currentTimeMillis()): String? {
        val cleaned = base32Secret.trim().uppercase().replace(" ", "").replace("-", "")
        if (cleaned.isEmpty()) return null
        val keyBytes = decodeBase32(cleaned) ?: return null
        val counter = (timeMillis / 1000L) / 30L
        return computeHotp(keyBytes, counter, digits = 6)
    }

    fun secondsRemaining(timeMillis: Long = System.currentTimeMillis()): Int {
        val elapsedInWindow = ((timeMillis / 1000L) % 30L).toInt()
        return 30 - elapsedInWindow
    }

    private fun computeHotp(key: ByteArray, counter: Long, digits: Int): String? {
        return try {
            val data = ByteBuffer.allocate(8).putLong(counter).array()
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(key, "HmacSHA1"))
            val hash = mac.doFinal(data)
            val offset = hash[hash.size - 1].toInt() and 0x0F
            val binary =
                ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)
            val otp = binary % 10.0.pow(digits.toDouble()).toInt()
            otp.toString().padStart(digits, '0')
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBase32(input: String): ByteArray? {
        val stripped = input.trimEnd('=')
        if (stripped.isEmpty()) return null
        val output = ArrayList<Byte>((stripped.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        for (ch in stripped) {
            val value = BASE32_ALPHABET.indexOf(ch)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return output.toByteArray()
    }
}
