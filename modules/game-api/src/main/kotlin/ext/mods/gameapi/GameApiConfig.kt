package ext.mods.gameapi

import ext.mods.commons.config.ServerPropertiesSecretBootstrap
import ext.mods.commons.logging.CLogger
import java.io.File
import java.util.Properties

/**
 * Configuration for the internal Site API HTTP server.
 * Read from game/config/server.properties alongside the normal game-server config.
 *
 * Properties:
 *   GameApiEnabled            = true|false (default: false)
 *   GameApiHost               = 127.0.0.1
 *   GameApiPort               = 9080
 *   GameApiSecret             = base64-encoded, 32+ bytes
 *   GameApiNonceWindowMs      = 300000  (5 min)
 *   GameApiRateLimit          = 30      (requests/min per IP)
 *   GameApiClanChatRateLimit  = 120     (chat messages/min per IP - higher for real-time)
 *   GameApiClanChatAllowOffline = true  (chat funcs with offline char)
 *   GameApiClanChatHistorySize = 300   (max entries per clan buffer)
 *   SiteAccountRenameItemId       = 4037
 *   SiteAccountPkResetItemId      = 4037
 *   SiteAccountPlayerResetItemId  = 4037
 *   SiteAccountPlayerResetLoc     = 25665,15888,-3545
 *   SiteAccountPlayerResetInstanceId = 0
 *   SiteAccountClanRenameItemId   = 4037
 *   SiteAccountClanRenameItemAmount = 1
 */
object GameApiConfig {
    private val LOGGER = CLogger(GameApiConfig::class.java.name)

    @Volatile private var serverPropertiesFile: File? = null
    @Volatile private var lastLoadedTimestamp: Long = 0L

    var enabled: Boolean = false
        private set
    var host: String = "127.0.0.1"
        private set
    var port: Int = 9080
        private set
    var secret: ByteArray = ByteArray(0)
        private set
    var nonceWindowMs: Long = 300_000L
        private set
    var rateLimitPerMinute: Int = 30
        private set
    var clanChatRateLimitPerMinute: Int = 120
        private set
    var clanChatAllowOffline: Boolean = true
        private set
    var clanChatHistorySize: Int = 300
        private set
    var clanChatCooldownMs: Long = 3000L
        private set
    var clanChatMaxPerMinute: Int = 20
        private set
    var siteWsPushUrl: String? = null
        private set
    var siteWsPushHost: String = "127.0.0.1"
        private set
    var siteWsPushPort: Int = 8080
        private set
    var accountRenameItemId: Int = 4037
        private set
    var accountPkResetItemId: Int = 4037
        private set
    var accountPlayerResetItemId: Int = 4037
        private set
    var accountClanRenameItemId: Int = 4037
        private set
    var accountClanRenameItemAmount: Long = 1L
        private set
    var accountClanRenameAllyItemId: Int = 4037
        private set
    var accountClanLevelUpItemId: Int = 4037
        private set
    var accountClanLevelDownItemId: Int = 4037
        private set
    var accountClanTransferLeaderItemId: Int = 4037
        private set
    var accountClanBanMemberItemId: Int = 4037
        private set
    var accountPlayerResetX: Int = 25665
        private set
    var accountPlayerResetY: Int = 15888
        private set
    var accountPlayerResetZ: Int = -3545
        private set
    var accountPlayerResetInstanceId: Int = 0
        private set

