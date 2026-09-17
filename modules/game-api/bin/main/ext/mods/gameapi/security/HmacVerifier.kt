package ext.mods.gameapi.security

import ext.mods.gameapi.GameApiConfig
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * Verifies HMAC-SHA256 signatures on incoming requests from the site.
 *
 * Expected headers from site:
 *   X-Site-Timestamp: epoch millis
 *   X-Site-Nonce: unique string (UUID)
 *   X-Site-Signature: hex(HMAC-SHA256(secret, "METHOD|path|timestamp|nonce|bodyHash"))
 *
 * Body hash = SHA-256 hex of the raw body (empty string → hash of empty bytes).
 */
object HmacVerifier {

    private const val ALGORITHM = "HmacSHA256"

    /**
     * Validates the signature against the shared secret.
     * Returns true if the signature matches AND the timestamp is within the nonce window.
     */
    fun verify(
        method: String,
        path: String,
        timestamp: Long,
        nonce: String,
        bodyBytes: ByteArray,
        signature: String
    ): Boolean {
        val now = System.currentTimeMillis()
        val window = GameApiConfig.nonceWindowMs

        // Reject if timestamp is too old or in the future
        if (Math.abs(now - timestamp) > window) return false

        val bodyHash = sha256Hex(bodyBytes)
        val payload = "${method.uppercase()}|$path|$timestamp|$nonce|$bodyHash"

        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(GameApiConfig.secret, ALGORITHM))
        val expected = mac.doFinal(payload.toByteArray(Charsets.UTF_8)).toHex()

        if (!MessageDigest.isEqual(expected.toByteArray(), signature.lowercase().toByteArray())) return false

        // Consume the nonce only after the signature is valid; otherwise an attacker
        // could burn a legitimate nonce with an invalid signature.
        return NonceCache.tryConsume(nonce, timestamp)
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
