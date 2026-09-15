package ext.mods.gameapi.security

import ext.mods.gameapi.GameApiConfig
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Verifies HMAC-SHA256 signatures on incoming requests from the site.
 * High-performance, zero-allocation implementation with ThreadLocal caching
 * and Java 25 HexFormat.
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
    private val HEX = HexFormat.of()

    @Volatile
    private var cachedSecret: ByteArray? = null
    @Volatile
    private var cachedKeySpec: SecretKeySpec? = null

    private val MAC_TL = ThreadLocal.withInitial {
        try {
            Mac.getInstance(ALGORITHM)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private val SHA256_TL = ThreadLocal.withInitial {
        try {
            MessageDigest.getInstance("SHA-256")
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun getKeySpec(): SecretKeySpec {
        val currentSecret = GameApiConfig.secret
        var key = cachedKeySpec
        if (key == null || cachedSecret !== currentSecret) {
            key = SecretKeySpec(currentSecret, ALGORITHM)
            cachedSecret = currentSecret
            cachedKeySpec = key
        }
        return key
    }

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

        val mac = MAC_TL.get()
        mac.reset()
        mac.init(getKeySpec())
        val macBytes = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        val expected = HEX.formatHex(macBytes)

        if (!MessageDigest.isEqual(expected.toByteArray(StandardCharsets.US_ASCII), signature.lowercase().toByteArray(StandardCharsets.US_ASCII))) {
            // Se a chave em disco foi modificada (ex: o site foi iniciado ou reiniciado rotacionando os segredos),
            // tentamos hot-reload sem regressao de usabilidade para o GameServer ativo.
            if (GameApiConfig.reloadSecretIfModified()) {
                mac.reset()
                mac.init(getKeySpec())
                val retryBytes = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
                val retryExpected = HEX.formatHex(retryBytes)
                if (!MessageDigest.isEqual(retryExpected.toByteArray(StandardCharsets.US_ASCII), signature.lowercase().toByteArray(StandardCharsets.US_ASCII))) {
                    return false
                }
            } else {
                return false
            }
        }

        // Consume the nonce only after the signature is valid; otherwise an attacker
        // could burn a legitimate nonce with an invalid signature.
        return NonceCache.tryConsume(nonce, timestamp)
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = SHA256_TL.get()
        digest.reset()
        return HEX.formatHex(digest.digest(data))
    }
}
