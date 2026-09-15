package ext.mods.gameapi.security

import ext.mods.gameapi.GameApiConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Field
import java.nio.charset.StandardCharsets
import java.util.HexFormat
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class GameApiSecurityTest {

    private val testSecret = "test-secret-key-that-is-at-least-32-bytes-long".toByteArray(StandardCharsets.UTF_8)

    @BeforeEach
    fun setUp() {
        NonceCache.clear()
        // Use reflection to set secret and nonceWindowMs for testing
        setPrivateField(GameApiConfig, "secret", testSecret)
        setPrivateField(GameApiConfig, "nonceWindowMs", 300_000L)
    }

    private fun setPrivateField(target: Any, fieldName: String, value: Any) {
        val field: Field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    @Test
    fun testHmacVerifierSuccess() {
        val method = "POST"
        val path = "/internal/site/register"
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val bodyBytes = "{\"login\":\"testuser\",\"password\":\"testpass123\"}".toByteArray(StandardCharsets.UTF_8)

        // Compute valid signature
        val bodyHash = HmacVerifier.sha256Hex(bodyBytes)
        val canonical = "$method|$path|$timestamp|$nonce|$bodyHash"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(testSecret, "HmacSHA256"))
        val signature = HexFormat.of().formatHex(mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)))

        assertTrue(HmacVerifier.verify(method, path, timestamp, nonce, bodyBytes, signature))
    }

    @Test
    fun testHmacVerifierReplayFails() {
        val method = "POST"
        val path = "/internal/site/login"
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val bodyBytes = "{\"login\":\"testuser\"}".toByteArray(StandardCharsets.UTF_8)

        val bodyHash = HmacVerifier.sha256Hex(bodyBytes)
        val canonical = "$method|$path|$timestamp|$nonce|$bodyHash"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(testSecret, "HmacSHA256"))
        val signature = HexFormat.of().formatHex(mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)))

        // First verification succeeds
        assertTrue(HmacVerifier.verify(method, path, timestamp, nonce, bodyBytes, signature))
        // Replay with identical nonce fails
        assertFalse(HmacVerifier.verify(method, path, timestamp, nonce, bodyBytes, signature))
    }

    @Test
    fun testHmacVerifierInvalidSignatureFails() {
        val method = "GET"
        val path = "/internal/site/rankings/pvp"
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val bodyBytes = ByteArray(0)

        assertFalse(HmacVerifier.verify(method, path, timestamp, nonce, bodyBytes, "invalid_signature_hex_12345"))
    }

    @Test
    fun testHmacVerifierExpiredTimestampFails() {
        val method = "POST"
        val path = "/internal/site/vote/status"
        val oldTimestamp = System.currentTimeMillis() - 400_000L // outside 300s window
        val nonce = UUID.randomUUID().toString()
        val bodyBytes = ByteArray(0)

        val bodyHash = HmacVerifier.sha256Hex(bodyBytes)
        val canonical = "$method|$path|$oldTimestamp|$nonce|$bodyHash"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(testSecret, "HmacSHA256"))
        val signature = HexFormat.of().formatHex(mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)))

        assertFalse(HmacVerifier.verify(method, path, oldTimestamp, nonce, bodyBytes, signature))
    }

    @Test
    fun testNonceCacheAmortizedPurge() {
        val now = System.currentTimeMillis()
        for (i in 0 until 1000) {
            assertTrue(NonceCache.tryConsume("test-nonce-$i", now))
        }
        // Duplicate check
        assertFalse(NonceCache.tryConsume("test-nonce-0", now))
        assertFalse(NonceCache.tryConsume("test-nonce-999", now))
    }

    @Test
    fun testRateLimiterThreshold() {
        val limiter = RateLimiter(5)
        val ip = "127.0.0.1"

        for (i in 1..5) {
            assertTrue(limiter.tryAcquire(ip), "Request $i should be permitted")
        }
        assertFalse(limiter.tryAcquire(ip), "Request 6 should exceed rate limit")
    }

    @Test
    fun testHmacVerifierHotReloadOnSecretChange() {
        val tempProps = java.io.File.createTempFile("server", ".properties")
        tempProps.deleteOnExit()
        val initialSecretBytes = "initial-secret-that-is-at-least-32-bytes-long".toByteArray(StandardCharsets.UTF_8)
        val initialSecretB64 = java.util.Base64.getEncoder().encodeToString(initialSecretBytes)
        tempProps.writeText("GameApiSecret=$initialSecretB64\n")

        setPrivateField(GameApiConfig, "serverPropertiesFile", tempProps)
        setPrivateField(GameApiConfig, "lastLoadedTimestamp", tempProps.lastModified())
        setPrivateField(GameApiConfig, "secret", initialSecretBytes)

        val rotatedSecretBytes = "rotated-secret-that-is-at-least-32-bytes-long".toByteArray(StandardCharsets.UTF_8)
        val rotatedSecretB64 = java.util.Base64.getEncoder().encodeToString(rotatedSecretBytes)

        Thread.sleep(20L)
        tempProps.writeText("GameApiSecret=$rotatedSecretB64\n")
        tempProps.setLastModified(System.currentTimeMillis() + 1000L)

        val method = "POST"
        val path = "/internal/site/register"
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val bodyBytes = "{}".toByteArray(StandardCharsets.UTF_8)

        val bodyHash = HmacVerifier.sha256Hex(bodyBytes)
        val canonical = "$method|$path|$timestamp|$nonce|$bodyHash"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(rotatedSecretBytes, "HmacSHA256"))
        val signature = HexFormat.of().formatHex(mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)))

        assertTrue(HmacVerifier.verify(method, path, timestamp, nonce, bodyBytes, signature))
    }
}
