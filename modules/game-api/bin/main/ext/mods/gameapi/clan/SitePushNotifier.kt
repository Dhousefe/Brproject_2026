package ext.mods.gameapi.clan

import com.google.gson.Gson
import ext.mods.commons.logging.CLogger
import ext.mods.gameapi.GameApiConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pushes new clan chat messages to the Site's WebSocket hub via HTTP POST.
 *
 * Architecture:
 *   ClanChatRing.append() → listener → SitePushNotifier (async, single-thread executor)
 *   → HTTP POST to site /internal/ws/clan-chat/push (HMAC-signed)
 *   → Site broadcasts to all WS clients of that clan
 *
 * This keeps the game-server thread unblocked: the HTTP call happens on a
 * dedicated daemon thread. If the site is down, failures are logged and dropped
 * (messages are still in the ring buffer for next poll fallback).
 *
 * Configuration:
 *   - SiteWsPushUrl: base URL of the site (e.g., http://127.0.0.1:8080)
 *   - GameApiSecret: shared HMAC secret (reused for signing push requests)
 */
object SitePushNotifier : ClanChatRing.OnMessageListener {

    private val LOGGER = CLogger(SitePushNotifier::class.java.name)
    private val GSON = Gson()

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ClanChat-SitePush").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    @Volatile
    private var siteBaseUrl: String? = null

    @Volatile
    private var secret: ByteArray = ByteArray(0)

    /**
     * Call once during startup (after GameApiConfig.load()) to activate push notifications.
     * If siteUrl is null/empty, push is disabled (polling-only mode).
     */
    fun init(siteUrl: String?, sharedSecret: ByteArray) {
        this.siteBaseUrl = siteUrl?.trimEnd('/')
        this.secret = sharedSecret
        if (siteBaseUrl != null && secret.isNotEmpty()) {
            ClanChatRing.addListener(this)
            LOGGER.info("[SitePushNotifier] active → $siteBaseUrl")
        } else {
            LOGGER.info("[SitePushNotifier] disabled (no SiteWsPushUrl or secret)")
        }
    }

    override fun onMessage(entry: ClanChatRing.Entry) {
        val url = siteBaseUrl ?: return
        executor.submit {
            try {
                pushToSite(url, entry)
            } catch (e: Exception) {
                LOGGER.warn("[SitePushNotifier] push failed for clan=${entry.clanId}: ${e.message}")
            }
        }
    }

    private fun pushToSite(baseUrl: String, entry: ClanChatRing.Entry) {
        val path = "/internal/ws/clan-chat/push"
        val body = GSON.toJson(mapOf(
            "clanId" to entry.clanId,
            "seq" to entry.seq,
            "time" to entry.time,
            "characterId" to entry.characterId,
            "characterName" to entry.characterName,
            "text" to entry.text
        ))

        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val signature = sign(secret, "POST", path, body, timestamp, nonce)

        val uri = URI("$baseUrl$path")
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(2))
            .header("Content-Type", "application/json")
            .header("X-Site-Timestamp", timestamp.toString())
            .header("X-Site-Nonce", nonce)
            .header("X-Site-Signature", signature)
            .POST(HttpRequest.BodyPublishers.ofString(body, Charsets.UTF_8))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            LOGGER.warn("[SitePushNotifier] site returned ${response.statusCode()} for clan=${entry.clanId}")
        }
    }

    private fun sign(secret: ByteArray, method: String, path: String, body: String, timestamp: Long, nonce: String): String {
        val bodyHash = sha256Hex(body.toByteArray(Charsets.UTF_8))
        val canonical = "${method.uppercase()}|$path|$timestamp|$nonce|$bodyHash"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    fun shutdown() {
        ClanChatRing.removeListener(this)
        executor.shutdownNow()
    }
}