    fun load() {
        // Make sure GameApiSecret is present and valid before we read it.
        // This also keeps SiteGameApiSecret in sync (they MUST match).
        runCatching { ServerPropertiesSecretBootstrap.ensureDefault() }

        val props = loadServerProperties()
        enabled = props.bool("GameApiEnabled", false)
        host = props.getProperty("GameApiHost", "127.0.0.1")
        port = props.int("GameApiPort", 9080)
        val secretB64 = props.getProperty("GameApiSecret", "")
        secret = if (secretB64.isBlank()) ByteArray(0) else java.util.Base64.getDecoder().decode(secretB64)
        nonceWindowMs = props.long("GameApiNonceWindowMs", 300000L)
        rateLimitPerMinute = props.int("GameApiRateLimit", 30)
        clanChatRateLimitPerMinute = props.int("GameApiClanChatRateLimit", 120).coerceAtLeast(10)
        clanChatAllowOffline = props.bool("GameApiClanChatAllowOffline", true)
        clanChatHistorySize = props.int("GameApiClanChatHistorySize", 300).coerceIn(50, 5000)
        clanChatCooldownMs = props.long("GameApiClanChatCooldownMs", 3000L).coerceIn(500L, 30000L)
        clanChatMaxPerMinute = props.int("GameApiClanChatMaxPerMinute", 20).coerceIn(5, 200)
        val pushHostStr = props.getProperty("SiteWsPushHost")
            ?: props.getProperty("SiteBindHost")
            ?: props.getProperty("SITE_BIND_HOST")
            ?: props.getProperty("KtorWebServerIp")
        siteWsPushHost = pushHostStr?.trim()?.ifEmpty { null } ?: "127.0.0.1"

        val pushPortStr = props.getProperty("SiteWsPushPort")
            ?: props.getProperty("SiteBindPort")
            ?: props.getProperty("KtorWebServerPort")
            ?: props.getProperty("SITE_BIND_PORT")
            ?: props.getProperty("WebServerKtorPort")
        siteWsPushPort = pushPortStr?.toIntOrNull()?.coerceIn(1, 65535) ?: 8080

        val pushEnabled = props.bool("SiteWsPushEnabled", true)
        val explicitPushUrl = props.getProperty("SiteWsPushUrl")?.trim()?.ifEmpty { null }
        siteWsPushUrl = if (pushEnabled) (explicitPushUrl ?: "http://$siteWsPushHost:$siteWsPushPort") else null
        accountRenameItemId = props.int("SiteAccountRenameItemId", 4037).coerceAtLeast(1)
        accountPkResetItemId = props.int("SiteAccountPkResetItemId", 4037).coerceAtLeast(1)
        accountPlayerResetItemId = props.int("SiteAccountPlayerResetItemId", 4037).coerceAtLeast(1)
        accountClanRenameItemId = props.int("SiteAccountClanRenameItemId", 4037).coerceAtLeast(1)
        accountClanRenameItemAmount = props.long("SiteAccountClanRenameItemAmount", 1L).coerceAtLeast(1L)
        accountClanRenameAllyItemId = props.int("SiteAccountClanRenameAllyItemId", 4037).coerceAtLeast(1)
        accountClanLevelUpItemId = props.int("SiteAccountClanLevelUpItemId", 4037).coerceAtLeast(1)
        accountClanLevelDownItemId = props.int("SiteAccountClanLevelDownItemId", 4037).coerceAtLeast(1)
        accountClanTransferLeaderItemId = props.int("SiteAccountClanTransferLeaderItemId", 4037).coerceAtLeast(1)
        accountClanBanMemberItemId = props.int("SiteAccountClanBanMemberItemId", 4037).coerceAtLeast(1)
        val resetLoc = props.location("SiteAccountPlayerResetLoc", 25665, 15888, -3545)
        accountPlayerResetX = resetLoc.x
        accountPlayerResetY = resetLoc.y
        accountPlayerResetZ = resetLoc.z
        accountPlayerResetInstanceId = props.int("SiteAccountPlayerResetInstanceId", 0).coerceAtLeast(0)
    }

    private fun findServerPropertiesFile(): File? {
        val candidates = listOf(
            System.getProperty("brproject.server.properties"),
            "game/config/server.properties",
            "config/server.properties",
            "../game/config/server.properties",
            "../../game/config/server.properties"
        ).filterNotNull()
        for (candidate in candidates) {
            val file = File(candidate)
            if (file.exists() && file.isFile) return file
        }
        return null
    }

    private fun loadServerProperties(): Properties {
        val props = Properties()
        val file = findServerPropertiesFile()
        if (file != null) {
            serverPropertiesFile = file
            lastLoadedTimestamp = file.lastModified()
            file.inputStream().use { props.load(it) }
        }
        return props
    }

    /**
     * Checks if server.properties has been modified since the last load.
     * If so, hot-reloads GameApiSecret into memory and returns true.
     * Prevents usability regressions when the site is started or rotates secrets while GameServer is active.
     */
    fun reloadSecretIfModified(): Boolean {
        val file = serverPropertiesFile ?: findServerPropertiesFile() ?: return false
        val currentMtime = file.lastModified()
        if (currentMtime <= lastLoadedTimestamp) {
            return false
        }
        synchronized(this) {
            if (file.lastModified() <= lastLoadedTimestamp) return false
            val props = Properties()
            try {
                file.inputStream().use { props.load(it) }
                val secretB64 = props.getProperty("GameApiSecret", "")
                val newSecret = if (secretB64.isBlank()) ByteArray(0) else java.util.Base64.getDecoder().decode(secretB64)
                if (newSecret.size >= 32) {
                    secret = newSecret
                    lastLoadedTimestamp = file.lastModified()
                    LOGGER.info("[game-api] GameApiSecret recarregado dinamicamente com sucesso de server.properties (mtime: {})", lastLoadedTimestamp)
                    return true
                }
            } catch (e: Exception) {
                LOGGER.warn("[game-api] Falha ao recarregar GameApiSecret dinamicamente: {}", e.message)
            }
        }
        return false
    }

    private fun Properties.bool(key: String, default: Boolean): Boolean =
        getProperty(key)?.trim()?.equals("true", ignoreCase = true) ?: default

    private fun Properties.int(key: String, default: Int): Int =
        getProperty(key)?.trim()?.toIntOrNull() ?: default

    private fun Properties.long(key: String, default: Long): Long =
        getProperty(key)?.trim()?.toLongOrNull() ?: default

    private data class ResetLocation(val x: Int, val y: Int, val z: Int)

    private fun Properties.location(key: String, defaultX: Int, defaultY: Int, defaultZ: Int): ResetLocation {
        val parts = getProperty(key)
            ?.split(',', ';')
            ?.map { it.trim().toIntOrNull() }
            ?: return ResetLocation(defaultX, defaultY, defaultZ)
        return ResetLocation(
            parts.getOrNull(0) ?: defaultX,
            parts.getOrNull(1) ?: defaultY,
            parts.getOrNull(2) ?: defaultZ
        )
    }
}