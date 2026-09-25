package ext.mods.gameapi.db

import ext.mods.commons.crypt.BCrypt
import ext.mods.commons.jdbc.DatabaseDialect
import ext.mods.commons.pool.ConnectionPool
import ext.mods.gameapi.GameApiConfig
import java.sql.Connection
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Database operations exposed to the internal Site API. */
object SiteApiRepository {

    enum class RegisterResult { CREATED, DUPLICATE, ERROR }

    data class AccountLoginResult(
        val ok: Boolean,
        val accessLevel: Int = 0,
        val lastServer: Int = 1,
        val message: String = ""
    )

    data class PlayerRankEntry(
        val position: Int,
        val name: String,
        val value: Int,
        val level: Int,
        val clan: String?
    )

    data class ClanRankEntry(
        val position: Int,
        val name: String,
        val level: Int,
        val reputation: Int,
        val members: Int,
        val leader: String?
    )

    data class AccountCharactersResponse(
        val ok: Boolean,
        val message: String,
        val characters: List<AccountCharacter> = emptyList(),
        val renameItemId: Int = GameApiConfig.accountRenameItemId,
        val renameItemTotal: Long = 0L,
        val pkResetItemId: Int = GameApiConfig.accountPkResetItemId,
        val pkResetItemTotal: Long = 0L,
        val playerResetItemId: Int = GameApiConfig.accountPlayerResetItemId,
        val playerResetItemTotal: Long = 0L,
        val clanRenameItemId: Int = GameApiConfig.accountClanRenameItemId,
        val clanRenameItemTotal: Long = 0L
    )

    data class AccountCharacter(
        val id: Int,
        val name: String,
        val title: String?,
        val level: Int,
        val race: Int,
        val classId: Int,
        val baseClass: Int,
        val sex: Int,
        val online: Boolean,
        val pvpKills: Int,
        val pkKills: Int,
        val karma: Int,
        val clan: String?,
        val inPeaceZone: Boolean = false,
        val inCombat: Boolean = false,
        val equipped: List<AccountItem>,
        val inventory: List<AccountItem>
    )

    data class AccountItem(
        val objectId: Int,
        val itemId: Int,
        val count: Long,
        val enchant: Int,
        val location: String,
        val slot: Int
    )

    data class RenameCharacterResult(
        val ok: Boolean,
        val message: String,
        val itemId: Int = GameApiConfig.accountRenameItemId,
        val remaining: Long = 0L,
        val oldName: String = "",
        val newName: String = ""
    )

    data class PkResetResult(
        val ok: Boolean,
        val message: String,
        val itemId: Int = GameApiConfig.accountPkResetItemId,
        val remaining: Long = 0L,
        val characterId: Int = 0,
        val characterName: String = "",
        val oldPkKills: Int = 0,
        val oldKarma: Int = 0
    )

    data class PlayerResetResult(
        val ok: Boolean,
        val message: String,
        val itemId: Int = GameApiConfig.accountPlayerResetItemId,
        val remaining: Long = 0L,
        val characterId: Int = 0,
        val characterName: String = "",
        val x: Int = GameApiConfig.accountPlayerResetX,
        val y: Int = GameApiConfig.accountPlayerResetY,
        val z: Int = GameApiConfig.accountPlayerResetZ,
        val instanceId: Int = GameApiConfig.accountPlayerResetInstanceId,
        val kicked: Boolean = false
    )

    data class RenameClanResult(
        val ok: Boolean,
        val message: String,
        val itemId: Int = GameApiConfig.accountClanRenameItemId,
        val remaining: Long = 0L,
        val characterId: Int = 0,
        val characterName: String = "",
        val clanId: Int = 0,
        val oldName: String = "",
        val newName: String = "",
        val persistedName: String = ""
    )

    data class ClanAllianceInfoResult(
        val ok: Boolean,
        val message: String,
        val characterId: Int = 0,
        val characterName: String = "",
        val clanId: Int = 0,
        val clanName: String = "",
        val hasAlliance: Boolean = false,
        val currentAllyName: String = "",
        val allianceLeaderName: String = "",
        val allianceLeaderClan: String = "",
        val isAllianceLeader: Boolean = false
    )

    data class ClanMemberListItem(
        val characterId: Int,
        val characterName: String,
        val level: Int,
        val classId: Int,
        val online: Boolean,
        val eligibleForLeadership: Boolean
    )

    data class ClanMembersResponse(
        val ok: Boolean,
        val message: String,
        val clanId: Int = 0,
        val clanName: String = "",
        val isExecutingLeader: Boolean = false,
        val members: List<ClanMemberListItem> = emptyList()
    )

    fun register(login: String, password: CharArray): RegisterResult {
        val passwordString = String(password)
        val hash = BCrypt.hashPw(passwordString)
        return try {
            ConnectionPool.getConnection().use { con ->
                if (accountExists(con, login)) return RegisterResult.DUPLICATE
                con.prepareStatement(
                    """
                    INSERT INTO accounts (login, password, last_active, access_level, last_server)
                    VALUES (?, ?, ?, 0, 1)
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, login)
                    ps.setString(2, hash)
                    ps.setLong(3, System.currentTimeMillis())
                    ps.executeUpdate()
                }
            }
            RegisterResult.CREATED
        } catch (_: Exception) {
            RegisterResult.ERROR
        }
    }

    fun login(login: String, password: CharArray): AccountLoginResult {
        val passwordString = String(password)
        return try {
            ConnectionPool.getConnection().use { con ->
                con.prepareStatement("SELECT password, access_level, last_server FROM accounts WHERE login=?").use { ps ->
                    ps.setString(1, login)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) {
                            // Timing hardening: burn a BCrypt check even when the account does not exist.
                            BCrypt.checkPw(passwordString, DUMMY_BCRYPT_HASH)
                            return AccountLoginResult(false, message = "Login ou senha inválidos")
                        }
                        val ok = BCrypt.checkPw(passwordString, rs.getString("password"))
                        if (!ok) return AccountLoginResult(false, message = "Login ou senha inválidos")
                        AccountLoginResult(true, rs.getInt("access_level"), rs.getInt("last_server"), "OK")
                    }
                }
            }
        } catch (_: Exception) {
            AccountLoginResult(false, message = "Erro interno")
        }
    }

    data class ChangePasswordResult(
        val ok: Boolean,
        val message: String
    )

    fun changePassword(login: String, currentPassword: CharArray, newPassword: CharArray): ChangePasswordResult {
        val currentPasswordString = String(currentPassword)
        val newPasswordString = String(newPassword)
        return try {
            ConnectionPool.getConnection().use { con ->
                con.prepareStatement("SELECT password FROM accounts WHERE login=?").use { ps ->
                    ps.setString(1, login)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) {
                            BCrypt.checkPw(currentPasswordString, DUMMY_BCRYPT_HASH)
                            return ChangePasswordResult(false, "Conta não encontrada")
                        }
                        val currentHash = rs.getString("password")
                        val ok = BCrypt.checkPw(currentPasswordString, currentHash)
                        if (!ok) {
                            return ChangePasswordResult(false, "Senha atual incorreta")
                        }
                        if (BCrypt.checkPw(newPasswordString, currentHash)) {
                            return ChangePasswordResult(false, "A nova senha deve ser diferente da senha atual")
                        }
                    }
                }
                val newHash = BCrypt.hashPw(newPasswordString)
                con.prepareStatement("UPDATE accounts SET password=? WHERE login=?").use { ps ->
                    ps.setString(1, newHash)
                    ps.setString(2, login)
                    val updated = ps.executeUpdate()
                    if (updated > 0) {
                        ChangePasswordResult(true, "Senha alterada com sucesso")
                    } else {
                        ChangePasswordResult(false, "Falha ao atualizar senha")
                    }
                }
            }
        } catch (_: Exception) {
            ChangePasswordResult(false, "Erro interno ao alterar senha")
        }
    }

    fun resetPassword(login: String, newPassword: CharArray): ChangePasswordResult {
        val newPasswordString = String(newPassword)
        return try {
            ConnectionPool.getConnection().use { con ->
                con.prepareStatement("SELECT password FROM accounts WHERE login=?").use { ps ->
                    ps.setString(1, login)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) {
                            return ChangePasswordResult(false, "Conta não encontrada")
                        }
                    }
                }
                val newHash = BCrypt.hashPw(newPasswordString)
                con.prepareStatement("UPDATE accounts SET password=? WHERE login=?").use { ps ->
                    ps.setString(1, newHash)
                    ps.setString(2, login)
                    val updated = ps.executeUpdate()
                    if (updated > 0) {
                        ChangePasswordResult(true, "Senha redefinida com sucesso via Hardware Guard")
                    } else {
                        ChangePasswordResult(false, "Falha ao redefinir senha")
                    }
                }
            }
        } catch (_: Exception) {
            ChangePasswordResult(false, "Erro interno ao redefinir senha")
        }
    }

    data class HardwareCredentialRecord(
        val credentialId: String,
        val publicKeyDer: String,
        val algorithm: Int,
        val deviceName: String,
        val signCount: Long,
        val createdAt: Long,
        val lastUsedAt: Long
    )

    fun getHardwareCredentials(login: String): List<HardwareCredentialRecord> {
        val list = mutableListOf<HardwareCredentialRecord>()
        return try {
            ConnectionPool.getConnection().use { con ->
                con.prepareStatement(
                    """
                    SELECT credential_id, public_key_der, algorithm, device_name, sign_count, created_at, last_used_at
                    FROM accounts_hardware_guard WHERE login=?
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, login.trim().lowercase())
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            list.add(
                                HardwareCredentialRecord(
                                    credentialId = rs.getString("credential_id") ?: "",
                                    publicKeyDer = rs.getString("public_key_der") ?: "",
                                    algorithm = rs.getInt("algorithm"),
                                    deviceName = rs.getString("device_name") ?: "Windows Hello (TPM 2.0)",
                                    signCount = rs.getLong("sign_count"),
                                    createdAt = rs.getLong("created_at"),
                                    lastUsedAt = rs.getLong("last_used_at")
                                )
                            )
                        }
                    }
                }
            }
            list
        } catch (_: Exception) {
            list
        }
    }

    fun saveHardwareCredential(
        login: String,
        credentialId: String,
        publicKeyDer: String,
        algorithm: Int,
        deviceName: String,
        signCount: Long = 0L
    ): Boolean {
        val now = System.currentTimeMillis()
        val cleanLogin = login.trim().lowercase()
        return try {
            ConnectionPool.getConnection().use { con ->
                con.prepareStatement(
                    DatabaseDialect.upsert(
                        "accounts_hardware_guard",
                        "login, credential_id, public_key_der, algorithm, device_name, sign_count, created_at, last_used_at",
                        "?, ?, ?, ?, ?, ?, ?, ?",
                        "login, credential_id",
                        "public_key_der, algorithm, device_name, sign_count, created_at, last_used_at"
                    )
                ).use { ps ->
                    ps.setString(1, cleanLogin)
                    ps.setString(2, credentialId)
                    ps.setString(3, publicKeyDer)
                    ps.setInt(4, algorithm)
                    ps.setString(5, deviceName)
                    ps.setLong(6, signCount)
                    ps.setLong(7, now)
                    ps.setLong(8, now)
                    ps.executeUpdate() > 0
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun removeHardwareCredentials(login: String): Boolean {
        return try {
            ConnectionPool.getConnection().use { con ->
                con.prepareStatement("DELETE FROM accounts_hardware_guard WHERE login=?").use { ps ->
                    ps.setString(1, login.trim().lowercase())
                    ps.executeUpdate() >= 0
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun updateHardwareSignCount(login: String, credentialId: String, newSignCount: Long): Boolean {
        val now = System.currentTimeMillis()
        return try {
            ConnectionPool.getConnection().use { con ->
                con.prepareStatement(
                    "UPDATE accounts_hardware_guard SET sign_count=?, last_used_at=? WHERE login=? AND credential_id=?"
                ).use { ps ->
                    ps.setLong(1, newSignCount)
                    ps.setLong(2, now)
                    ps.setString(3, login.trim().lowercase())
                    ps.setString(4, credentialId)
                    ps.executeUpdate() > 0
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private data class CachedRanking(val timestamp: Long, val data: Any)
    private val rankingCache = ConcurrentHashMap<String, CachedRanking>()
    private const val RANKING_CACHE_TTL_MS = 10_000L

    fun ranking(type: String, limit: Int = 20): Any {
        val key = "$type:$limit"
        val now = System.currentTimeMillis()
        val cached = rankingCache[key]
        if (cached != null && (now - cached.timestamp) < RANKING_CACHE_TTL_MS) {
            return cached.data
        }
        val data = when (type) {
            "pvp" -> playerRanking("pvpkills", limit)
            "pk" -> playerRanking("pkkills", limit)
            "clan" -> clanRanking(limit)
            else -> emptyList<PlayerRankEntry>()
        }
        rankingCache[key] = CachedRanking(now, data)
        return data
    }

    fun accountCharacters(login: String): AccountCharactersResponse {
        return try {
            ConnectionPool.getConnection().use { con ->
                val characters = loadAccountCharacters(con, login)
                val renameItemTotal = countAccountItem(con, login, GameApiConfig.accountRenameItemId)
                val pkResetItemTotal = countAccountItem(con, login, GameApiConfig.accountPkResetItemId)
                val playerResetItemTotal = countAccountItem(con, login, GameApiConfig.accountPlayerResetItemId)
                val clanRenameItemTotal = countAccountItem(con, login, GameApiConfig.accountClanRenameItemId)
                if (characters.isEmpty()) {
                    AccountCharactersResponse(
                        true,
                        "Nenhum personagem vinculado a esta conta",
                        emptyList(),
                        renameItemTotal = renameItemTotal,
                        pkResetItemTotal = pkResetItemTotal,
                        playerResetItemTotal = playerResetItemTotal,
                        clanRenameItemTotal = clanRenameItemTotal
                    )
                } else {
                    AccountCharactersResponse(
                        true,
                        "OK",
                        characters,
                        renameItemTotal = renameItemTotal,
                        pkResetItemTotal = pkResetItemTotal,
                        playerResetItemTotal = playerResetItemTotal,
                        clanRenameItemTotal = clanRenameItemTotal
                    )
                }
            }
        } catch (_: Exception) {
            AccountCharactersResponse(false, "Erro interno ao carregar personagens", emptyList())
        }
    }

    fun renameCharacter(login: String, characterId: Int, newName: String): RenameCharacterResult {
        if (!CHAR_NAME_REGEX.matches(newName)) {
            return RenameCharacterResult(false, "Nome inválido: use 2-16 letras ou números.")
        }
        val itemId = GameApiConfig.accountRenameItemId
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedCharacterForRename(con, login, characterId)
                        ?: return@use rollback(con, RenameCharacterResult(false, "Personagem inválido para esta conta.", itemId = itemId))
                    if (current.online) {
                        return@use rollback(con, RenameCharacterResult(false, "O personagem precisa estar offline para trocar o nome.", itemId = itemId))
                    }
                    if (current.name.equals(newName, ignoreCase = true)) {
                        return@use rollback(con, RenameCharacterResult(false, "Escolha um nome diferente do atual.", itemId = itemId, oldName = current.name, newName = newName))
                    }
                    if (characterNameExists(con, newName, characterId)) {
                        return@use rollback(con, RenameCharacterResult(false, "Este nome já está em uso.", itemId = itemId, oldName = current.name, newName = newName))
                    }
                    val item = loadConsumableAccountItem(con, login, itemId)
                        ?: return@use rollback(con, RenameCharacterResult(false, "Você não possui o item necessário para esta operação.", itemId = itemId, oldName = current.name, newName = newName))

                    con.prepareStatement("UPDATE characters SET char_name=? WHERE obj_Id=? AND account_name=? AND COALESCE(online, 0)=0 AND COALESCE(deletetime, 0)=0").use { ps ->
                        ps.setString(1, newName)
                        ps.setInt(2, characterId)
                        ps.setString(3, login)
                        if (ps.executeUpdate() != 1) {
                            return@use rollback(con, RenameCharacterResult(false, "Não foi possível trocar o nome. Atualize o painel e tente novamente.", itemId = itemId, oldName = current.name, newName = newName))
                        }
                    }
                    consumeOneItem(con, item)
                    con.commit()
                    RenameCharacterResult(true, "Nome alterado com sucesso.", itemId, countAccountItem(con, login, itemId), current.name, newName)
                } catch (e: Exception) {
                    runCatching { con.rollback() }
                    RenameCharacterResult(false, "Erro interno ao trocar nome.", itemId = itemId)
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (_: Exception) {
            RenameCharacterResult(false, "Erro interno ao trocar nome.", itemId = itemId)
        }
    }

    fun resetPkAndKarma(login: String, characterId: Int): PkResetResult {
        val itemId = GameApiConfig.accountPkResetItemId
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedCharacterForPkReset(con, login, characterId)
                        ?: return@use rollback(con, PkResetResult(false, "Personagem inválido para esta conta.", itemId = itemId))
                    if (current.online) {
                        return@use rollback(con, PkResetResult(false, "O personagem precisa estar offline para zerar PK e karma.", itemId = itemId, characterId = current.id, characterName = current.name, oldPkKills = current.pkKills, oldKarma = current.karma))
                    }
                    if (current.pkKills <= 0 && current.karma <= 0) {
                        return@use rollback(con, PkResetResult(false, "Este personagem já está com PK e karma zerados.", itemId = itemId, characterId = current.id, characterName = current.name, oldPkKills = current.pkKills, oldKarma = current.karma))
                    }
                    val item = loadConsumableAccountItem(con, login, itemId)
                        ?: return@use rollback(con, PkResetResult(false, "Você não possui o item necessário para esta operação.", itemId = itemId, characterId = current.id, characterName = current.name, oldPkKills = current.pkKills, oldKarma = current.karma))

                    con.prepareStatement("UPDATE characters SET pkkills=0, karma=0 WHERE obj_Id=? AND account_name=? AND COALESCE(online, 0)=0 AND COALESCE(deletetime, 0)=0").use { ps ->
                        ps.setInt(1, characterId)
                        ps.setString(2, login)
                        if (ps.executeUpdate() != 1) {
                            return@use rollback(con, PkResetResult(false, "Não foi possível zerar PK/karma. Atualize o painel e tente novamente.", itemId = itemId, characterId = current.id, characterName = current.name, oldPkKills = current.pkKills, oldKarma = current.karma))
                        }
                    }
                    consumeOneItem(con, item)
                    con.commit()
                    PkResetResult(true, "PK e karma zerados com sucesso.", itemId, countAccountItem(con, login, itemId), current.id, current.name, current.pkKills, current.karma)
                } catch (e: Exception) {
                    runCatching { con.rollback() }
                    PkResetResult(false, "Erro interno ao zerar PK/karma.", itemId = itemId)
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (_: Exception) {
            PkResetResult(false, "Erro interno ao zerar PK/karma.", itemId = itemId)
        }
    }

    fun resetPlayerLocation(login: String, characterId: Int): PlayerResetResult {
        val itemId = GameApiConfig.accountPlayerResetItemId
        val x = GameApiConfig.accountPlayerResetX
        val y = GameApiConfig.accountPlayerResetY
        val z = GameApiConfig.accountPlayerResetZ
        val instanceId = GameApiConfig.accountPlayerResetInstanceId
        return try {
            ConnectionPool.getConnection().use { con ->
                var current = loadOwnedCharacterForPlayerReset(con, login, characterId)
                    ?: return@use PlayerResetResult(false, "Personagem inválido para esta conta.", itemId = itemId, characterId = characterId, x = x, y = y, z = z, instanceId = instanceId)
                var kicked = false
                if (current.online) {
                    if (!current.inPeaceZone || current.inCombat) {
                        return@use PlayerResetResult(false, "Reset de player permitido somente em zona de paz e fora de combate. Vá para uma zona segura e tente novamente. Nenhum item foi consumido.", itemId = itemId, characterId = current.id, characterName = current.name, x = x, y = y, z = z, instanceId = instanceId)
                    }
                    kicked = kickOnlinePlayer(characterId)
                    if (kicked) waitUntilOffline(con, login, characterId)
                    current = loadOwnedCharacterForPlayerReset(con, login, characterId) ?: current
                    if (current.online) {
                        return@use PlayerResetResult(false, "Personagem conectado. O kick foi solicitado; aguarde desconectar e tente novamente. Nenhum item foi consumido.", itemId = itemId, characterId = current.id, characterName = current.name, x = x, y = y, z = z, instanceId = instanceId, kicked = kicked)
                    }
                }

                con.autoCommit = false
                try {
                    val locked = loadOwnedCharacterForPlayerReset(con, login, characterId)
                        ?: return@use rollback(con, PlayerResetResult(false, "Personagem inválido para esta conta.", itemId = itemId, characterId = characterId, x = x, y = y, z = z, instanceId = instanceId, kicked = kicked))
                    if (locked.online) {
                        return@use rollback(con, PlayerResetResult(false, "O personagem precisa estar offline para resetar localização.", itemId = itemId, characterId = locked.id, characterName = locked.name, x = x, y = y, z = z, instanceId = instanceId, kicked = kicked))
                    }
                    val item = loadConsumableAccountItem(con, login, itemId)
                        ?: return@use rollback(con, PlayerResetResult(false, "Você não possui o item necessário para esta operação.", itemId = itemId, characterId = locked.id, characterName = locked.name, x = x, y = y, z = z, instanceId = instanceId, kicked = kicked))

                    con.prepareStatement("UPDATE characters SET x=?, y=?, z=?, isin7sdungeon=0 WHERE obj_Id=? AND account_name=? AND COALESCE(online, 0)=0 AND COALESCE(deletetime, 0)=0").use { ps ->
                        ps.setInt(1, x)
                        ps.setInt(2, y)
                        ps.setInt(3, z)
                        ps.setInt(4, characterId)
                        ps.setString(5, login)
                        if (ps.executeUpdate() != 1) {
                            return@use rollback(con, PlayerResetResult(false, "Não foi possível resetar o personagem. Atualize o painel e tente novamente.", itemId = itemId, characterId = locked.id, characterName = locked.name, x = x, y = y, z = z, instanceId = instanceId, kicked = kicked))
                        }
                    }
                    clearInstanceMemo(con, characterId, instanceId)
                    consumeOneItem(con, item)
                    con.commit()
                    PlayerResetResult(true, "Personagem resetado para Giran com sucesso.", itemId, countAccountItem(con, login, itemId), locked.id, locked.name, x, y, z, instanceId, kicked)
                } catch (e: Exception) {
                    runCatching { con.rollback() }
                    PlayerResetResult(false, "Erro interno ao resetar personagem.", itemId = itemId, x = x, y = y, z = z, instanceId = instanceId, kicked = kicked)
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (_: Exception) {
            PlayerResetResult(false, "Erro interno ao resetar personagem.", itemId = itemId, x = x, y = y, z = z, instanceId = instanceId)
        }
    }

    fun validateClanService(login: String, characterId: Int, serviceId: String): Map<String, Any?> {
        return try {
            ConnectionPool.getConnection().use { con ->
                // Load character + clan
                val charRow = con.prepareStatement(
                    """
                    SELECT c.obj_Id, c.char_name, COALESCE(c.online, 0) AS online,
                           c.clanid, cd.clan_id, cd.clan_name, cd.leader_id, cd.ally_id, cd.ally_name,
                           COALESCE(cd.clan_level, 0) AS clan_level,
                           COALESCE(cd.dissolving_expiry_time, 0) AS dissolving_expiry_time
                    FROM characters c
                    LEFT JOIN clan_data cd ON cd.clan_id = c.clanid
                    WHERE c.obj_Id = ? AND c.account_name = ? AND COALESCE(c.deletetime, 0) = 0
                    LIMIT 1
                    """.trimIndent()
                ).use { ps ->
                    ps.setInt(1, characterId)
                    ps.setString(2, login)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return@use mapOf("ok" to false, "dryRun" to true, "canExecute" to false, "serviceId" to serviceId,
                            "reasons" to listOf("Personagem inválido para esta conta."), "warnings" to emptyList<String>())
                        mapOf(
                            "objId" to rs.getInt("obj_Id"),
                            "charName" to (rs.getString("char_name") ?: ""),
                            "online" to (rs.getInt("online") != 0),
                            "clanId" to rs.getInt("clan_id"),
                            "clanName" to (rs.getString("clan_name") ?: ""),
                            "leaderId" to rs.getInt("leader_id"),
                            "allyId" to rs.getInt("ally_id"),
                            "allyName" to (rs.getString("ally_name") ?: ""),
                            "clanLevel" to rs.getInt("clan_level"),
                            "dissolving" to (rs.getLong("dissolving_expiry_time") > 0L)
                        )
                    }
                } as? Map<String, Any?> ?: return@use mapOf("ok" to false, "dryRun" to true, "canExecute" to false, "serviceId" to serviceId,
                    "reasons" to listOf("Personagem inválido."), "warnings" to emptyList<String>())

                val online = charRow["online"] as? Boolean ?: false
                val worldOnline = isPlayerOnlineInWorld(characterId)
                val isOnline = online || worldOnline
                val clanId = charRow["clanId"] as? Int ?: 0
                val leaderId = charRow["leaderId"] as? Int ?: 0
                val isLeader = leaderId == characterId
                val dissolving = charRow["dissolving"] as? Boolean ?: false

                val reasons = mutableListOf<String>()
                val warnings = mutableListOf<String>()

                if (clanId <= 0) reasons.add("Personagem não está em nenhum clan.")
                if (serviceId != "castle-siege" && !isLeader) reasons.add("Apenas o líder do clan pode usar este serviço.")
                if (isOnline) warnings.add("Personagem está online. Muitos serviços exigem offline.")
                if (dissolving) reasons.add("Clan está em processo de dissolução.")

                when (serviceId) {
                    "rename-clan" -> {
                        if (isOnline) reasons.add("Líder precisa estar offline para renomear clan.")
                    }
                    "rename-ally" -> {
                        val allyId = charRow["allyId"] as? Int ?: 0
                        if (allyId <= 0) reasons.add("Clan não possui aliança para renomear.")
                        if (isOnline) reasons.add("Líder precisa estar offline para alterar aliança.")
                    }
                    "level-up-clan" -> {
                        val level = charRow["clanLevel"] as? Int ?: 0
                        if (level >= 8) reasons.add("Clan já está no nível máximo.")
                        warnings.add("Requer reputação e requisitos configurados por nível.")
                    }
                    "level-down-clan" -> {
                        val level = charRow["clanLevel"] as? Int ?: 0
                        if (level <= 0) reasons.add("Clan já está no nível mínimo.")
                        warnings.add("Pode remover skills e permissões existentes.")
                    }
                    "transfer-leadership" -> {
                        if (isOnline) reasons.add("Líder precisa estar offline para transferir liderança.")
                        warnings.add("Requer informar o ID do membro alvo na execução real.")
                    }
                    "ban-member" -> {
                        warnings.add("Requer informar o ID do membro a ser removido.")
                        warnings.add("Penalidades de clan_join_expiry_time podem ser aplicadas.")
                    }
                    "royal-guard" -> {
                        warnings.add("Gerenciamento de subpledges depende de estrutura interna do servidor.")
                    }
                    "castle-siege" -> {
                        warnings.add("Consulta de castelos e inscrição de siege respeitam janela de registro.")
                    }
                }

                val canExecute = reasons.isEmpty()
                mapOf(
                    "ok" to canExecute,
                    "dryRun" to true,
                    "canExecute" to canExecute,
                    "serviceId" to serviceId,
                    "characterId" to characterId,
                    "characterName" to (charRow["charName"] ?: ""),
                    "clanId" to clanId,
                    "clanName" to (charRow["clanName"] ?: ""),
                    "clanLevel" to (charRow["clanLevel"] ?: 0),
                    "isLeader" to isLeader,
                    "isOnline" to isOnline,
                    "dissolving" to dissolving,
                    "reasons" to reasons,
                    "warnings" to warnings
                )
            }
        } catch (_: Exception) {
            mapOf("ok" to false, "dryRun" to true, "canExecute" to false, "serviceId" to serviceId,
                "reasons" to listOf("Erro interno ao validar serviço de clan."), "warnings" to emptyList<String>())
        }
    }

    fun renameClan(login: String, characterId: Int, newName: String): RenameClanResult {
        if (!CLAN_NAME_REGEX.matches(newName)) {
            return RenameClanResult(false, "Nome do clan inválido: use 3-20 letras, números ou espaço.")
        }
        val itemId = GameApiConfig.accountClanRenameItemId
        val amount = GameApiConfig.accountClanRenameItemAmount
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedClanLeaderForRename(con, login, characterId)
                        ?: return@use rollback(con, RenameClanResult(false, "Personagem inválido, sem clan ou sem liderança para esta conta.", itemId = itemId, characterId = characterId, newName = newName))
                    if (current.online) {
                        return@use rollback(con, RenameClanResult(false, "O líder do clan precisa estar offline para alterar o nome.", itemId = itemId, characterId = current.characterId, characterName = current.characterName, clanId = current.clanId, oldName = current.clanName, newName = newName))
                    }
                    if (current.dissolving) {
                        return@use rollback(con, RenameClanResult(false, "Este clan está em processo de dissolução e não pode ser renomeado.", itemId = itemId, characterId = current.characterId, characterName = current.characterName, clanId = current.clanId, oldName = current.clanName, newName = newName))
                    }
                    if (current.clanName.equals(newName, ignoreCase = true)) {
                        return@use rollback(con, RenameClanResult(false, "Escolha um nome de clan diferente do atual.", itemId = itemId, characterId = current.characterId, characterName = current.characterName, clanId = current.clanId, oldName = current.clanName, newName = newName))
                    }
                    if (clanNameExists(con, newName, current.clanId)) {
                        return@use rollback(con, RenameClanResult(false, "Este nome de clan já está em uso.", itemId = itemId, characterId = current.characterId, characterName = current.characterName, clanId = current.clanId, oldName = current.clanName, newName = newName))
                    }
                    val items = loadConsumableAccountItems(con, login, itemId, amount)
                    val available = items.sumOf { it.count }
                    if (available < amount) {
                        return@use rollback(con, RenameClanResult(false, "Você não possui o item necessário para esta operação.", itemId = itemId, characterId = current.characterId, characterName = current.characterName, clanId = current.clanId, oldName = current.clanName, newName = newName))
                    }

                    con.prepareStatement("UPDATE clan_data SET clan_name=? WHERE clan_id=? AND leader_id=?").use { ps ->
                        ps.setString(1, newName)
                        ps.setInt(2, current.clanId)
                        ps.setInt(3, characterId)
                        if (ps.executeUpdate() != 1) {
                            return@use rollback(con, RenameClanResult(false, "Não foi possível alterar o nome do clan. Atualize o painel e tente novamente.", itemId = itemId, characterId = current.characterId, characterName = current.characterName, clanId = current.clanId, oldName = current.clanName, newName = newName))
                        }
                    }
                    val persistedName = persistedClanName(con, current.clanId)
                    if (persistedName == null || persistedName != newName) {
                        return@use rollback(con, RenameClanResult(false, "A alteração não foi confirmada no banco. Nenhum item foi consumido.", itemId = itemId, characterId = current.characterId, characterName = current.characterName, clanId = current.clanId, oldName = current.clanName, newName = newName, persistedName = persistedName ?: ""))
                    }
                    recordClanAudit(con, login, current.characterId, current.clanId, "rename-clan", current.clanName, persistedName)
                    consumeItems(con, items, amount)
                    con.commit()
                    RenameClanResult(true, "Nome do clan alterado com sucesso.", itemId, countAccountItem(con, login, itemId), current.characterId, current.characterName, current.clanId, current.clanName, newName, persistedName)
                } catch (e: Exception) {
                    runCatching { con.rollback() }
                    RenameClanResult(false, "Erro interno ao alterar nome do clan.", itemId = itemId)
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (_: Exception) {
            RenameClanResult(false, "Erro interno ao alterar nome do clan.", itemId = itemId)
        }
    }

    fun getClanAllianceInfo(login: String, characterId: Int): ClanAllianceInfoResult {
        return try {
            ConnectionPool.getConnection().use { con ->
                val sql = """
                    SELECT c.obj_Id,
                           c.char_name,
                           cd.clan_id,
                           cd.clan_name,
                           cd.leader_id AS clan_leader_id,
                           COALESCE(cd.ally_id, 0) AS ally_id,
                           COALESCE(cd.ally_name, '') AS ally_name,
                           main_cd.clan_name AS main_clan_name,
                           main_leader.char_name AS alliance_leader_name
                    FROM characters c
                    JOIN clan_data cd ON cd.clan_id = c.clanid
                    LEFT JOIN clan_data main_cd ON main_cd.clan_id = cd.ally_id
                    LEFT JOIN characters main_leader ON main_leader.obj_Id = main_cd.leader_id
                    WHERE c.obj_Id = ?
                      AND c.account_name = ?
                      AND COALESCE(c.deletetime, 0) = 0
                    LIMIT 1
                """.trimIndent()
                con.prepareStatement(sql).use { ps ->
                    ps.setInt(1, characterId)
                    ps.setString(2, login)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) {
                            return ClanAllianceInfoResult(false, "Personagem ou clan não encontrado para esta conta.")
                        }
                        val charName = rs.getString("char_name") ?: ""
                        val clanId = rs.getInt("clan_id")
                        val clanName = rs.getString("clan_name") ?: ""
                        val allyId = rs.getInt("ally_id")
                        val allyName = rs.getString("ally_name") ?: ""
                        val mainClanName = rs.getString("main_clan_name") ?: ""
                        val allianceLeaderName = rs.getString("alliance_leader_name") ?: ""

                        val hasAlliance = allyId > 0 && allyName.isNotBlank()
                        if (hasAlliance) {
                            val isAllianceLeader = (clanId == allyId)
                            ClanAllianceInfoResult(
                                ok = true,
                                message = "OK",
                                characterId = characterId,
                                characterName = charName,
                                clanId = clanId,
                                clanName = clanName,
                                hasAlliance = true,
                                currentAllyName = allyName,
                                allianceLeaderName = if (allianceLeaderName.isNotBlank()) allianceLeaderName else charName,
                                allianceLeaderClan = if (mainClanName.isNotBlank()) mainClanName else clanName,
                                isAllianceLeader = isAllianceLeader
                            )
                        } else {
                            ClanAllianceInfoResult(
                                ok = true,
                                message = "OK",
                                characterId = characterId,
                                characterName = charName,
                                clanId = clanId,
                                clanName = clanName,
                                hasAlliance = false,
                                currentAllyName = "Nenhuma (Será criada)",
                                allianceLeaderName = charName,
                                allianceLeaderClan = clanName,
                                isAllianceLeader = true
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            ClanAllianceInfoResult(false, "Erro ao buscar informações da aliança: ${e.message}")
        }
    }

    fun getClanMembersForTransfer(login: String, characterId: Int): ClanMembersResponse {
        return try {
            ConnectionPool.getConnection().use { con ->
                val leaderCheckSql = """
                    SELECT c.obj_Id, c.char_name, cd.clan_id, cd.clan_name, cd.leader_id
                    FROM characters c
                    JOIN clan_data cd ON cd.clan_id = c.clanid
                    WHERE c.obj_Id = ? AND c.account_name = ? AND COALESCE(c.deletetime, 0) = 0
                    LIMIT 1
                """.trimIndent()
                val clanInfo = con.prepareStatement(leaderCheckSql).use { ps ->
                    ps.setInt(1, characterId)
                    ps.setString(2, login)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return@use null
                        Triple(rs.getInt("clan_id"), rs.getString("clan_name") ?: "", rs.getInt("leader_id") == characterId)
                    }
                } ?: return ClanMembersResponse(false, "Personagem inválido ou sem clan para esta conta.")

                val (clanId, clanName, isExecutingLeader) = clanInfo
                if (!isExecutingLeader) {
                    return ClanMembersResponse(
                        ok = false,
                        message = "Apenas o líder atual do clan pode visualizar e transferir a liderança.",
                        clanId = clanId,
                        clanName = clanName,
                        isExecutingLeader = false
                    )
                }

                val membersSql = """
                    SELECT c.obj_Id, c.char_name, COALESCE(c.level, 0) AS level, COALESCE(c.classid, 0) AS classid, COALESCE(c.online, 0) AS online
                    FROM characters c
                    WHERE c.clanid = ? AND c.obj_Id <> ? AND COALESCE(c.deletetime, 0) = 0
                    ORDER BY c.online ASC, c.level DESC, c.char_name ASC
                """.trimIndent()

                val memberList = con.prepareStatement(membersSql).use { ps ->
                    ps.setInt(1, clanId)
                    ps.setInt(2, characterId)
                    ps.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                val mId = rs.getInt("obj_Id")
                                val mName = rs.getString("char_name") ?: ""
                                val level = rs.getInt("level")
                                val classId = rs.getInt("classid")
                                val dbOnline = rs.getInt("online") != 0
                                val worldOnline = isPlayerOnlineInWorld(mId)
                                val online = dbOnline || worldOnline
                                add(ClanMemberListItem(
                                    characterId = mId,
                                    characterName = mName,
                                    level = level,
                                    classId = classId,
                                    online = online,
                                    eligibleForLeadership = !online
                                ))
                            }
                        }
                    }
                }

                ClanMembersResponse(
                    ok = true,
                    message = "OK",
                    clanId = clanId,
                    clanName = clanName,
                    isExecutingLeader = true,
                    members = memberList
                )
            }
        } catch (e: Exception) {
            ClanMembersResponse(false, "Erro ao buscar membros do clan: ${e.message}")
        }
    }

    fun renameAlly(login: String, characterId: Int, newAllyName: String): Map<String, Any?> {
        if (!CLAN_NAME_REGEX.matches(newAllyName)) {
            return mapOf("ok" to false, "message" to "Nome da aliança inválido: use 3-20 letras, números ou espaço.")
        }
        val itemId = GameApiConfig.accountClanRenameAllyItemId
        val amount = 1L
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedClanLeaderForRename(con, login, characterId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança para esta conta.", "itemId" to itemId, "characterId" to characterId, "newName" to newAllyName))
                    if (current.online) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "O líder do clan precisa estar offline para alterar o nome da aliança.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "newName" to newAllyName))
                    }
                    if (current.dissolving) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Este clan está em processo de dissolução e não pode ter a aliança renomeada.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "newName" to newAllyName))
                    }
                    val allyId = persistedClanAllyId(con, current.clanId)
                    val isCreation = allyId == null || allyId <= 0
                    val oldAllyName = if (isCreation) "" else (persistedAllyName(con, allyId) ?: "")
                    if (!isCreation && oldAllyName.equals(newAllyName, ignoreCase = true)) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Escolha um nome de aliança diferente do atual.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "oldName" to oldAllyName, "newName" to newAllyName))
                    }
                    if (allyNameExists(con, newAllyName, if (isCreation) 0 else allyId)) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Este nome de aliança já está em uso.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "oldName" to oldAllyName, "newName" to newAllyName))
                    }
                    val items = loadConsumableAccountItems(con, login, itemId, amount)
                    val available = items.sumOf { it.count }
                    if (available < amount) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Você não possui o item necessário para esta operação.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "oldName" to oldAllyName, "newName" to newAllyName))
                    }

                    if (isCreation) {
                        // Criar aliança: ally_id = clan_id do líder, ally_name = nome escolhido
                        con.prepareStatement("UPDATE clan_data SET ally_id=?, ally_name=? WHERE clan_id=? AND leader_id=?").use { ps ->
                            ps.setInt(1, current.clanId)
                            ps.setString(2, newAllyName)
                            ps.setInt(3, current.clanId)
                            ps.setInt(4, characterId)
                            if (ps.executeUpdate() != 1) {
                                return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível criar a aliança. Atualize o painel e tente novamente.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "newName" to newAllyName))
                            }
                        }
                    } else {
                        // Renomear aliança existente: atualiza todos os clans com mesmo ally_id
                        con.prepareStatement("UPDATE clan_data SET ally_name=? WHERE ally_id=?").use { ps ->
                            ps.setString(1, newAllyName)
                            ps.setInt(2, allyId!!)
                            if (ps.executeUpdate() < 1) {
                                return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível alterar o nome da aliança. Atualize o painel e tente novamente.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "oldName" to oldAllyName, "newName" to newAllyName))
                            }
                        }
                    }
                    val effectiveAllyId = if (isCreation) current.clanId else allyId!!
                    val persistedName = persistedAllyName(con, effectiveAllyId)
                    if (persistedName == null || persistedName != newAllyName) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "A alteração não foi confirmada no banco. Nenhum item foi consumido.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "oldName" to oldAllyName, "newName" to newAllyName, "persistedName" to (persistedName ?: "")))
                    }
                    recordClanAudit(con, login, current.characterId, current.clanId, if (isCreation) "create-ally" else "rename-ally", oldAllyName, persistedName)
                    consumeItems(con, items, amount)
                    con.commit()
                    mapOf(
                        "ok" to true,
                        "message" to if (isCreation) "Aliança criada com sucesso." else "Nome da aliança alterado com sucesso.",
                        "itemId" to itemId,
                        "remaining" to countAccountItem(con, login, itemId),
                        "characterId" to current.characterId,
                        "characterName" to current.characterName,
                        "clanId" to current.clanId,
                        "allyId" to effectiveAllyId,
                        "oldName" to oldAllyName,
                        "newName" to newAllyName,
                        "persistedName" to persistedName,
                        "created" to isCreation
                    )
                } catch (e: Exception) {
                    runCatching { con.rollback() }
                    mapOf("ok" to false, "message" to "Erro interno ao alterar nome da aliança.", "itemId" to itemId)
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (_: Exception) {
            mapOf("ok" to false, "message" to "Erro interno ao alterar nome da aliança.", "itemId" to itemId)
        }
    }

    fun levelUpClan(login: String, characterId: Int): Map<String, Any?> {
        val itemId = GameApiConfig.accountClanLevelUpItemId
        val amount = 1L
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedClanLeaderForRename(con, login, characterId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança para esta conta.", "itemId" to itemId, "characterId" to characterId))
                    if (current.online) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "O líder do clan precisa estar offline para subir o nível do clan.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId))
                    }
                    if (current.dissolving) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Este clan está em processo de dissolução e não pode subir de nível.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId))
                    }
                    val oldLevel = persistedClanLevel(con, current.clanId) ?: -1
                    if (oldLevel < 0) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível ler o nível atual do clan.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId))
                    }
                    if (oldLevel >= 8) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Clan já está no nível máximo (8).", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "oldLevel" to oldLevel))
                    }
                    val items = loadConsumableAccountItems(con, login, itemId, amount)
                    val available = items.sumOf { it.count }
                    if (available < amount) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Você não possui o item necessário para esta operação.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "oldLevel" to oldLevel))
                    }

                    con.prepareStatement("UPDATE clan_data SET clan_level = clan_level + 1 WHERE clan_id=? AND leader_id=? AND COALESCE(clan_level, 0) < 8").use { ps ->
                        ps.setInt(1, current.clanId)
                        ps.setInt(2, characterId)
                        if (ps.executeUpdate() != 1) {
                            return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível subir o nível do clan. Atualize o painel e tente novamente.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "oldLevel" to oldLevel))
                        }
                    }
                    val persistedLevel = persistedClanLevel(con, current.clanId)
                    if (persistedLevel == null || persistedLevel != oldLevel + 1) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "A alteração não foi confirmada no banco. Nenhum item foi consumido.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "oldLevel" to oldLevel, "persistedLevel" to (persistedLevel ?: -1)))
                    }
                    recordClanAudit(con, login, current.characterId, current.clanId, "level-up-clan", oldLevel.toString(), persistedLevel.toString())
                    consumeItems(con, items, amount)
                    con.commit()
                    mapOf(
                        "ok" to true,
                        "message" to "Nível do clan aumentado com sucesso.",
                        "itemId" to itemId,
                        "remaining" to countAccountItem(con, login, itemId),
                        "characterId" to current.characterId,
                        "characterName" to current.characterName,
                        "clanId" to current.clanId,
                        "oldLevel" to oldLevel,
                        "newLevel" to persistedLevel,
                        "persistedLevel" to persistedLevel
                    )
                } catch (e: Exception) {
                    runCatching { con.rollback() }
                    mapOf("ok" to false, "message" to "Erro interno ao subir nível do clan.", "itemId" to itemId)
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (_: Exception) {
            mapOf("ok" to false, "message" to "Erro interno ao subir nível do clan.", "itemId" to itemId)
        }
    }

    fun levelDownClan(login: String, characterId: Int): Map<String, Any?> {
        val itemId = GameApiConfig.accountClanLevelDownItemId
        val amount = 1L
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedClanLeaderForRename(con, login, characterId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança para esta conta.", "itemId" to itemId, "characterId" to characterId))
                    if (current.online) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "O líder do clan precisa estar offline para reduzir o nível do clan.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId))
                    }
                    if (current.dissolving) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Este clan está em processo de dissolução e não pode ter o nível alterado.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId))
                    }
                    val oldLevel = persistedClanLevel(con, current.clanId) ?: -1
                    if (oldLevel < 0) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível ler o nível atual do clan.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId))
                    }
                    if (oldLevel <= 0) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Clan já está no nível mínimo (0).", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "oldLevel" to oldLevel))
                    }
                    val items = loadConsumableAccountItems(con, login, itemId, amount)
                    val available = items.sumOf { it.count }
                    if (available < amount) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Você não possui o item necessário para esta operação.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "oldLevel" to oldLevel))
                    }

                    con.prepareStatement("UPDATE clan_data SET clan_level = clan_level - 1 WHERE clan_id=? AND leader_id=? AND COALESCE(clan_level, 0) > 0").use { ps ->
                        ps.setInt(1, current.clanId)
                        ps.setInt(2, characterId)
                        if (ps.executeUpdate() != 1) {
                            return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível reduzir o nível do clan. Atualize o painel e tente novamente.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "oldLevel" to oldLevel))
                        }
                    }
                    val persistedLevel = persistedClanLevel(con, current.clanId)
                    if (persistedLevel == null || persistedLevel != oldLevel - 1) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "A alteração não foi confirmada no banco. Nenhum item foi consumido.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "oldLevel" to oldLevel, "persistedLevel" to (persistedLevel ?: -1)))
                    }
                    // Remove skills whose required level exceeds the new level.
                    runCatching {
                        con.prepareStatement("DELETE FROM clan_skills WHERE clan_id=? AND skill_level > ?").use { ps ->
                            ps.setInt(1, current.clanId)
                            ps.setInt(2, persistedLevel)
                            ps.executeUpdate()
                        }
                    }
                    recordClanAudit(con, login, current.characterId, current.clanId, "level-down-clan", oldLevel.toString(), persistedLevel.toString())
                    consumeItems(con, items, amount)
                    con.commit()
                    mapOf(
                        "ok" to true,
                        "message" to "Nível do clan reduzido com sucesso.",
                        "itemId" to itemId,
                        "remaining" to countAccountItem(con, login, itemId),
                        "characterId" to current.characterId,
                        "characterName" to current.characterName,
                        "clanId" to current.clanId,
                        "oldLevel" to oldLevel,
                        "newLevel" to persistedLevel,
                        "persistedLevel" to persistedLevel
                    )
                } catch (e: Exception) {
                    runCatching { con.rollback() }
                    mapOf("ok" to false, "message" to "Erro interno ao reduzir nível do clan.", "itemId" to itemId)
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (_: Exception) {
            mapOf("ok" to false, "message" to "Erro interno ao reduzir nível do clan.", "itemId" to itemId)
        }
    }

    fun transferLeadership(login: String, characterId: Int, targetCharacterId: Int): Map<String, Any?> {
        val itemId = GameApiConfig.accountClanTransferLeaderItemId
        val amount = 1L
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedClanLeaderForRename(con, login, characterId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança para esta conta.", "itemId" to itemId, "characterId" to characterId, "targetCharacterId" to targetCharacterId))
                    if (current.online) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "O líder atual precisa estar offline para transferir a liderança.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId))
                    }
                    if (current.dissolving) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Este clan está em processo de dissolução e não pode transferir liderança.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId))
                    }
                    if (targetCharacterId == characterId) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "O líder atual já é o personagem informado.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId))
                    }
                    val target = loadOwnedClanMemberForTransfer(con, targetCharacterId, current.clanId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Membro alvo inválido: não pertence ao clan ou está deletado.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId))
                    if (target.online) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "O membro alvo precisa estar offline para receber a liderança.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId, "targetCharacterName" to target.name))
                    }
                    val items = loadConsumableAccountItems(con, login, itemId, amount)
                    val available = items.sumOf { it.count }
                    if (available < amount) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Você não possui o item necessário para esta operação.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId, "targetCharacterName" to target.name))
                    }

                    con.prepareStatement("UPDATE clan_data SET leader_id=? WHERE clan_id=? AND leader_id=?").use { ps ->
                        ps.setInt(1, targetCharacterId)
                        ps.setInt(2, current.clanId)
                        ps.setInt(3, characterId)
                        if (ps.executeUpdate() != 1) {
                            return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível transferir a liderança. Atualize o painel e tente novamente.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId, "targetCharacterName" to target.name))
                        }
                    }
                    val persistedLeader = persistedClanLeaderId(con, current.clanId)
                    if (persistedLeader == null || persistedLeader != targetCharacterId) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "A transferência não foi confirmada no banco. Nenhum item foi consumido.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId, "targetCharacterName" to target.name, "persistedLeaderId" to (persistedLeader ?: -1)))
                    }
                    recordClanAudit(con, login, current.characterId, current.clanId, "transfer-leadership", characterId.toString(), targetCharacterId.toString())
                    consumeItems(con, items, amount)
                    con.commit()
                    mapOf(
                        "ok" to true,
                        "message" to "Liderança transferida com sucesso.",
                        "itemId" to itemId,
                        "remaining" to countAccountItem(con, login, itemId),
                        "characterId" to current.characterId,
                        "characterName" to current.characterName,
                        "clanId" to current.clanId,
                        "oldLeaderId" to characterId,
                        "targetCharacterId" to targetCharacterId,
                        "targetCharacterName" to target.name,
                        "persistedLeaderId" to persistedLeader
                    )
                } catch (e: Exception) {
                    runCatching { con.rollback() }
                    mapOf("ok" to false, "message" to "Erro interno ao transferir liderança.", "itemId" to itemId)
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (_: Exception) {
            mapOf("ok" to false, "message" to "Erro interno ao transferir liderança.", "itemId" to itemId)
        }
    }

    fun banMember(login: String, characterId: Int, targetCharacterId: Int): Map<String, Any?> {
        val itemId = GameApiConfig.accountClanBanMemberItemId
        val amount = 1L
        val expiry = System.currentTimeMillis() + 86400000L
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedClanLeaderForRename(con, login, characterId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança para esta conta.", "itemId" to itemId, "characterId" to characterId, "targetCharacterId" to targetCharacterId))
                    if (current.online) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "O líder do clan precisa estar offline para banir um membro.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId))
                    }
                    if (targetCharacterId == characterId) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "O líder não pode banir a si mesmo. Use transferência de liderança antes.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId))
                    }
                    val target = loadOwnedClanMemberForTransfer(con, targetCharacterId, current.clanId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Membro alvo inválido: não pertence ao clan, está deletado ou é o líder.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId))
                    if (target.online) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "O membro alvo precisa estar offline para ser banido.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId, "targetCharacterName" to target.name))
                    }
                    val items = loadConsumableAccountItems(con, login, itemId, amount)
                    val available = items.sumOf { it.count }
                    if (available < amount) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Você não possui o item necessário para esta operação.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId, "targetCharacterName" to target.name))
                    }

                    con.prepareStatement("UPDATE characters SET clanid=0, power_grade=0, subpledge=0, apprentice=0, sponsor=0, lvl_joined_academy=0, clan_join_expiry_time=? WHERE obj_Id=? AND clanid=? AND COALESCE(deletetime, 0)=0 AND COALESCE(online, 0)=0").use { ps ->
                        ps.setLong(1, expiry)
                        ps.setInt(2, targetCharacterId)
                        ps.setInt(3, current.clanId)
                        if (ps.executeUpdate() != 1) {
                            return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível banir o membro. Atualize o painel e tente novamente.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId, "targetCharacterName" to target.name))
                        }
                    }
                    val persistedClanId = persistedCharacterClanId(con, targetCharacterId)
                    if (persistedClanId == null || persistedClanId != 0) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "O banimento não foi confirmado no banco. Nenhum item foi consumido.", "itemId" to itemId, "characterId" to current.characterId, "characterName" to current.characterName, "clanId" to current.clanId, "targetCharacterId" to targetCharacterId, "targetCharacterName" to target.name, "persistedClanId" to (persistedClanId ?: -1)))
                    }
                    recordClanAudit(con, login, current.characterId, current.clanId, "ban-member", targetCharacterId.toString(), expiry.toString())
                    consumeItems(con, items, amount)
                    con.commit()
                    mapOf(
                        "ok" to true,
                        "message" to "Membro banido com sucesso.",
                        "itemId" to itemId,
                        "remaining" to countAccountItem(con, login, itemId),
                        "characterId" to current.characterId,
                        "characterName" to current.characterName,
                        "clanId" to current.clanId,
                        "targetCharacterId" to targetCharacterId,
                        "targetCharacterName" to target.name,
                        "expiryTime" to expiry,
                        "persistedClanId" to persistedClanId
                    )
                } catch (e: Exception) {
                    runCatching { con.rollback() }
                    mapOf("ok" to false, "message" to "Erro interno ao banir membro.", "itemId" to itemId)
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (_: Exception) {
            mapOf("ok" to false, "message" to "Erro interno ao banir membro.", "itemId" to itemId)
        }
    }

    fun listRoyalGuards(login: String, characterId: Int): Map<String, Any?> {
        return try {
            ConnectionPool.getConnection().use { con ->
                val current = loadOwnedClanLeaderForRename(con, login, characterId)
                    ?: return@use mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança para esta conta.", "characterId" to characterId)

                var reputationScore = 0
                var clanLevel = 0
                con.prepareStatement("SELECT COALESCE(reputation_score, 0) AS reputation_score, COALESCE(clan_level, 0) AS clan_level FROM clan_data WHERE clan_id = ? LIMIT 1").use { ps ->
                    ps.setInt(1, current.clanId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            reputationScore = rs.getInt("reputation_score")
                            clanLevel = rs.getInt("clan_level")
                        }
                    }
                }

                val subpledges = mutableListOf<Map<String, Any?>>()
                con.prepareStatement(
                    """
                    SELECT cs.sub_pledge_id, cs.name, cs.leader_id,
                           (SELECT char_name FROM characters WHERE obj_Id = cs.leader_id AND COALESCE(deletetime, 0) = 0) AS leader_name
                    FROM clan_subpledges cs
                    WHERE cs.clan_id = ?
                    ORDER BY cs.sub_pledge_id ASC
                    """.trimIndent()
                ).use { ps ->
                    ps.setInt(1, current.clanId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            subpledges.add(mapOf(
                                "subPledgeId" to rs.getInt("sub_pledge_id"),
                                "name" to (rs.getString("name") ?: ""),
                                "leaderId" to rs.getInt("leader_id"),
                                "leaderName" to (rs.getString("leader_name") ?: "")
                            ))
                        }
                    }
                }

                val knownSubIds = subpledges.mapNotNull { (it["subPledgeId"] as? Number)?.toInt() }.toSet()
                con.prepareStatement(
                    "SELECT DISTINCT COALESCE(subpledge, 0) AS subid FROM characters WHERE clanid=? AND COALESCE(deletetime, 0)=0"
                ).use { ps ->
                    ps.setInt(1, current.clanId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val subId = rs.getInt("subid")
                            if (subId != 0 && subId != -1 && !knownSubIds.contains(subId)) {
                                subpledges.add(mapOf(
                                    "subPledgeId" to subId,
                                    "name" to "Royal Guard $subId",
                                    "leaderId" to 0,
                                    "leaderName" to ""
                                ))
                            }
                        }
                    }
                }

                val members = mutableListOf<Map<String, Any?>>()
                con.prepareStatement(
                    "SELECT obj_Id, char_name, COALESCE(level, 0) AS level, COALESCE(classid, 0) AS classid, COALESCE(online, 0) AS online, COALESCE(subpledge, 0) AS subpledge, COALESCE(power_grade, 0) AS power_grade FROM characters WHERE clanid=? AND COALESCE(deletetime, 0)=0 ORDER BY subpledge ASC, char_name ASC"
                ).use { ps ->
                    ps.setInt(1, current.clanId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val mId = rs.getInt("obj_Id")
                            val dbOnline = rs.getInt("online") != 0
                            val worldOnline = isPlayerOnlineInWorld(mId)
                            members.add(mapOf(
                                "characterId" to mId,
                                "characterName" to (rs.getString("char_name") ?: ""),
                                "level" to rs.getInt("level"),
                                "classId" to rs.getInt("classid"),
                                "online" to (dbOnline || worldOnline),
                                "subPledgeId" to rs.getInt("subpledge"),
                                "powerGrade" to rs.getInt("power_grade")
                            ))
                        }
                    }
                }

                mapOf(
                    "ok" to true,
                    "message" to "OK",
                    "characterId" to current.characterId,
                    "characterName" to current.characterName,
                    "clanId" to current.clanId,
                    "clanName" to current.clanName,
                    "reputationScore" to reputationScore,
                    "clanLevel" to clanLevel,
                    "subpledges" to subpledges,
                    "members" to members
                )
            }
        } catch (_: Exception) {
            mapOf("ok" to false, "message" to "Erro interno ao listar royal guards.")
        }
    }

    fun listCastleSiege(login: String, characterId: Int): Map<String, Any?> {
        return try {
            ConnectionPool.getConnection().use { con ->
                val charRow = con.prepareStatement(
                    """
                    SELECT c.obj_Id, c.char_name, COALESCE(c.clanid, 0) AS clanid,
                           cd.clan_name, COALESCE(cd.clan_level, 0) AS clan_level,
                           COALESCE(cd.hasCastle, 0) AS has_castle,
                           (cd.leader_id = c.obj_Id) AS is_leader
                    FROM characters c
                    LEFT JOIN clan_data cd ON cd.clan_id = c.clanid
                    WHERE c.obj_Id = ? AND c.account_name = ? AND COALESCE(c.deletetime, 0) = 0
                    LIMIT 1
                    """.trimIndent()
                ).use { ps ->
                    ps.setInt(1, characterId)
                    ps.setString(2, login)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return@use mapOf("ok" to false, "message" to "Personagem inválido para esta conta.")
                        mapOf(
                            "characterId" to rs.getInt("obj_Id"),
                            "characterName" to (rs.getString("char_name") ?: ""),
                            "clanId" to rs.getInt("clanid"),
                            "clanName" to (rs.getString("clan_name") ?: ""),
                            "clanLevel" to rs.getInt("clan_level"),
                            "hasCastle" to rs.getInt("has_castle"),
                            "isLeader" to rs.getBoolean("is_leader")
                        )
                    }
                }

                val clanId = charRow["clanId"] as Int
                val clanName = charRow["clanName"] as String
                val clanLevel = charRow["clanLevel"] as Int
                val clanHasCastle = charRow["hasCastle"] as Int
                val isLeader = charRow["isLeader"] as Boolean

                val now = System.currentTimeMillis()
                val sdf = java.text.SimpleDateFormat("EEEE, dd/MM 'às' HH:mm", java.util.Locale("pt", "BR"))
                sdf.timeZone = java.util.TimeZone.getDefault()

                val castleNames = mapOf(
                    1 to "Gludio",
                    2 to "Dion",
                    3 to "Giran",
                    4 to "Oren",
                    5 to "Aden",
                    6 to "Innadril",
                    7 to "Goddard",
                    8 to "Rune",
                    9 to "Schuttgart"
                )

                // Load existing registrations for our clan
                var myRegisteredCastleId = 0
                var myRegistrationType = "NONE"
                if (clanId > 0) {
                    con.prepareStatement("SELECT castle_id, type FROM siege_clans WHERE clan_id=? LIMIT 1").use { ps ->
                        ps.setInt(1, clanId)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                myRegisteredCastleId = rs.getInt("castle_id")
                                myRegistrationType = rs.getString("type") ?: "ATTACKER"
                            }
                        }
                    }
                }

                val castles = mutableListOf<Map<String, Any?>>()
                var nextClosestCastle: Map<String, Any?>? = null
                var minFutureSiegeDiff = Long.MAX_VALUE

                val castlesSql = """
                    SELECT c.id, c.currentTaxPercent, c.treasury, c.siegeDate, c.regTimeOver,
                           cd.clan_id AS owner_clan_id, cd.clan_name AS owner_clan_name,
                           COALESCE(cd.clan_level, 0) AS owner_clan_level,
                           COALESCE(cd.ally_name, '') AS owner_ally_name,
                           leader.char_name AS owner_leader_name
                    FROM castle c
                    LEFT JOIN clan_data cd ON cd.hasCastle = c.id
                    LEFT JOIN characters leader ON leader.obj_Id = cd.leader_id
                    ORDER BY c.id ASC
                """.trimIndent()

                con.prepareStatement(castlesSql).use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val cId = rs.getInt("id")
                            val cName = castleNames[cId] ?: "Castelo #$cId"
                            val tax = rs.getInt("currentTaxPercent")
                            val treasury = rs.getLong("treasury")
                            val siegeDate = rs.getLong("siegeDate")
                            val regTimeOverRaw = rs.getString("regTimeOver") ?: "false"
                            val isRegTimeOver = regTimeOverRaw.equals("true", ignoreCase = true) || regTimeOverRaw == "1"

                            val ownerClanId = rs.getInt("owner_clan_id")
                            val ownerClanName = rs.getString("owner_clan_name") ?: ""
                            val ownerClanLevel = rs.getInt("owner_clan_level")
                            val ownerAllyName = rs.getString("owner_ally_name") ?: ""
                            val ownerLeaderName = rs.getString("owner_leader_name") ?: ""

                            val isRegistrationOpen = !isRegTimeOver && siegeDate > now
                            val dateFormatted = if (siegeDate > 0) {
                                try { sdf.format(java.util.Date(siegeDate)).replaceFirstChar { it.uppercase() } } catch (_: Exception) { "A definir" }
                            } else "A definir"

                            val remainingText = if (siegeDate > now) {
                                val diff = (siegeDate - now) / 1000
                                val days = diff / 86400
                                val hours = (diff % 86400) / 3600
                                val mins = (diff % 3600) / 60
                                when {
                                    days > 0 -> "em $days dia(s) e $hours h"
                                    hours > 0 -> "em $hours h e $mins min"
                                    else -> "em $mins min"
                                }
                            } else if (siegeDate > 0) "Em andamento / Imediata" else "A definir"

                            // Load attackers & defenders for this castle
                            val attackers = mutableListOf<Map<String, Any?>>()
                            val defenders = mutableListOf<Map<String, Any?>>()

                            con.prepareStatement(
                                """
                                SELECT sc.clan_id, sc.type, cd.clan_name, COALESCE(cd.clan_level, 0) AS level,
                                       COALESCE(cd.ally_name, '') AS ally_name, leader.char_name AS leader_name
                                FROM siege_clans sc
                                JOIN clan_data cd ON cd.clan_id = sc.clan_id
                                LEFT JOIN characters leader ON leader.obj_Id = cd.leader_id
                                WHERE sc.castle_id = ?
                                ORDER BY cd.clan_name ASC
                                """.trimIndent()
                            ).use { scPs ->
                                scPs.setInt(1, cId)
                                scPs.executeQuery().use { scRs ->
                                    while (scRs.next()) {
                                        val scClanId = scRs.getInt("clan_id")
                                        val scClanName = scRs.getString("clan_name") ?: "Clan #$scClanId"
                                        val scType = scRs.getString("type") ?: "ATTACKER"
                                        val scLevel = scRs.getInt("level")
                                        val scAlly = scRs.getString("ally_name") ?: ""
                                        val scLeader = scRs.getString("leader_name") ?: "N/A"

                                        val item = mapOf(
                                            "clanId" to scClanId,
                                            "clanName" to scClanName,
                                            "clanLevel" to scLevel,
                                            "allyName" to scAlly,
                                            "leaderName" to scLeader,
                                            "type" to scType
                                        )

                                        if (scType.equals("ATTACKER", ignoreCase = true)) {
                                            attackers.add(item)
                                        } else {
                                            defenders.add(item)
                                        }
                                    }
                                }
                            }

                            val isMyClanOwner = clanId > 0 && ownerClanId == clanId
                            val isMyClanAttacker = clanId > 0 && myRegisteredCastleId == cId && myRegistrationType.equals("ATTACKER", ignoreCase = true)
                            val isMyClanDefender = clanId > 0 && myRegisteredCastleId == cId && !myRegistrationType.equals("ATTACKER", ignoreCase = true)

                            val canRegisterAttack = clanId > 0 && isLeader && clanLevel >= 4 &&
                                    clanHasCastle == 0 && myRegisteredCastleId == 0 &&
                                    isRegistrationOpen && !isMyClanOwner

                            val canCancelAttack = clanId > 0 && isLeader && isMyClanAttacker && isRegistrationOpen

                            val castleEntry = mapOf(
                                "castleId" to cId,
                                "castleName" to cName,
                                "currentTaxPercent" to tax,
                                "treasury" to treasury,
                                "siegeDate" to siegeDate,
                                "siegeDateFormatted" to dateFormatted,
                                "remainingText" to remainingText,
                                "isRegistrationOpen" to isRegistrationOpen,
                                "ownerClanId" to ownerClanId,
                                "ownerClanName" to ownerClanName,
                                "ownerClanLevel" to ownerClanLevel,
                                "ownerAllyName" to ownerAllyName,
                                "ownerLeaderName" to ownerLeaderName,
                                "hasOwner" to (ownerClanId > 0),
                                "attackersCount" to attackers.size,
                                "defendersCount" to defenders.size,
                                "attackers" to attackers,
                                "defenders" to defenders,
                                "isMyClanOwner" to isMyClanOwner,
                                "isMyClanAttacker" to isMyClanAttacker,
                                "isMyClanDefender" to isMyClanDefender,
                                "canRegisterAttack" to canRegisterAttack,
                                "canCancelAttack" to canCancelAttack
                            )

                            castles.add(castleEntry)

                            if (siegeDate > now && (siegeDate - now) < minFutureSiegeDiff) {
                                minFutureSiegeDiff = siegeDate - now
                                nextClosestCastle = castleEntry
                            }
                        }
                    }
                }

                if (nextClosestCastle == null && castles.isNotEmpty()) {
                    nextClosestCastle = castles.first()
                }

                mapOf(
                    "ok" to true,
                    "message" to "OK",
                    "characterId" to characterId,
                    "characterName" to charRow["characterName"],
                    "clanId" to clanId,
                    "clanName" to clanName,
                    "clanLevel" to clanLevel,
                    "clanHasCastle" to clanHasCastle,
                    "isLeader" to isLeader,
                    "myRegisteredCastleId" to myRegisteredCastleId,
                    "myRegistrationType" to myRegistrationType,
                    "nextClosestSiege" to nextClosestCastle,
                    "castles" to castles
                )
            }
        } catch (e: Exception) {
            mapOf("ok" to false, "message" to "Erro interno ao listar castle siege: ${e.message}")
        }
    }


    fun moveRoyalGuardMember(login: String, characterId: Int, targetCharacterId: Int, targetSubPledgeId: Int): Map<String, Any?> {
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedClanLeaderForRename(con, login, characterId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança."))
                    if (current.online) return@use rollback(con, mapOf("ok" to false, "message" to "Líder precisa estar offline."))
                    if (targetCharacterId == characterId) return@use rollback(con, mapOf("ok" to false, "message" to "Não pode mover o líder de subpledge."))
                    // Verify target belongs to same clan
                    val targetCheck = con.prepareStatement("SELECT obj_Id, char_name, COALESCE(subpledge,0) AS subpledge FROM characters WHERE obj_Id=? AND clanid=? AND COALESCE(deletetime,0)=0 LIMIT 1").use { ps ->
                        ps.setInt(1, targetCharacterId); ps.setInt(2, current.clanId)
                        ps.executeQuery().use { rs -> if (rs.next()) Triple(rs.getInt(1), rs.getString("char_name") ?: "", rs.getInt("subpledge")) else null }
                    } ?: return@use rollback(con, mapOf("ok" to false, "message" to "Membro alvo não pertence ao clan."))
                    val oldSubPledge = targetCheck.third
                    if (oldSubPledge == targetSubPledgeId) return@use rollback(con, mapOf("ok" to false, "message" to "Membro já está nesta ordem."))
                    // Verify target subpledge exists (0 = main pledge is always valid)
                    if (targetSubPledgeId != 0) {
                        val exists = con.prepareStatement("SELECT 1 FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id=? LIMIT 1").use { ps ->
                            ps.setInt(1, current.clanId); ps.setInt(2, targetSubPledgeId); ps.executeQuery().use { it.next() }
                        }
                        if (!exists) return@use rollback(con, mapOf("ok" to false, "message" to "Ordem destino não existe neste clan."))
                    }
                    con.prepareStatement("UPDATE characters SET subpledge=? WHERE obj_Id=? AND clanid=?").use { ps ->
                        ps.setInt(1, targetSubPledgeId); ps.setInt(2, targetCharacterId); ps.setInt(3, current.clanId)
                        if (ps.executeUpdate() != 1) return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível mover o membro."))
                    }
                    // Confirm
                    val persisted = con.prepareStatement("SELECT COALESCE(subpledge,0) FROM characters WHERE obj_Id=?").use { ps -> ps.setInt(1, targetCharacterId); ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else -1 } }
                    if (persisted != targetSubPledgeId) return@use rollback(con, mapOf("ok" to false, "message" to "Movimentação não confirmada no banco."))
                    recordClanAudit(con, login, characterId, current.clanId, "royal-guard-move", "$targetCharacterId:subpledge=$oldSubPledge", "$targetCharacterId:subpledge=$targetSubPledgeId")
                    con.commit()
                    mapOf("ok" to true, "message" to "Membro ${targetCheck.second} movido para ordem $targetSubPledgeId.", "targetName" to targetCheck.second, "oldSubPledge" to oldSubPledge, "newSubPledge" to targetSubPledgeId)
                } catch (_: Exception) { runCatching { con.rollback() }; mapOf("ok" to false, "message" to "Erro interno.") }
                finally { runCatching { con.autoCommit = true } }
            }
        } catch (_: Exception) { mapOf("ok" to false, "message" to "Erro interno.") }
    }

    fun createRoyalGuard(login: String, characterId: Int, name: String): Map<String, Any?> {
        val safeName = name.trim()
        if (!ROYAL_NAME_REGEX.matches(safeName)) return mapOf("ok" to false, "message" to "Nome da Royal inválido: use 3-45 letras, números ou espaço.")
        return try { ConnectionPool.getConnection().use { con ->
            con.autoCommit = false
            try {
                val current = loadOwnedClanLeaderForRename(con, login, characterId) ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança."))
                if (current.online) return@use rollback(con, mapOf("ok" to false, "message" to "Líder precisa estar offline para criar Royal Guard."))
                val existsName = con.prepareStatement("SELECT 1 FROM clan_subpledges WHERE clan_id=? AND LOWER(name)=LOWER(?) LIMIT 1").use { ps -> ps.setInt(1, current.clanId); ps.setString(2, safeName); ps.executeQuery().use { it.next() } }
                if (existsName) return@use rollback(con, mapOf("ok" to false, "message" to "Já existe uma Royal/ordem com este nome."))
                val used = mutableSetOf<Int>()
                con.prepareStatement("SELECT sub_pledge_id FROM clan_subpledges WHERE clan_id=?").use { ps -> ps.setInt(1, current.clanId); ps.executeQuery().use { rs -> while (rs.next()) used.add(rs.getInt(1)) } }
                val newId = listOf(100, 200, 1001, 1002, 2001, 2002).firstOrNull { it !in used } ?: return@use rollback(con, mapOf("ok" to false, "message" to "Todas as Royals/ordens disponíveis já foram criadas."))
                con.prepareStatement("INSERT INTO clan_subpledges (clan_id, sub_pledge_id, name, leader_id) VALUES (?, ?, ?, 0)").use { ps -> ps.setInt(1, current.clanId); ps.setInt(2, newId); ps.setString(3, safeName); if (ps.executeUpdate() != 1) return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível criar a Royal.")) }
                val persisted = con.prepareStatement("SELECT name FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id=? LIMIT 1").use { ps -> ps.setInt(1, current.clanId); ps.setInt(2, newId); ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null } }
                if (persisted != safeName) return@use rollback(con, mapOf("ok" to false, "message" to "Criação não confirmada no banco."))
                recordClanAudit(con, login, characterId, current.clanId, "royal-guard-create", "", "$newId:$safeName")
                con.commit(); refreshClanRuntime(current.clanId)
                mapOf("ok" to true, "message" to "Royal Guard criada com sucesso.", "subPledgeId" to newId, "name" to safeName)
            } catch (_: Exception) { runCatching { con.rollback() }; mapOf("ok" to false, "message" to "Erro interno ao criar Royal Guard.") } finally { runCatching { con.autoCommit = true } }
        } } catch (_: Exception) { mapOf("ok" to false, "message" to "Erro interno ao criar Royal Guard.") }
    }

    fun deleteRoyalGuard(login: String, characterId: Int, subPledgeId: Int, moveToSubPledgeId: Int): Map<String, Any?> {
        if (subPledgeId == 0 || subPledgeId == -1) return mapOf("ok" to false, "message" to "Esta ordem não pode ser deletada.")
        if (subPledgeId == moveToSubPledgeId) return mapOf("ok" to false, "message" to "Escolha uma ordem diferente para mover os membros.")
        return try { ConnectionPool.getConnection().use { con ->
            con.autoCommit = false
            try {
                val current = loadOwnedClanLeaderForRename(con, login, characterId) ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança."))
                if (current.online) return@use rollback(con, mapOf("ok" to false, "message" to "Líder precisa estar offline para deletar Royal Guard."))
                
                val oldNameRow = con.prepareStatement("SELECT name FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id=? LIMIT 1").use { ps -> ps.setInt(1, current.clanId); ps.setInt(2, subPledgeId); ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null } }
                val oldName = oldNameRow ?: "Royal Guard $subPledgeId"

                if (moveToSubPledgeId != 0) {
                    val destExists = con.prepareStatement("SELECT 1 FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id=? LIMIT 1").use { ps -> ps.setInt(1, current.clanId); ps.setInt(2, moveToSubPledgeId); ps.executeQuery().use { it.next() } }
                    if (!destExists) return@use rollback(con, mapOf("ok" to false, "message" to "Ordem destino não existe neste clan."))
                }
                val moved = con.prepareStatement("UPDATE characters SET subpledge=? WHERE clanid=? AND COALESCE(subpledge,0)=?").use { ps -> ps.setInt(1, moveToSubPledgeId); ps.setInt(2, current.clanId); ps.setInt(3, subPledgeId); ps.executeUpdate() }
                con.prepareStatement("DELETE FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id=?").use { ps -> ps.setInt(1, current.clanId); ps.setInt(2, subPledgeId); ps.executeUpdate() }

                recordClanAudit(con, login, characterId, current.clanId, "royal-guard-delete", "$subPledgeId:$oldName", "moved=$moved:to=$moveToSubPledgeId")
                con.commit(); refreshClanRuntime(current.clanId)
                mapOf("ok" to true, "message" to "Royal Guard deletada com sucesso. $moved membro(s) movido(s).", "deletedSubPledgeId" to subPledgeId, "moveToSubPledgeId" to moveToSubPledgeId, "movedMembers" to moved)
            } catch (_: Exception) { runCatching { con.rollback() }; mapOf("ok" to false, "message" to "Erro interno ao deletar Royal Guard.") } finally { runCatching { con.autoCommit = true } }
        } } catch (_: Exception) { mapOf("ok" to false, "message" to "Erro interno ao deletar Royal Guard.") }
    }

    fun renameRoyalGuardSubPledge(login: String, characterId: Int, subPledgeId: Int, newName: String): Map<String, Any?> {
        val safeName = newName.trim()
        if (!ROYAL_NAME_REGEX.matches(safeName)) {
            return mapOf("ok" to false, "message" to "Nome da ordem/subpledge inválido: use 3-45 letras, números ou espaço.")
        }
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedClanLeaderForRename(con, login, characterId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança."))
                    if (current.online) return@use rollback(con, mapOf("ok" to false, "message" to "Líder precisa estar offline para renomear subpledge."))
                    
                    val existsName = con.prepareStatement("SELECT 1 FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id <> ? AND LOWER(name)=LOWER(?) LIMIT 1").use { ps ->
                        ps.setInt(1, current.clanId)
                        ps.setInt(2, subPledgeId)
                        ps.setString(3, safeName)
                        ps.executeQuery().use { it.next() }
                    }
                    if (existsName) return@use rollback(con, mapOf("ok" to false, "message" to "Já existe uma ordem com este nome no clan."))

                    val existingRow = con.prepareStatement("SELECT name FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id=? LIMIT 1").use { ps ->
                        ps.setInt(1, current.clanId)
                        ps.setInt(2, subPledgeId)
                        ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) ?: "" else null }
                    }

                    if (existingRow == null) {
                        con.prepareStatement("INSERT INTO clan_subpledges (clan_id, sub_pledge_id, name, leader_id) VALUES (?, ?, ?, 0)").use { ps ->
                            ps.setInt(1, current.clanId)
                            ps.setInt(2, subPledgeId)
                            ps.setString(3, safeName)
                            if (ps.executeUpdate() != 1) return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível criar registro da subpledge."))
                        }
                    } else {
                        con.prepareStatement("UPDATE clan_subpledges SET name=? WHERE clan_id=? AND sub_pledge_id=?").use { ps ->
                            ps.setString(1, safeName)
                            ps.setInt(2, current.clanId)
                            ps.setInt(3, subPledgeId)
                            if (ps.executeUpdate() != 1) return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível renomear a subpledge."))
                        }
                    }

                    recordClanAudit(con, login, characterId, current.clanId, "royal-guard-rename", "$subPledgeId:${existingRow ?: ""}", "$subPledgeId:$safeName")
                    con.commit()
                    refreshClanRuntime(current.clanId)
                    mapOf("ok" to true, "message" to "Nome da ordem alterado para '$safeName' com sucesso.", "subPledgeId" to subPledgeId, "name" to safeName)
                } catch (_: Exception) {
                    runCatching { con.rollback() }
                    mapOf("ok" to false, "message" to "Erro interno ao renomear subpledge.")
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (_: Exception) {
            mapOf("ok" to false, "message" to "Erro interno ao renomear subpledge.")
        }
    }

    fun assignRoyalGuardCaptain(login: String, characterId: Int, subPledgeId: Int, targetCharacterId: Int): Map<String, Any?> {
        if (subPledgeId == 0) {
            return mapOf("ok" to false, "message" to "O Clan Principal é liderado pelo Líder do Clan.")
        }
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedClanLeaderForRename(con, login, characterId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança."))
                    if (targetCharacterId == characterId) return@use rollback(con, mapOf("ok" to false, "message" to "O Líder do Clan não pode ser nomeado capitão de uma ordem."))

                    val targetCheck = con.prepareStatement("SELECT obj_Id, char_name FROM characters WHERE obj_Id=? AND clanid=? AND COALESCE(deletetime,0)=0 LIMIT 1").use { ps ->
                        ps.setInt(1, targetCharacterId); ps.setInt(2, current.clanId)
                        ps.executeQuery().use { rs -> if (rs.next()) rs.getString("char_name") ?: "" else null }
                    } ?: return@use rollback(con, mapOf("ok" to false, "message" to "Membro alvo não pertence ao clan."))

                    val existingRow = con.prepareStatement("SELECT name FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id=? LIMIT 1").use { ps ->
                        ps.setInt(1, current.clanId); ps.setInt(2, subPledgeId); ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) ?: "" else null }
                    }

                    if (existingRow == null) {
                        val defaultName = if (subPledgeId == -1) "Academia" else "Ordem $subPledgeId"
                        con.prepareStatement("INSERT INTO clan_subpledges (clan_id, sub_pledge_id, name, leader_id) VALUES (?, ?, ?, ?)").use { ps ->
                            ps.setInt(1, current.clanId); ps.setInt(2, subPledgeId); ps.setString(3, defaultName); ps.setInt(4, targetCharacterId)
                            if (ps.executeUpdate() != 1) return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível nomear o capitão."))
                        }
                    } else {
                        con.prepareStatement("UPDATE clan_subpledges SET leader_id=? WHERE clan_id=? AND sub_pledge_id=?").use { ps ->
                            ps.setInt(1, targetCharacterId); ps.setInt(2, current.clanId); ps.setInt(3, subPledgeId)
                            if (ps.executeUpdate() != 1) return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível nomear o capitão."))
                        }
                    }

                    recordClanAudit(con, login, characterId, current.clanId, "royal-guard-assign-captain", "$subPledgeId", "$targetCharacterId:$targetCheck")
                    con.commit()
                    refreshClanRuntime(current.clanId)
                    mapOf("ok" to true, "message" to "Membro $targetCheck nomeado Capitão com sucesso.", "subPledgeId" to subPledgeId, "targetName" to targetCheck)
                } catch (_: Exception) {
                    runCatching { con.rollback() }
                    mapOf("ok" to false, "message" to "Erro interno ao nomear capitão.")
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (_: Exception) {
            mapOf("ok" to false, "message" to "Erro interno ao nomear capitão.")
        }
    }

    fun removeRoyalGuardCaptain(login: String, characterId: Int, subPledgeId: Int): Map<String, Any?> {
        if (subPledgeId == 0) return mapOf("ok" to false, "message" to "Ordem inválida.")
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedClanLeaderForRename(con, login, characterId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança."))

                    val existingLeader = con.prepareStatement("SELECT leader_id FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id=? LIMIT 1").use { ps ->
                        ps.setInt(1, current.clanId); ps.setInt(2, subPledgeId); ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                    }

                    con.prepareStatement("UPDATE clan_subpledges SET leader_id=0 WHERE clan_id=? AND sub_pledge_id=?").use { ps ->
                        ps.setInt(1, current.clanId); ps.setInt(2, subPledgeId)
                        ps.executeUpdate()
                    }

                    recordClanAudit(con, login, characterId, current.clanId, "royal-guard-remove-captain", "$subPledgeId", "$existingLeader")
                    con.commit()
                    refreshClanRuntime(current.clanId)
                    mapOf("ok" to true, "message" to "Capitão removido da ordem com sucesso.", "subPledgeId" to subPledgeId)
                } catch (_: Exception) {
                    runCatching { con.rollback() }
                    mapOf("ok" to false, "message" to "Erro interno ao remover capitão.")
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (_: Exception) {
            mapOf("ok" to false, "message" to "Erro interno ao remover capitão.")
        }
    }

    fun getClanInviteCandidates(login: String, characterId: Int): Map<String, Any?> {
        return try {
            ConnectionPool.getConnection().use { con ->
                val current = loadOwnedClanLeaderForRename(con, login, characterId)
                    ?: return mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança.")

                val clanPenaltyTime = con.prepareStatement(
                    "SELECT COALESCE(char_penalty_expiry_time, 0) FROM clan_data WHERE clan_id=? LIMIT 1"
                ).use { ps ->
                    ps.setInt(1, current.clanId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
                }
                val now = System.currentTimeMillis()
                val hasPenalty = clanPenaltyTime > now
                val penaltyExpiryFormatted = if (hasPenalty) {
                    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(clanPenaltyTime))
                } else ""

                val diffMs = clanPenaltyTime - now
                val remainingText = if (hasPenalty && diffMs > 0) {
                    val totalSecs = diffMs / 1000
                    val hours = totalSecs / 3600
                    val mins = (totalSecs % 3600) / 60
                    val secs = totalSecs % 60
                    if (hours > 0) "$hours hora(s) e $mins min" else if (mins > 0) "$mins minuto(s) e $secs s" else "$secs segundo(s)"
                } else ""

                val candidates = mutableListOf<Map<String, Any?>>()
                con.prepareStatement(
                    """
                    SELECT obj_Id, char_name, COALESCE(level, 0) AS level, COALESCE(classid, 0) AS classid, COALESCE(online, 0) AS online
                    FROM characters
                    WHERE COALESCE(clanid, 0) = 0 AND COALESCE(deletetime, 0) = 0
                    ORDER BY online DESC, level DESC, char_name ASC
                    LIMIT 300
                    """.trimIndent()
                ).use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val cId = rs.getInt("obj_Id")
                            val isOnline = isPlayerOnlineInWorld(cId)
                            candidates.add(mapOf(
                                "characterId" to cId,
                                "characterName" to (rs.getString("char_name") ?: ""),
                                "level" to rs.getInt("level"),
                                "classId" to rs.getInt("classid"),
                                "online" to isOnline
                            ))
                        }
                    }
                }

                mapOf(
                    "ok" to true,
                    "clanName" to current.clanName,
                    "clanPenaltyActive" to hasPenalty,
                    "clanPenaltyExpiryTime" to clanPenaltyTime,
                    "clanPenaltyFormatted" to penaltyExpiryFormatted,
                    "clanPenaltyRemainingText" to remainingText,
                    "candidates" to candidates
                )
            }
        } catch (_: Exception) {
            mapOf("ok" to false, "message" to "Erro ao carregar lista de jogadores sem clan.")
        }
    }

    fun sendClanInvite(login: String, characterId: Int, targetCharacterId: Int, targetSubPledgeId: Int = 0): Map<String, Any?> {
        return try {
            ConnectionPool.getConnection().use { con ->
                val current = loadOwnedClanLeaderForRename(con, login, characterId)
                if (current == null) {
                    return mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou você não é o líder principal do clan.")
                }

                val clanId = current.clanId
                val clanName = current.clanName

                val targetInfo = con.prepareStatement(
                    "SELECT obj_Id, char_name, COALESCE(clanid, 0) AS clanid FROM characters WHERE obj_Id=? AND COALESCE(deletetime, 0)=0 LIMIT 1"
                ).use { ps ->
                    ps.setInt(1, targetCharacterId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            Triple(rs.getInt("obj_Id"), rs.getString("char_name") ?: "", rs.getInt("clanid"))
                        } else null
                    }
                }
                if (targetInfo == null) {
                    return mapOf("ok" to false, "message" to "Jogador alvo não encontrado no banco de dados.")
                }

                val (targetId, targetName, targetClanId) = targetInfo
                if (targetClanId > 0) {
                    return mapOf("ok" to false, "message" to "O jogador $targetName já possui clan.")
                }

                // 1. Check Clan Recruitment Penalty
                val clanPenaltyTime = con.prepareStatement(
                    "SELECT COALESCE(char_penalty_expiry_time, 0) FROM clan_data WHERE clan_id=? LIMIT 1"
                ).use { ps ->
                    ps.setInt(1, clanId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
                }
                val now = System.currentTimeMillis()
                if (clanPenaltyTime > now) {
                    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm")
                    val formatted = sdf.format(java.util.Date(clanPenaltyTime))
                    return mapOf("ok" to false, "message" to "Seu clan possui penalidade ativa para recrutamento até $formatted (devido à remoção/saída recente de membro).")
                }

                // 2. Check Target Player Clan Join Penalty
                val playerPenaltyTime = con.prepareStatement(
                    "SELECT COALESCE(clan_join_expiry_time, 0) FROM characters WHERE obj_Id=? LIMIT 1"
                ).use { ps ->
                    ps.setInt(1, targetId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
                }
                if (playerPenaltyTime > now) {
                    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm")
                    val formatted = sdf.format(java.util.Date(playerPenaltyTime))
                    return mapOf("ok" to false, "message" to "O jogador $targetName possui penalidade ativa para entrar em clans até $formatted.")
                }

                // 3. Check Subpledge Capacity
                val currentCount = con.prepareStatement(
                    "SELECT COUNT(*) FROM characters WHERE clanid=? AND COALESCE(subpledge, 0)=? AND COALESCE(deletetime, 0)=0"
                ).use { ps ->
                    ps.setInt(1, clanId); ps.setInt(2, targetSubPledgeId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                }
                val maxAllowed = when (targetSubPledgeId) {
                    0 -> 80
                    -1 -> 20
                    else -> 20
                }
                if (currentCount >= maxAllowed) {
                    return mapOf("ok" to false, "message" to "A ordem/clan selecionado já atingiu o limite máximo de $maxAllowed membros.")
                }

                val subPledgeName = if (targetSubPledgeId != 0) {
                    con.prepareStatement("SELECT name FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id=? LIMIT 1").use { ps ->
                        ps.setInt(1, clanId); ps.setInt(2, targetSubPledgeId)
                        ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
                    } ?: if (targetSubPledgeId == -1) "Academia" else "Ordem $targetSubPledgeId"
                } else clanName

                val worldClass = Class.forName("ext.mods.gameserver.model.World")
                val playerClass = Class.forName("ext.mods.gameserver.model.actor.Player")
                val handlerClass = Class.forName("ext.mods.gameserver.network.clientpackets.SiteClanInviteHandler")

                val world = worldClass.getMethod("getInstance").invoke(null)
                var targetPlayer = worldClass.getMethod("getPlayer", Int::class.javaPrimitiveType).invoke(world, targetId)
                if (targetPlayer == null) {
                    // Fallback to name lookup
                    targetPlayer = worldClass.getMethod("getPlayer", String::class.java).invoke(world, targetName)
                }

                if (targetPlayer == null) {
                    return mapOf("ok" to false, "message" to "O jogador $targetName não está online no servidor de jogo no momento.")
                }

                val sendInviteMethod = handlerClass.getMethod(
                    "sendInvite",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    playerClass,
                    String::class.java,
                    String::class.java
                )
                val failureReason = sendInviteMethod.invoke(null, characterId, clanId, targetSubPledgeId, targetPlayer, clanName, subPledgeName) as? String

                if (failureReason != null) {
                    return mapOf("ok" to false, "message" to failureReason)
                }

                recordClanAudit(con, login, characterId, clanId, "royal-guard-invite-send", "$targetSubPledgeId", "$targetId:$targetName")
                mapOf("ok" to true, "message" to "Convite enviado com sucesso para $targetName in-game! A caixa de diálogo (Sim / Não) foi exibida na tela do jogador.", "targetName" to targetName)
            }
        } catch (e: Exception) {
            println("[SITE-API-INVITE-EXCEPTION] ${e.stackTraceToString()}")
            mapOf("ok" to false, "message" to "Erro ao enviar convite in-game: ${e.message ?: "falha na execução"}")
        }
    }

    private fun determineClanRole(con: Connection, clanId: Int, characterId: Int): String {
        return runCatching {
            val isLeader = con.prepareStatement("SELECT 1 FROM clan_data WHERE clan_id=? AND leader_id=? LIMIT 1").use { ps ->
                ps.setInt(1, clanId); ps.setInt(2, characterId); ps.executeQuery().use { it.next() }
            }
            if (isLeader) return "LEADER"

            val isCaptain = con.prepareStatement("SELECT 1 FROM clan_subpledges WHERE clan_id=? AND leader_id=? LIMIT 1").use { ps ->
                ps.setInt(1, clanId); ps.setInt(2, characterId); ps.executeQuery().use { it.next() }
            }
            if (isCaptain) return "CAPTAIN"

            "MEMBER"
        }.getOrDefault("MEMBER")
    }

    fun clanChat(login: String, characterId: Int, since: Long): Map<String, Any?> {
        return try { ConnectionPool.getConnection().use { con ->
            val member = loadOwnedClanMemberForChat(con, login, characterId) ?: return@use mapOf("ok" to false, "message" to "Personagem inválido ou sem clan para esta conta.")
            val sinceSafe = since.coerceAtLeast(0L)
            val messages = ext.mods.gameapi.clan.ClanChatRing.fetchSince(member.clanId, sinceSafe)
                .map { e -> mapOf("seq" to e.seq, "time" to e.time, "characterId" to e.characterId, "characterName" to e.characterName, "text" to e.text, "role" to e.role, "channel" to e.channel) }
            mapOf("ok" to true, "message" to "OK", "clanId" to member.clanId, "clanName" to member.clanName, "allyId" to member.allyId, "messages" to messages, "latestSeq" to (messages.lastOrNull()?.get("seq") ?: sinceSafe), "allowOffline" to GameApiConfig.clanChatAllowOffline, "cooldownMs" to GameApiConfig.clanChatCooldownMs, "maxPerMinute" to GameApiConfig.clanChatMaxPerMinute)
        } } catch (_: Exception) { mapOf("ok" to false, "message" to "Erro interno ao consultar chat do clan.") }
    }

    fun sendClanChat(login: String, characterId: Int, text: String): Map<String, Any?> {
        val safeText = sanitizeChatText(text)
        if (safeText.isBlank()) return mapOf("ok" to false, "message" to "Mensagem vazia.")
        return try { ConnectionPool.getConnection().use { con ->
            val member = loadOwnedClanMemberForChat(con, login, characterId) ?: return@use mapOf("ok" to false, "message" to "Personagem inválido ou sem clan para esta conta.")
            val role = determineClanRole(con, member.clanId, member.characterId)

            if (member.allyId > 0) {
                val allyClans = getAllianceClanIds(con, member.allyId)
                val targetClans = (allyClans + member.clanId).distinct()
                for (cid in targetClans) {
                    ext.mods.gameapi.clan.ClanChatRing.append(cid, member.characterId, member.characterName, safeText, role, if (cid == member.clanId) "CLAN" else "ALLY")
                }
                broadcastClanChat(member.clanId, member.characterId, member.characterName, safeText, role)
                broadcastAllianceChat(member.allyId, member.characterId, member.characterName, safeText, role)
            } else {
                ext.mods.gameapi.clan.ClanChatRing.append(member.clanId, member.characterId, member.characterName, safeText, role, "CLAN")
                broadcastClanChat(member.clanId, member.characterId, member.characterName, safeText, role)
            }

            mapOf("ok" to true, "message" to "Mensagem enviada.", "clanId" to member.clanId, "characterName" to member.characterName, "text" to safeText, "role" to role, "allyId" to member.allyId)
        } } catch (_: Exception) { mapOf("ok" to false, "message" to "Erro interno ao enviar mensagem do clan.") }
    }

    fun getClanWars(login: String, characterId: Int): Map<String, Any?> {
        return try {
            ConnectionPool.getConnection().use { con ->
                val current = loadOwnedClanMemberForWar(con, login, characterId)
                val clanId = current?.clanId ?: 0
                val now = System.currentTimeMillis()

                val membersCount = if (clanId > 0) {
                    con.prepareStatement(
                        "SELECT COUNT(*) FROM characters WHERE clanid = ? AND COALESCE(deletetime, 0) = 0"
                    ).use { ps ->
                        ps.setInt(1, clanId)
                        ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                    }
                } else 0

                val activeWars = mutableListOf<Map<String, Any?>>()
                val incomingDeclarations = mutableListOf<Map<String, Any?>>()
                val penalties = mutableListOf<Map<String, Any?>>()
                val mutualTargetIds = mutableSetOf<Int>()
                val declaredTargetIds = mutableSetOf<Int>()
                val incomingAttackerIds = mutableSetOf<Int>()
                val penaltyTargetIds = mutableMapOf<Int, String>()

                if (clanId > 0) {
                    val declaredWarsSql = """
                        SELECT cw.clan2 AS target_id, cw.expiry_time, cd.clan_name, COALESCE(cd.clan_level, 0) AS level,
                               COALESCE(cd.ally_name, '') AS ally_name, leader.char_name AS leader_name,
                               (SELECT COUNT(*) FROM characters m WHERE m.clanid = cw.clan2 AND COALESCE(m.deletetime, 0) = 0) AS members_count,
                               EXISTS(SELECT 1 FROM clan_wars mutual WHERE mutual.clan1 = cw.clan2 AND mutual.clan2 = ? AND (mutual.expiry_time = 0 OR mutual.expiry_time IS NULL)) AS is_mutual
                        FROM clan_wars cw
                        LEFT JOIN clan_data cd ON cd.clan_id = cw.clan2
                        LEFT JOIN characters leader ON leader.obj_Id = cd.leader_id
                        WHERE cw.clan1 = ?
                    """.trimIndent()

                    con.prepareStatement(declaredWarsSql).use { ps ->
                        ps.setInt(1, clanId)
                        ps.setInt(2, clanId)
                        ps.executeQuery().use { rs ->
                            while (rs.next()) {
                                val targetId = rs.getInt("target_id")
                                val expiryTime = rs.getLong("expiry_time")
                                val targetName = rs.getString("clan_name") ?: "Clan #$targetId"
                                val level = rs.getInt("level")
                                val allyName = rs.getString("ally_name") ?: ""
                                val leaderName = rs.getString("leader_name") ?: "N/A"
                                val mCount = rs.getInt("members_count")
                                val isMutual = rs.getBoolean("is_mutual")

                                if (expiryTime > now) {
                                    val diffMs = expiryTime - now
                                    val totalSecs = diffMs / 1000
                                    val days = totalSecs / 86400
                                    val hours = (totalSecs % 86400) / 3600
                                    val mins = (totalSecs % 3600) / 60
                                    val remainingFormatted = when {
                                        days > 0 -> "$days dia(s) e $hours h"
                                        hours > 0 -> "$hours h e $mins min"
                                        else -> "$mins min"
                                    }
                                    penaltyTargetIds[targetId] = remainingFormatted
                                    penalties.add(mapOf(
                                        "targetClanId" to targetId,
                                        "targetClanName" to targetName,
                                        "targetClanLevel" to level,
                                        "targetClanLeader" to leaderName,
                                        "targetClanMembers" to mCount,
                                        "targetClanAlly" to allyName,
                                        "penaltyExpiryTime" to expiryTime,
                                        "remainingText" to remainingFormatted
                                    ))
                                } else {
                                    if (isMutual) mutualTargetIds.add(targetId) else declaredTargetIds.add(targetId)
                                    activeWars.add(mapOf(
                                        "targetClanId" to targetId,
                                        "targetClanName" to targetName,
                                        "targetClanLevel" to level,
                                        "targetClanLeader" to leaderName,
                                        "targetClanMembers" to mCount,
                                        "targetClanAlly" to allyName,
                                        "isMutual" to isMutual,
                                        "type" to if (isMutual) "MUTUAL" else "DECLARED"
                                    ))
                                }
                            }
                        }
                    }

                    val incomingWarsSql = """
                        SELECT cw.clan1 AS attacker_id, cw.expiry_time, cd.clan_name, COALESCE(cd.clan_level, 0) AS level,
                               COALESCE(cd.ally_name, '') AS ally_name, leader.char_name AS leader_name,
                               (SELECT COUNT(*) FROM characters m WHERE m.clanid = cw.clan1 AND COALESCE(m.deletetime, 0) = 0) AS members_count
                        FROM clan_wars cw
                        LEFT JOIN clan_data cd ON cd.clan_id = cw.clan1
                        LEFT JOIN characters leader ON leader.obj_Id = cd.leader_id
                        WHERE cw.clan2 = ? AND (cw.expiry_time = 0 OR cw.expiry_time <= ?)
                          AND NOT EXISTS (SELECT 1 FROM clan_wars our WHERE our.clan1 = ? AND our.clan2 = cw.clan1 AND (our.expiry_time = 0 OR our.expiry_time <= ?))
                    """.trimIndent()

                    con.prepareStatement(incomingWarsSql).use { ps ->
                        ps.setInt(1, clanId)
                        ps.setLong(2, now)
                        ps.setInt(3, clanId)
                        ps.setLong(4, now)
                        ps.executeQuery().use { rs ->
                            while (rs.next()) {
                                val attackerId = rs.getInt("attacker_id")
                                val attackerName = rs.getString("clan_name") ?: "Clan #$attackerId"
                                val level = rs.getInt("level")
                                val allyName = rs.getString("ally_name") ?: ""
                                val leaderName = rs.getString("leader_name") ?: "N/A"
                                val mCount = rs.getInt("members_count")

                                incomingAttackerIds.add(attackerId)
                                incomingDeclarations.add(mapOf(
                                    "attackerClanId" to attackerId,
                                    "attackerClanName" to attackerName,
                                    "attackerClanLevel" to level,
                                    "attackerClanLeader" to leaderName,
                                    "attackerClanMembers" to mCount,
                                    "attackerClanAlly" to allyName
                                ))
                            }
                        }
                    }
                }

                // 3. Load all clans in the server for the War Radar
                val castleNames = mapOf(
                    1 to "Gludio", 2 to "Dion", 3 to "Giran", 4 to "Oren",
                    5 to "Aden", 6 to "Innadril", 7 to "Goddard", 8 to "Rune", 9 to "Schuttgart"
                )

                val serverClans = mutableListOf<Map<String, Any?>>()
                val serverClansSql = """
                    SELECT cd.clan_id, cd.clan_name, COALESCE(cd.clan_level, 0) AS clan_level,
                           COALESCE(cd.reputation_score, 0) AS reputation_score,
                           COALESCE(cd.ally_id, 0) AS ally_id, COALESCE(cd.ally_name, '') AS ally_name,
                           COALESCE(cd.hasCastle, 0) AS has_castle,
                           COALESCE(cd.dissolving_expiry_time, 0) AS dissolving_expiry_time,
                           leader.char_name AS leader_name,
                           (SELECT COUNT(*) FROM characters m WHERE m.clanid = cd.clan_id AND COALESCE(m.deletetime, 0) = 0) AS members_count,
                           (SELECT COUNT(*) FROM clan_wars w WHERE (w.clan1 = cd.clan_id OR w.clan2 = cd.clan_id) AND (w.expiry_time = 0 OR w.expiry_time <= ?)) AS active_wars_count
                    FROM clan_data cd
                    LEFT JOIN characters leader ON leader.obj_Id = cd.leader_id
                    WHERE cd.clan_name IS NOT NULL AND cd.clan_name <> ''
                    ORDER BY cd.clan_level DESC, cd.reputation_score DESC, cd.clan_name ASC
                    LIMIT 200
                """.trimIndent()

                con.prepareStatement(serverClansSql).use { ps ->
                    ps.setLong(1, now)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val cId = rs.getInt("clan_id")
                            val cName = rs.getString("clan_name") ?: "Clan #$cId"
                            val cLevel = rs.getInt("clan_level")
                            val cRep = rs.getInt("reputation_score")
                            val cAllyId = rs.getInt("ally_id")
                            val cAllyName = rs.getString("ally_name") ?: ""
                            val cCastleId = rs.getInt("has_castle")
                            val cCastleName = castleNames[cCastleId] ?: ""
                            val cDissolving = rs.getLong("dissolving_expiry_time") > 0L
                            val cLeader = rs.getString("leader_name") ?: "N/A"
                            val cMembers = rs.getInt("members_count")
                            val cActiveWars = rs.getInt("active_wars_count")

                            val isEligible = cLevel >= 3 && cMembers >= 15 && !cDissolving

                            val relation = when {
                                clanId > 0 && cId == clanId -> "OWN"
                                clanId > 0 && current?.allyId != null && current.allyId > 0 && cAllyId == current.allyId -> "ALLY"
                                mutualTargetIds.contains(cId) -> "WAR_MUTUAL"
                                declaredTargetIds.contains(cId) -> "WAR_DECLARED"
                                incomingAttackerIds.contains(cId) -> "WAR_INCOMING"
                                penaltyTargetIds.containsKey(cId) -> "PENALTY"
                                else -> "NEUTRAL"
                            }

                            val penaltyRemaining = penaltyTargetIds[cId] ?: ""

                            val canDeclare = clanId > 0 && current?.isLeader == true &&
                                    current.clanLevel >= 3 && membersCount >= 15 &&
                                    activeWars.size < 30 &&
                                    (isEligible || incomingAttackerIds.contains(cId)) &&
                                    (relation == "NEUTRAL" || relation == "WAR_INCOMING")

                            serverClans.add(mapOf(
                                "clanId" to cId,
                                "clanName" to cName,
                                "clanLevel" to cLevel,
                                "reputation" to cRep,
                                "allyId" to cAllyId,
                                "allyName" to cAllyName,
                                "castleId" to cCastleId,
                                "castleName" to cCastleName,
                                "leaderName" to cLeader,
                                "membersCount" to cMembers,
                                "activeWarsCount" to cActiveWars,
                                "isEligible" to isEligible,
                                "isDissolving" to cDissolving,
                                "relation" to relation,
                                "penaltyRemaining" to penaltyRemaining,
                                "canDeclare" to canDeclare
                            ))
                        }
                    }
                }

                // 4. Load all active wars in the server
                val serverWars = mutableListOf<Map<String, Any?>>()
                val serverWarsSql = """
                    SELECT cw.clan1 AS c1_id, c1.clan_name AS c1_name, COALESCE(c1.clan_level, 0) AS c1_level, COALESCE(c1.ally_name, '') AS c1_ally,
                           cw.clan2 AS c2_id, c2.clan_name AS c2_name, COALESCE(c2.clan_level, 0) AS c2_level, COALESCE(c2.ally_name, '') AS c2_ally,
                           cw.expiry_time,
                           EXISTS(SELECT 1 FROM clan_wars mutual WHERE mutual.clan1 = cw.clan2 AND mutual.clan2 = cw.clan1 AND (mutual.expiry_time = 0 OR mutual.expiry_time <= ?)) AS is_mutual
                    FROM clan_wars cw
                    JOIN clan_data c1 ON c1.clan_id = cw.clan1
                    JOIN clan_data c2 ON c2.clan_id = cw.clan2
                    WHERE (cw.expiry_time = 0 OR cw.expiry_time <= ?)
                    ORDER BY is_mutual DESC, c1.clan_name ASC
                    LIMIT 200
                """.trimIndent()

                con.prepareStatement(serverWarsSql).use { ps ->
                    ps.setLong(1, now)
                    ps.setLong(2, now)
                    ps.executeQuery().use { rs ->
                        val seenMutualPairs = mutableSetOf<String>()
                        while (rs.next()) {
                            val c1Id = rs.getInt("c1_id")
                            val c2Id = rs.getInt("c2_id")
                            val isMutual = rs.getBoolean("is_mutual")

                            if (isMutual) {
                                val pairKey = if (c1Id < c2Id) "$c1Id:$c2Id" else "$c2Id:$c1Id"
                                if (seenMutualPairs.contains(pairKey)) continue
                                seenMutualPairs.add(pairKey)
                            }

                            serverWars.add(mapOf(
                                "clan1Id" to c1Id,
                                "clan1Name" to (rs.getString("c1_name") ?: "Clan #$c1Id"),
                                "clan1Level" to rs.getInt("c1_level"),
                                "clan1Ally" to (rs.getString("c1_ally") ?: ""),
                                "clan2Id" to c2Id,
                                "clan2Name" to (rs.getString("c2_name") ?: "Clan #$c2Id"),
                                "clan2Level" to rs.getInt("c2_level"),
                                "clan2Ally" to (rs.getString("c2_ally") ?: ""),
                                "isMutual" to isMutual
                            ))
                        }
                    }
                }

                // 5. Load Alliances in the server
                val allianceMap = mutableMapOf<Int, MutableMap<String, Any?>>()
                val allySql = """
                    SELECT cd.ally_id, cd.ally_name, cd.clan_id, cd.clan_name, COALESCE(cd.clan_level, 0) AS clan_level,
                           leader.char_name AS leader_name,
                           (SELECT COUNT(*) FROM characters m WHERE m.clanid = cd.clan_id AND COALESCE(m.deletetime, 0) = 0) AS members_count
                    FROM clan_data cd
                    LEFT JOIN characters leader ON leader.obj_Id = cd.leader_id
                    WHERE cd.ally_id > 0 AND cd.ally_name IS NOT NULL AND cd.ally_name <> ''
                    ORDER BY cd.ally_name ASC, cd.clan_level DESC
                """.trimIndent()

                con.prepareStatement(allySql).use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val aId = rs.getInt("ally_id")
                            val aName = rs.getString("ally_name") ?: "Aliança #$aId"
                            val cId = rs.getInt("clan_id")
                            val cName = rs.getString("clan_name") ?: "Clan #$cId"
                            val cLevel = rs.getInt("clan_level")
                            val cLeader = rs.getString("leader_name") ?: "N/A"
                            val cMembers = rs.getInt("members_count")

                            val entry = allianceMap.getOrPut(aId) {
                                mutableMapOf(
                                    "allyId" to aId,
                                    "allyName" to aName,
                                    "totalMembers" to 0,
                                    "clans" to mutableListOf<Map<String, Any?>>()
                                )
                            }
                            entry["totalMembers"] = (entry["totalMembers"] as Int) + cMembers
                            @Suppress("UNCHECKED_CAST")
                            val clansList = entry["clans"] as MutableList<Map<String, Any?>>
                            clansList.add(mapOf(
                                "clanId" to cId,
                                "clanName" to cName,
                                "clanLevel" to cLevel,
                                "leaderName" to cLeader,
                                "membersCount" to cMembers
                            ))
                        }
                    }
                }

                val serverAlliances = allianceMap.values.toList()

                mapOf(
                    "ok" to true,
                    "clanId" to clanId,
                    "clanName" to (current?.clanName ?: ""),
                    "clanLevel" to (current?.clanLevel ?: 0),
                    "membersCount" to membersCount,
                    "isLeader" to (current?.isLeader ?: false),
                    "maxWars" to 30,
                    "activeWarsCount" to activeWars.size,
                    "activeWars" to activeWars,
                    "incomingDeclarations" to incomingDeclarations,
                    "penalties" to penalties,
                    "serverClans" to serverClans,
                    "serverWars" to serverWars,
                    "serverAlliances" to serverAlliances
                )
            }
        } catch (e: Exception) {
            mapOf("ok" to false, "message" to "Erro ao carregar radar de guerras do clan: ${e.message}")
        }
    }

    fun declareClanWar(login: String, characterId: Int, targetClanName: String): Map<String, Any?> {
        val normTarget = targetClanName.trim()
        if (normTarget.isEmpty()) {
            return mapOf("ok" to false, "message" to "Informe o nome do clan alvo.")
        }

        return try {
            ConnectionPool.getConnection().use { con ->
                val current = loadOwnedClanMemberForWar(con, login, characterId)
                    ?: return mapOf("ok" to false, "message" to "Personagem inválido ou não pertence a um clan.")

                if (!current.isLeader) {
                    return mapOf("ok" to false, "message" to "Apenas o líder do clan pode declarar guerras.")
                }

                val clanId = current.clanId
                val now = System.currentTimeMillis()

                val membersCount = con.prepareStatement(
                    "SELECT COUNT(*) FROM characters WHERE clanid = ? AND COALESCE(deletetime, 0) = 0"
                ).use { ps ->
                    ps.setInt(1, clanId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                }

                if (current.clanLevel < 3 || membersCount < 15) {
                    return mapOf("ok" to false, "message" to "Seu clan precisa ser no mínimo Nível 3 e possuir ao menos 15 membros para declarar guerra.")
                }

                val currentWarsCount = con.prepareStatement(
                    "SELECT COUNT(*) FROM clan_wars WHERE clan1 = ? AND (expiry_time = 0 OR expiry_time <= ?)"
                ).use { ps ->
                    ps.setInt(1, clanId)
                    ps.setLong(2, now)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                }

                if (currentWarsCount >= 30) {
                    return mapOf("ok" to false, "message" to "Seu clan já atingiu o limite máximo de 30 guerras simultâneas.")
                }

                val targetClan = con.prepareStatement(
                    """
                    SELECT cd.clan_id, cd.clan_name, COALESCE(cd.clan_level, 0) AS level,
                           COALESCE(cd.ally_id, 0) AS ally_id, COALESCE(cd.dissolving_expiry_time, 0) AS dissolving_expiry_time,
                           (SELECT COUNT(*) FROM characters m WHERE m.clanid = cd.clan_id AND COALESCE(m.deletetime, 0) = 0) AS members_count
                    FROM clan_data cd
                    WHERE LOWER(cd.clan_name) = LOWER(?)
                    LIMIT 1
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, normTarget)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            ClanTargetInfo(
                                clanId = rs.getInt("clan_id"),
                                clanName = rs.getString("clan_name") ?: normTarget,
                                level = rs.getInt("level"),
                                allyId = rs.getInt("ally_id"),
                                dissolvingExpiryTime = rs.getLong("dissolving_expiry_time"),
                                membersCount = rs.getInt("members_count")
                            )
                        } else null
                    }
                } ?: return mapOf("ok" to false, "message" to "Clan '$normTarget' não encontrado.")

                if (targetClan.clanId == clanId) {
                    return mapOf("ok" to false, "message" to "Você não pode declarar guerra contra o seu próprio clan.")
                }

                if (current.allyId != 0 && current.allyId == targetClan.allyId) {
                    return mapOf("ok" to false, "message" to "Você não pode declarar guerra contra um clan da mesma Aliança.")
                }

                if (targetClan.dissolvingExpiryTime > 0) {
                    return mapOf("ok" to false, "message" to "Não é possível declarar guerra contra um clan em processo de dissolução.")
                }

                val isTargetAttacker = con.prepareStatement(
                    "SELECT 1 FROM clan_wars WHERE clan1 = ? AND clan2 = ? AND (expiry_time = 0 OR expiry_time <= ?)"
                ).use { ps ->
                    ps.setInt(1, targetClan.clanId)
                    ps.setInt(2, clanId)
                    ps.setLong(3, now)
                    ps.executeQuery().use { rs -> rs.next() }
                }

                if (!isTargetAttacker && (targetClan.level < 3 || targetClan.membersCount < 15)) {
                    return mapOf("ok" to false, "message" to "O clan rival '${targetClan.clanName}' não possui os requisitos mínimos (Nível 3 e 15 membros).")
                }

                con.prepareStatement(
                    "SELECT expiry_time FROM clan_wars WHERE clan1 = ? AND clan2 = ?"
                ).use { ps ->
                    ps.setInt(1, clanId)
                    ps.setInt(2, targetClan.clanId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            val exp = rs.getLong("expiry_time")
                            if (exp > now) {
                                val diffDays = ((exp - now) / 86400000L) + 1
                                return mapOf("ok" to false, "message" to "Há uma penalidade de trégua ativa com '${targetClan.clanName}'. Aguarde $diffDays dia(s).")
                            } else if (exp == 0L) {
                                return mapOf("ok" to false, "message" to "Guerra já declarada contra '${targetClan.clanName}'.")
                            }
                        }
                    }
                }

                runCatching {
                    val clanTableClass = Class.forName("ext.mods.gameserver.data.sql.ClanTable")
                    val instance = clanTableClass.getMethod("getInstance").invoke(null)
                    clanTableClass.getMethod("storeClansWars", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        .invoke(instance, clanId, targetClan.clanId)
                }

                con.prepareStatement(DatabaseDialect.upsert("clan_wars", "clan1, clan2, expiry_time", "?, ?, 0", "clan1, clan2", "expiry_time")).use { ps ->
                    ps.setInt(1, clanId)
                    ps.setInt(2, targetClan.clanId)
                    ps.executeUpdate()
                }

                recordClanAudit(con, login, characterId, clanId, "clan-war-declare", targetClan.clanId.toString(), targetClan.clanName)
                mapOf("ok" to true, "message" to "Guerra declarada com sucesso contra o clan '${targetClan.clanName}'!")
            }
        } catch (e: Exception) {
            mapOf("ok" to false, "message" to "Erro ao declarar guerra: ${e.message}")
        }
    }

    fun stopClanWar(login: String, characterId: Int, targetClanId: Int): Map<String, Any?> {
        return try {
            ConnectionPool.getConnection().use { con ->
                val current = loadOwnedClanMemberForWar(con, login, characterId)
                    ?: return mapOf("ok" to false, "message" to "Personagem inválido ou não pertence a um clan.")

                if (!current.isLeader) {
                    return mapOf("ok" to false, "message" to "Apenas o líder do clan pode encerrar ou render-se de guerras.")
                }

                val clanId = current.clanId

                val exists = con.prepareStatement(
                    "SELECT 1 FROM clan_wars WHERE clan1 = ? AND clan2 = ?"
                ).use { ps ->
                    ps.setInt(1, clanId)
                    ps.setInt(2, targetClanId)
                    ps.executeQuery().use { rs -> rs.next() }
                }

                if (!exists) {
                    return mapOf("ok" to false, "message" to "Seu clan não está em guerra com este clan.")
                }

                runCatching {
                    val clanTableClass = Class.forName("ext.mods.gameserver.data.sql.ClanTable")
                    val instance = clanTableClass.getMethod("getInstance").invoke(null)
                    clanTableClass.getMethod("deleteClansWars", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        .invoke(instance, clanId, targetClanId)
                }

                val penaltyExpiry = System.currentTimeMillis() + (5L * 86400000L)
                con.prepareStatement(
                    "UPDATE clan_wars SET expiry_time = ? WHERE clan1 = ? AND clan2 = ?"
                ).use { ps ->
                    ps.setLong(1, penaltyExpiry)
                    ps.setInt(2, clanId)
                    ps.setInt(3, targetClanId)
                    ps.executeUpdate()
                }

                recordClanAudit(con, login, characterId, clanId, "clan-war-stop", targetClanId.toString(), penaltyExpiry.toString())
                mapOf("ok" to true, "message" to "Guerra encerrada com sucesso. Penalidade de trégua aplicada por 5 dias.")
            }
        } catch (e: Exception) {
            mapOf("ok" to false, "message" to "Erro ao encerrar guerra: ${e.message}")
        }
    }

    fun registerSiege(login: String, characterId: Int, castleId: Int, type: String): Map<String, Any?> {
        val castleNames = mapOf(
            1 to "Gludio", 2 to "Dion", 3 to "Giran", 4 to "Oren", 5 to "Aden",
            6 to "Innadril", 7 to "Goddard", 8 to "Rune", 9 to "Schuttgart"
        )
        val castleName = castleNames[castleId] ?: "Castelo #$castleId"
        val actionType = type.uppercase().trim()

        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedClanLeaderForRename(con, login, characterId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança."))
                    if (current.clanId <= 0) return@use rollback(con, mapOf("ok" to false, "message" to "Sem clan."))

                    // Verify castle exists and registration is open
                    val (cId, isRegTimeOver, siegeDate) = con.prepareStatement("SELECT id, regTimeOver, siegeDate FROM castle WHERE id=?").use { ps ->
                        ps.setInt(1, castleId)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                val rawReg = rs.getString("regTimeOver") ?: "false"
                                val isOver = rawReg.equals("true", ignoreCase = true) || rawReg == "1"
                                Triple(rs.getInt("id"), isOver, rs.getLong("siegeDate"))
                            } else null
                        }
                    } ?: return@use rollback(con, mapOf("ok" to false, "message" to "Castelo $castleName não encontrado."))

                    val now = System.currentTimeMillis()
                    if (isRegTimeOver || (siegeDate > 0 && now >= siegeDate)) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "O período de registro de siege para $castleName está encerrado."))
                    }

                    if (actionType == "CANCEL" || actionType == "UNREGISTER" || actionType == "REMOVE") {
                        val alreadyCastleId = con.prepareStatement("SELECT castle_id FROM siege_clans WHERE clan_id=? LIMIT 1").use { ps ->
                            ps.setInt(1, current.clanId); ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
                        }
                        if (alreadyCastleId == null) {
                            return@use rollback(con, mapOf("ok" to false, "message" to "Seu clan não possui registro de siege ativo para cancelar."))
                        }

                        val targetCancelCastleId = if (alreadyCastleId > 0) alreadyCastleId else castleId
                        con.prepareStatement("DELETE FROM siege_clans WHERE clan_id=?").use { ps ->
                            ps.setInt(1, current.clanId); ps.executeUpdate()
                        }

                        recordClanAudit(con, login, characterId, current.clanId, "castle-siege-cancel", "castle=$targetCancelCastleId", "CANCEL")
                        con.commit()

                        // Runtime sync in-memory AFTER commit to prevent SQLite database lock
                        runCatching {
                            val castleManagerClass = Class.forName("ext.mods.gameserver.data.manager.CastleManager")
                            val cm = castleManagerClass.getMethod("getInstance").invoke(null)
                            val castleObj = castleManagerClass.getMethod("getCastleById", Int::class.javaPrimitiveType).invoke(cm, targetCancelCastleId)
                            if (castleObj != null) {
                                val clanTableClass = Class.forName("ext.mods.gameserver.data.sql.ClanTable")
                                val ct = clanTableClass.getMethod("getInstance").invoke(null)
                                val clanObj = clanTableClass.getMethod("getClan", Int::class.javaPrimitiveType).invoke(ct, current.clanId)
                                if (clanObj != null) {
                                    val siegeObj = castleObj.javaClass.getMethod("getSiege").invoke(castleObj)
                                    if (siegeObj != null) {
                                        val mapMethod = runCatching { siegeObj.javaClass.getMethod("getRegisteredClans") }.getOrNull()
                                        if (mapMethod != null) {
                                            val map = mapMethod.invoke(siegeObj) as? MutableMap<Any, Any>
                                            map?.remove(clanObj)
                                        }
                                    }
                                }
                            }
                        }

                        return@use mapOf("ok" to true, "message" to "Registro de ataque no Castelo de $castleName cancelado com sucesso.", "castleId" to castleId, "cancelled" to true)
                    }

                    // Check Clan Level & Castle Ownership
                    val (clanLevel, hasCastle) = con.prepareStatement("SELECT COALESCE(clan_level, 0), COALESCE(hasCastle, 0) FROM clan_data WHERE clan_id=?").use { ps ->
                        ps.setInt(1, current.clanId)
                        ps.executeQuery().use { rs -> if (rs.next()) Pair(rs.getInt(1), rs.getInt(2)) else Pair(0, 0) }
                    }

                    if (clanLevel < 4) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Seu clan precisa ser Nível 4 ou superior para registrar em sieges (Nível atual: $clanLevel)."))
                    }

                    if (hasCastle > 0 && hasCastle != castleId) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Clans que já possuem um castelo não podem se registrar para atacar outros castelos."))
                    }

                    if (hasCastle == castleId) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Seu clan é o proprietário deste castelo e já defende automaticamente."))
                    }

                    // Check if already registered on ANOTHER castle
                    val otherCastleId = con.prepareStatement("SELECT castle_id FROM siege_clans WHERE clan_id=? AND castle_id<>? LIMIT 1").use { ps ->
                        ps.setInt(1, current.clanId); ps.setInt(2, castleId); ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                    }
                    if (otherCastleId > 0) {
                        val otherName = castleNames[otherCastleId] ?: "#$otherCastleId"
                        return@use rollback(con, mapOf("ok" to false, "message" to "Seu clan já está registrado na siege do Castelo de $otherName. Cancele o registro anterior primeiro."))
                    }

                    val safeType = if (actionType in setOf("DEFENDER", "PENDING")) "DEFENDER" else "ATTACKER"

                    // Upsert registration
                    con.prepareStatement("DELETE FROM siege_clans WHERE castle_id=? AND clan_id=?").use { ps ->
                        ps.setInt(1, castleId); ps.setInt(2, current.clanId); ps.executeUpdate()
                    }
                    con.prepareStatement("INSERT INTO siege_clans (castle_id, clan_id, type) VALUES (?, ?, ?)").use { ps ->
                        ps.setInt(1, castleId); ps.setInt(2, current.clanId); ps.setString(3, safeType); ps.executeUpdate()
                    }

                    recordClanAudit(con, login, characterId, current.clanId, "castle-siege-register", "castle=$castleId", "type=$safeType")
                    con.commit()

                    // Runtime sync in-memory AFTER commit to prevent SQLite database lock
                    runCatching {
                        val castleManagerClass = Class.forName("ext.mods.gameserver.data.manager.CastleManager")
                        val cm = castleManagerClass.getMethod("getInstance").invoke(null)
                        val castleObj = castleManagerClass.getMethod("getCastleById", Int::class.javaPrimitiveType).invoke(cm, castleId)
                        if (castleObj != null) {
                            val clanTableClass = Class.forName("ext.mods.gameserver.data.sql.ClanTable")
                            val ct = clanTableClass.getMethod("getInstance").invoke(null)
                            val clanObj = clanTableClass.getMethod("getClan", Int::class.javaPrimitiveType).invoke(ct, current.clanId)
                            val siegeSideClass = Class.forName("ext.mods.gameserver.enums.SiegeSide")
                            val sideEnum = java.lang.Enum.valueOf(siegeSideClass as Class<out Enum<*>>, if (safeType == "ATTACKER") "ATTACKER" else "PENDING")
                            if (clanObj != null) {
                                val siegeObj = castleObj.javaClass.getMethod("getSiege").invoke(castleObj)
                                if (siegeObj != null) {
                                    val mapMethod = runCatching { siegeObj.javaClass.getMethod("getRegisteredClans") }.getOrNull()
                                    if (mapMethod != null) {
                                        val map = mapMethod.invoke(siegeObj) as? MutableMap<Any, Any>
                                        map?.put(clanObj, sideEnum)
                                    }
                                }
                            }
                        }
                    }

                    mapOf("ok" to true, "message" to "Clan registrado com sucesso como $safeType no Castelo de $castleName!", "castleId" to castleId, "type" to safeType)
                } catch (e: Exception) { runCatching { con.rollback() }; mapOf("ok" to false, "message" to "Erro ao processar registro: ${e.message}") }
                finally { runCatching { con.autoCommit = true } }
            }
        } catch (e: Exception) { mapOf("ok" to false, "message" to "Erro interno ao registrar siege: ${e.message}") }
    }

    private data class RenameCharacterRecord(val id: Int, val name: String, val online: Boolean)
    private data class PkResetCharacterRecord(val id: Int, val name: String, val online: Boolean, val pkKills: Int, val karma: Int)
    private data class PlayerResetCharacterRecord(val id: Int, val name: String, val online: Boolean, val inPeaceZone: Boolean, val inCombat: Boolean)
    private data class ClanLeaderRecord(val characterId: Int, val characterName: String, val online: Boolean, val clanId: Int, val clanName: String, val dissolving: Boolean)
    private data class ClanWarMemberRecord(val characterId: Int, val characterName: String, val online: Boolean, val clanId: Int, val clanName: String, val clanLevel: Int, val allyId: Int, val isLeader: Boolean, val dissolving: Boolean)
    private data class ClanTargetInfo(val clanId: Int, val clanName: String, val level: Int, val allyId: Int, val dissolvingExpiryTime: Long, val membersCount: Int)
    private data class OnlinePlayerState(val inPeaceZone: Boolean, val inCombat: Boolean)
    private data class ConsumableItemRecord(val objectId: Int, val count: Long)
    private data class ClanChatEntry(val seq: Long, val time: Long, val clanId: Int, val characterId: Int, val characterName: String, val text: String)

    private val clanChatSeq = AtomicLong(0L)
    private val clanChatLog = ArrayDeque<ClanChatEntry>()
    private const val MAX_CLAN_CHAT_LOG = 300

    private fun <T> rollback(con: Connection, result: T): T {
        runCatching { con.rollback() }
        return result
    }

    private val CHAR_NAME_REGEX = Regex("^[A-Za-z0-9]{2,16}$")
    private val CLAN_NAME_REGEX = Regex("^[A-Za-z0-9](?:[A-Za-z0-9 ]{1,18}[A-Za-z0-9])$")
    private val ROYAL_NAME_REGEX = Regex("^[A-Za-z0-9](?:[A-Za-z0-9 ]{1,43}[A-Za-z0-9])$")
    private val UNSAFE_UNICODE = Regex("[\u200B-\u200F\u202A-\u202E\u2060-\u2064\uFEFF\u00AD\u034F\u180E]")

    private fun sanitizeChatText(text: String): String {
        return text
            .trim()
            .replace(Regex("[\r\n\t]+"), " ")
            .replace(UNSAFE_UNICODE, "")
            .replace(Regex("\\s{2,}"), " ")
            .take(140)
    }

    private fun loadOwnedCharacterForRename(con: Connection, login: String, characterId: Int): RenameCharacterRecord? {
        return con.prepareStatement(
            "SELECT obj_Id, char_name, COALESCE(online, 0) AS online FROM characters WHERE obj_Id=? AND account_name=? LIMIT 1"
        ).use { ps ->
            ps.setInt(1, characterId)
            ps.setString(2, login)
            ps.executeQuery().use { rs ->
                if (rs.next()) RenameCharacterRecord(rs.getInt(1), rs.getString(2) ?: "", rs.getInt(3) != 0) else null
            }
        }
    }

    private fun loadOwnedCharacterForPkReset(con: Connection, login: String, characterId: Int): PkResetCharacterRecord? {
        return con.prepareStatement(
            "SELECT obj_Id, char_name, COALESCE(online, 0) AS online, COALESCE(pkkills, 0) AS pkkills, COALESCE(karma, 0) AS karma FROM characters WHERE obj_Id=? AND account_name=? AND COALESCE(deletetime, 0)=0 LIMIT 1"
        ).use { ps ->
            ps.setInt(1, characterId)
            ps.setString(2, login)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    PkResetCharacterRecord(
                        rs.getInt("obj_Id"),
                        rs.getString("char_name") ?: "",
                        rs.getInt("online") != 0,
                        rs.getInt("pkkills"),
                        rs.getInt("karma")
                    )
                } else {
                    null
                }
            }
        }
    }

    private fun loadOwnedCharacterForPlayerReset(con: Connection, login: String, characterId: Int): PlayerResetCharacterRecord? {
        return con.prepareStatement(
            "SELECT obj_Id, char_name, COALESCE(online, 0) AS online FROM characters WHERE obj_Id=? AND account_name=? AND COALESCE(deletetime, 0)=0 LIMIT 1"
        ).use { ps ->
            ps.setInt(1, characterId)
            ps.setString(2, login)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                val dbOnline = rs.getInt("online") != 0
                val worldOnline = isPlayerOnlineInWorld(characterId)
                val online = dbOnline || worldOnline
                val state = if (online) onlinePlayerState(characterId) else OnlinePlayerState(inPeaceZone = true, inCombat = false)
                PlayerResetCharacterRecord(
                    rs.getInt("obj_Id"),
                    rs.getString("char_name") ?: "",
                    online,
                    state.inPeaceZone,
                    state.inCombat
                )
            }
        }
    }

    private data class ClanChatMemberRecord(val characterId: Int, val characterName: String, val clanId: Int, val clanName: String, val allyId: Int)

    private fun loadOwnedClanMemberForChat(con: Connection, login: String, characterId: Int): ClanChatMemberRecord? {
        return con.prepareStatement(
            """
            SELECT c.obj_Id, c.char_name, c.clanid, cd.clan_name, COALESCE(cd.ally_id, 0) AS ally_id
            FROM characters c
            JOIN clan_data cd ON cd.clan_id = c.clanid
            WHERE c.obj_Id=?
              AND c.account_name=?
              AND COALESCE(c.deletetime, 0)=0
              AND COALESCE(c.clanid, 0)>0
            LIMIT 1
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, characterId)
            ps.setString(2, login)
            ps.executeQuery().use { rs ->
                if (rs.next()) ClanChatMemberRecord(rs.getInt("obj_Id"), rs.getString("char_name") ?: "", rs.getInt("clanid"), rs.getString("clan_name") ?: "", rs.getInt("ally_id")) else null
            }
        }
    }

    private fun getAllianceClanIds(con: Connection, allyId: Int): List<Int> {
        if (allyId <= 0) return emptyList()
        return runCatching {
            con.prepareStatement("SELECT clan_id FROM clan_data WHERE ally_id = ?").use { ps ->
                ps.setInt(1, allyId)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<Int>()
                    while (rs.next()) list.add(rs.getInt(1))
                    list
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun refreshClanRuntime(clanId: Int) {
        runCatching {
            val clanTableClass = Class.forName("ext.mods.gameserver.data.sql.ClanTable")
            val table = clanTableClass.getMethod("getInstance").invoke(null)
            val clan = clanTableClass.getMethod("getClan", Int::class.javaPrimitiveType).invoke(table, clanId) ?: return@runCatching
            val clanClass = clan.javaClass
            runCatching {
                val field = clanClass.getDeclaredField("_subPledges")
                field.isAccessible = true
                val map = field.get(clan) as? MutableMap<*, *>
                @Suppress("UNCHECKED_CAST")
                val typed = map as? MutableMap<Int, Any>
                typed?.clear()
                val subPledgeClass = Class.forName("ext.mods.gameserver.model.pledge.SubPledge")
                ConnectionPool.getConnection().use { con ->
                    con.prepareStatement("SELECT sub_pledge_id, name, leader_id FROM clan_subpledges WHERE clan_id=?").use { ps ->
                        ps.setInt(1, clanId)
                        ps.executeQuery().use { rs ->
                            while (rs.next()) {
                                val id = rs.getInt("sub_pledge_id")
                                val sub = subPledgeClass.getConstructor(Int::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType)
                                    .newInstance(id, rs.getString("name") ?: "", rs.getInt("leader_id"))
                                typed?.put(id, sub)
                            }
                        }
                    }
                }
            }
            runCatching { clanClass.getMethod("broadcastClanStatus").invoke(clan) }
        }
    }

    private fun formatInGameDisplayName(characterName: String, role: String): String {
        return runCatching {
            val configClansClass = Class.forName("ext.mods.config.ConfigClans")
            if (role == "LEADER") {
                val enabled = configClansClass.getField("ENABLE_CLAN_LEADER_CHAT_TAG").getBoolean(null)
                val tag = configClansClass.getField("CLAN_LEADER_CHAT_TAG").get(null) as? String
                if (enabled && !tag.isNullOrEmpty()) "$tag $characterName" else characterName
            } else if (role == "CAPTAIN") {
                val enabled = configClansClass.getField("ENABLE_CLAN_ROYAL_GUARD_CHAT_TAG").getBoolean(null)
                val tag = configClansClass.getField("CLAN_ROYAL_GUARD_CHAT_TAG").get(null) as? String
                if (enabled && !tag.isNullOrEmpty()) "$tag $characterName" else characterName
            } else {
                characterName
            }
        }.getOrDefault(characterName)
    }

    private fun broadcastClanChat(clanId: Int, characterId: Int, characterName: String, text: String, role: String = "MEMBER") {
        runCatching {
            // Format display name for in-game CreatureSay with configured role tags from ConfigClans
            val displayName = formatInGameDisplayName(characterName, role)

            // Get clan instance
            val clanTableClass = Class.forName("ext.mods.gameserver.data.sql.ClanTable")
            val clanTable = clanTableClass.getMethod("getInstance").invoke(null)
            val clan = clanTableClass.getMethod("getClan", Int::class.javaPrimitiveType).invoke(clanTable, clanId)
                ?: return@runCatching // Clan not loaded (server not fully started?)

            // Build CreatureSay packet - same as ChatClan.java does
            val sayTypeClass = Class.forName("ext.mods.gameserver.enums.SayType")
            val clanSayType = sayTypeClass.getField("CLAN").get(null)  // Enum constant access via field
            val packetClass = Class.forName("ext.mods.gameserver.network.serverpackets.CreatureSay")

            // CreatureSay(int objectId, SayType type, String charName, String text)
            val packet = packetClass.getConstructor(
                Int::class.javaPrimitiveType, sayTypeClass, String::class.java, String::class.java
            ).newInstance(characterId, clanSayType, displayName, text)

            // clan.broadcastToMembers(L2GameServerPacket...) — varargs = array in reflection
            val l2PacketClass = Class.forName("ext.mods.gameserver.network.serverpackets.L2GameServerPacket")
            val arrayType = java.lang.reflect.Array.newInstance(l2PacketClass, 0).javaClass
            val broadcastMethod = clan.javaClass.getMethod("broadcastToMembers", arrayType)
            val packetArray = java.lang.reflect.Array.newInstance(l2PacketClass, 1)
            java.lang.reflect.Array.set(packetArray, 0, packet)
            broadcastMethod.invoke(clan, packetArray)
        }.onFailure { e ->
            // Log the actual error so we can debug site->game delivery issues
            System.err.println("[SiteApiRepository] broadcastClanChat FAILED for clan=$clanId: ${e.javaClass.name}: ${e.message}")
            e.cause?.let { System.err.println("  caused by: ${it.javaClass.name}: ${it.message}") }
        }
    }

    private fun broadcastAllianceChat(allyId: Int, characterId: Int, characterName: String, text: String, role: String = "MEMBER") {
        if (allyId <= 0) return
        runCatching {
            val displayName = formatInGameDisplayName(characterName, role)

            val clanTableClass = Class.forName("ext.mods.gameserver.data.sql.ClanTable")
            val clanTable = clanTableClass.getMethod("getInstance").invoke(null)
            val clans = clanTableClass.getMethod("getClans").invoke(clanTable) as? Collection<*> ?: return@runCatching

            val sayTypeClass = Class.forName("ext.mods.gameserver.enums.SayType")
            val allySayType = sayTypeClass.getField("ALLIANCE").get(null)
            val packetClass = Class.forName("ext.mods.gameserver.network.serverpackets.CreatureSay")

            val packet = packetClass.getConstructor(
                Int::class.javaPrimitiveType, sayTypeClass, String::class.java, String::class.java
            ).newInstance(characterId, allySayType, displayName, text)

            val l2PacketClass = Class.forName("ext.mods.gameserver.network.serverpackets.L2GameServerPacket")
            val arrayType = java.lang.reflect.Array.newInstance(l2PacketClass, 0).javaClass
            val packetArray = java.lang.reflect.Array.newInstance(l2PacketClass, 1)
            java.lang.reflect.Array.set(packetArray, 0, packet)

            for (c in clans) {
                if (c == null) continue
                val cAllyId = runCatching { c.javaClass.getMethod("getAllyId").invoke(c) as? Int ?: 0 }.getOrDefault(0)
                if (cAllyId == allyId) {
                    runCatching {
                        val broadcastMethod = c.javaClass.getMethod("broadcastToMembers", arrayType)
                        broadcastMethod.invoke(c, packetArray)
                    }
                }
            }
        }.onFailure { e ->
            System.err.println("[SiteApiRepository] broadcastAllianceChat FAILED for allyId=$allyId: ${e.javaClass.name}: ${e.message}")
        }
    }

    private fun isPlayerOnlineInWorld(characterId: Int): Boolean = runCatching {
        val worldClass = Class.forName("ext.mods.gameserver.model.World")
        val world = worldClass.getMethod("getInstance").invoke(null)
        worldClass.getMethod("getPlayer", Int::class.javaPrimitiveType).invoke(world, characterId) != null
    }.getOrDefault(false)

    private fun onlinePlayerState(characterId: Int): OnlinePlayerState = runCatching {
        val worldClass = Class.forName("ext.mods.gameserver.model.World")
        val world = worldClass.getMethod("getInstance").invoke(null)
        val player = worldClass.getMethod("getPlayer", Int::class.javaPrimitiveType).invoke(world, characterId)
            ?: return@runCatching OnlinePlayerState(inPeaceZone = false, inCombat = true)
        val zoneIdClass = Class.forName("ext.mods.gameserver.enums.ZoneId")
        val peaceZone = zoneIdClass.getField("PEACE").get(null)
        val inPeaceZone = player.javaClass.getMethod("isInsideZone", zoneIdClass).invoke(player, peaceZone) as? Boolean ?: false
        val inCombat = player.javaClass.getMethod("isInCombat").invoke(player) as? Boolean ?: false
        OnlinePlayerState(inPeaceZone, inCombat)
    }.getOrDefault(OnlinePlayerState(inPeaceZone = false, inCombat = true))

    private fun waitUntilOffline(con: Connection, login: String, characterId: Int) {
        repeat(10) {
            Thread.sleep(250L)
            val current = loadOwnedCharacterForPlayerReset(con, login, characterId)
            if (current == null || !current.online) return
        }
    }

    private fun kickOnlinePlayer(characterId: Int): Boolean = runCatching {
        val worldClass = Class.forName("ext.mods.gameserver.model.World")
        val world = worldClass.getMethod("getInstance").invoke(null)
        val player = worldClass.getMethod("getPlayer", Int::class.javaPrimitiveType).invoke(world, characterId) ?: return@runCatching false
        player.javaClass.getMethod("logout", Boolean::class.javaPrimitiveType).invoke(player, true)
        true
    }.getOrDefault(false)

    private fun clearInstanceMemo(con: Connection, characterId: Int, instanceId: Int) {
        val memoKeys = listOf("instanceId", "instance_id", "instance", "lastInstanceId", "mapInstanceId")
        con.prepareStatement("DELETE FROM character_memo WHERE charId=? AND var IN (${memoKeys.joinToString(",") { "?" }})").use { ps ->
            ps.setInt(1, characterId)
            memoKeys.forEachIndexed { index, key -> ps.setString(index + 2, key) }
            ps.executeUpdate()
        }
        if (instanceId != 0) {
            con.prepareStatement(DatabaseDialect.upsert("character_memo", "charId, var, val", "?, 'instanceId', ?", "charId, var", "val")).use { ps ->
                ps.setInt(1, characterId)
                ps.setString(2, instanceId.toString())
                ps.executeUpdate()
            }
        }
    }

    private fun loadOwnedClanLeaderForRename(con: Connection, login: String, characterId: Int): ClanLeaderRecord? {
        return con.prepareStatement(
            """
            SELECT c.obj_Id,
                   c.char_name,
                   COALESCE(c.online, 0) AS online,
                   cd.clan_id,
                   cd.clan_name,
                   COALESCE(cd.dissolving_expiry_time, 0) AS dissolving_expiry_time
            FROM characters c
            JOIN clan_data cd ON cd.clan_id = c.clanid
            WHERE c.obj_Id = ?
              AND c.account_name = ?
              AND COALESCE(c.deletetime, 0) = 0
              AND cd.leader_id = c.obj_Id
            LIMIT 1
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, characterId)
            ps.setString(2, login)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                val dbOnline = rs.getInt("online") != 0
                val worldOnline = isPlayerOnlineInWorld(characterId)
                ClanLeaderRecord(
                    characterId = rs.getInt("obj_Id"),
                    characterName = rs.getString("char_name") ?: "",
                    online = dbOnline || worldOnline,
                    clanId = rs.getInt("clan_id"),
                    clanName = rs.getString("clan_name") ?: "",
                    dissolving = rs.getLong("dissolving_expiry_time") > 0L
                )
            }
        }
    }

    private fun loadOwnedClanMemberForWar(con: Connection, login: String, characterId: Int): ClanWarMemberRecord? {
        return con.prepareStatement(
            """
            SELECT c.obj_Id,
                   c.char_name,
                   COALESCE(c.online, 0) AS online,
                   cd.clan_id,
                   cd.clan_name,
                   COALESCE(cd.clan_level, 0) AS clan_level,
                   COALESCE(cd.ally_id, 0) AS ally_id,
                   (cd.leader_id = c.obj_Id) AS is_leader,
                   COALESCE(cd.dissolving_expiry_time, 0) AS dissolving_expiry_time
            FROM characters c
            JOIN clan_data cd ON cd.clan_id = c.clanid
            WHERE c.obj_Id = ?
              AND c.account_name = ?
              AND COALESCE(c.deletetime, 0) = 0
            LIMIT 1
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, characterId)
            ps.setString(2, login)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                val dbOnline = rs.getInt("online") != 0
                val worldOnline = isPlayerOnlineInWorld(characterId)
                ClanWarMemberRecord(
                    characterId = rs.getInt("obj_Id"),
                    characterName = rs.getString("char_name") ?: "",
                    online = dbOnline || worldOnline,
                    clanId = rs.getInt("clan_id"),
                    clanName = rs.getString("clan_name") ?: "",
                    clanLevel = rs.getInt("clan_level"),
                    allyId = rs.getInt("ally_id"),
                    isLeader = rs.getBoolean("is_leader"),
                    dissolving = rs.getLong("dissolving_expiry_time") > 0L
                )
            }
        }
    }

    private fun clanNameExists(con: Connection, name: String, ignoreClanId: Int): Boolean {
        return con.prepareStatement(
            "SELECT 1 FROM clan_data WHERE LOWER(clan_name)=LOWER(?) AND clan_id<>? LIMIT 1"
        ).use { ps ->
            ps.setString(1, name)
            ps.setInt(2, ignoreClanId)
            ps.executeQuery().use { it.next() }
        }
    }

    private fun persistedClanName(con: Connection, clanId: Int): String? {
        return con.prepareStatement("SELECT clan_name FROM clan_data WHERE clan_id=? LIMIT 1").use { ps ->
            ps.setInt(1, clanId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString("clan_name") else null }
        }
    }

    private fun persistedClanAllyId(con: Connection, clanId: Int): Int? {
        return con.prepareStatement("SELECT ally_id FROM clan_data WHERE clan_id=? LIMIT 1").use { ps ->
            ps.setInt(1, clanId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("ally_id") else null }
        }
    }

    private fun persistedAllyName(con: Connection, allyId: Int): String? {
        return con.prepareStatement("SELECT ally_name FROM clan_data WHERE ally_id=? LIMIT 1").use { ps ->
            ps.setInt(1, allyId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString("ally_name") else null }
        }
    }

    private fun allyNameExists(con: Connection, name: String, ignoreAllyId: Int): Boolean {
        return con.prepareStatement(
            "SELECT 1 FROM clan_data WHERE LOWER(COALESCE(ally_name, ''))=LOWER(?) AND ally_id<>? AND ally_id>0 LIMIT 1"
        ).use { ps ->
            ps.setString(1, name)
            ps.setInt(2, ignoreAllyId)
            ps.executeQuery().use { it.next() }
        }
    }

    private fun persistedClanLevel(con: Connection, clanId: Int): Int? {
        return con.prepareStatement("SELECT COALESCE(clan_level, 0) AS clan_level FROM clan_data WHERE clan_id=? LIMIT 1").use { ps ->
            ps.setInt(1, clanId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("clan_level") else null }
        }
    }

    private fun persistedClanLeaderId(con: Connection, clanId: Int): Int? {
        return con.prepareStatement("SELECT leader_id FROM clan_data WHERE clan_id=? LIMIT 1").use { ps ->
            ps.setInt(1, clanId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("leader_id") else null }
        }
    }

    private fun persistedCharacterClanId(con: Connection, characterId: Int): Int? {
        return con.prepareStatement("SELECT COALESCE(clanid, 0) AS clanid FROM characters WHERE obj_Id=? LIMIT 1").use { ps ->
            ps.setInt(1, characterId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("clanid") else null }
        }
    }

    private fun loadOwnedClanMemberForTransfer(con: Connection, targetCharacterId: Int, clanId: Int): RenameCharacterRecord? {
        return con.prepareStatement(
            "SELECT obj_Id, char_name, COALESCE(online, 0) AS online FROM characters WHERE obj_Id=? AND clanid=? AND COALESCE(deletetime, 0)=0 LIMIT 1"
        ).use { ps ->
            ps.setInt(1, targetCharacterId)
            ps.setInt(2, clanId)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    val dbOnline = rs.getInt("online") != 0
                    val worldOnline = isPlayerOnlineInWorld(targetCharacterId)
                    RenameCharacterRecord(rs.getInt("obj_Id"), rs.getString("char_name") ?: "", dbOnline || worldOnline)
                } else {
                    null
                }
            }
        }
    }

    private fun characterNameExists(con: Connection, name: String, ignoreCharacterId: Int): Boolean {
        // Case-insensitive uniqueness check. Using LOWER to stay portable across SQLite/MariaDB.
        return con.prepareStatement(
            "SELECT 1 FROM characters WHERE LOWER(char_name)=LOWER(?) AND obj_Id<>? AND COALESCE(deletetime, 0)=0 LIMIT 1"
        ).use { ps ->
            ps.setString(1, name)
            ps.setInt(2, ignoreCharacterId)
            ps.executeQuery().use { it.next() }
        }
    }

    private fun loadConsumableAccountItem(con: Connection, login: String, itemId: Int): ConsumableItemRecord? {
        // Prefer a non-equipment slot to avoid removing equipped gear. Pick the row with the smallest
        // object_id that still has count>=1, so behavior is stable across reloads.
        return con.prepareStatement(
            """
            SELECT i.object_id, i.count
            FROM items i
            JOIN characters c ON c.obj_Id = i.owner_id
            WHERE c.account_name = ?
              AND i.item_id = ?
              AND COALESCE(i.count, 0) >= 1
              AND UPPER(COALESCE(i.loc, '')) = 'INVENTORY'
            ORDER BY i.count ASC, i.object_id ASC
            LIMIT 1
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, login)
            ps.setInt(2, itemId)
            ps.executeQuery().use { rs ->
                if (rs.next()) ConsumableItemRecord(rs.getInt(1), rs.getLong(2)) else null
            }
        }
    }

    private fun loadConsumableAccountItems(con: Connection, login: String, itemId: Int, amount: Long): List<ConsumableItemRecord> {
        return con.prepareStatement(
            """
            SELECT i.object_id, i.count
            FROM items i
            JOIN characters c ON c.obj_Id = i.owner_id
            WHERE c.account_name = ?
              AND i.item_id = ?
              AND COALESCE(i.count, 0) >= 1
              AND UPPER(COALESCE(i.loc, '')) = 'INVENTORY'
            ORDER BY i.count ASC, i.object_id ASC
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, login)
            ps.setInt(2, itemId)
            ps.executeQuery().use { rs ->
                val items = mutableListOf<ConsumableItemRecord>()
                var remaining = amount
                while (rs.next() && remaining > 0L) {
                    val count = rs.getLong(2)
                    items.add(ConsumableItemRecord(rs.getInt(1), count))
                    remaining -= count
                }
                items
            }
        }
    }

    private fun consumeItems(con: Connection, items: List<ConsumableItemRecord>, amount: Long) {
        var remaining = amount
        for (item in items) {
            if (remaining <= 0L) break
            val take = minOf(item.count, remaining)
            val changed = if (take < item.count) {
                con.prepareStatement("UPDATE items SET count = count - ? WHERE object_id = ? AND count >= ?").use { ps ->
                    ps.setLong(1, take)
                    ps.setInt(2, item.objectId)
                    ps.setLong(3, take)
                    ps.executeUpdate()
                }
            } else {
                con.prepareStatement("DELETE FROM items WHERE object_id = ? AND count <= ?").use { ps ->
                    ps.setInt(1, item.objectId)
                    ps.setLong(2, take)
                    ps.executeUpdate()
                }
            }
            if (changed != 1) error("clan rename fee item was not consumed")
            remaining -= take
        }
        if (remaining > 0L) error("not enough clan rename fee items")
    }

    private fun consumeOneItem(con: Connection, item: ConsumableItemRecord) {
        val changed = if (item.count > 1L) {
            con.prepareStatement("UPDATE items SET count = count - 1 WHERE object_id = ? AND count >= 1").use { ps ->
                ps.setInt(1, item.objectId)
                ps.executeUpdate()
            }
        } else {
            con.prepareStatement("DELETE FROM items WHERE object_id = ? AND count <= 1").use { ps ->
                ps.setInt(1, item.objectId)
                ps.executeUpdate()
            }
        }
        if (changed != 1) error("rename fee item was not consumed")
    }

    private fun recordClanAudit(con: Connection, login: String, characterId: Int, clanId: Int, action: String, oldValue: String, newValue: String) {
        runCatching {
            con.prepareStatement(
                """
                INSERT INTO site_clan_audit_log (created_at, login, character_id, clan_id, action, old_value, new_value)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setLong(1, System.currentTimeMillis())
                ps.setString(2, login)
                ps.setInt(3, characterId)
                ps.setInt(4, clanId)
                ps.setString(5, action)
                ps.setString(6, oldValue)
                ps.setString(7, newValue)
                ps.executeUpdate()
            }
        }
    }

    private fun countAccountItem(con: Connection, login: String, itemId: Int): Long {
        // Total the account owns of this item across every character (inventory + paperdoll).
        return con.prepareStatement(
            """
            SELECT COALESCE(SUM(COALESCE(i.count, 0)), 0)
            FROM items i
            JOIN characters c ON c.obj_Id = i.owner_id
            WHERE c.account_name = ?
              AND i.item_id = ?
              AND UPPER(COALESCE(i.loc, '')) IN ('INVENTORY', 'PAPERDOLL')
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, login)
            ps.setInt(2, itemId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }
    }

    private fun accountExists(con: Connection, login: String): Boolean =
        con.prepareStatement("SELECT 1 FROM accounts WHERE login=? LIMIT 1").use { ps ->
            ps.setString(1, login)
            ps.executeQuery().use { it.next() }
        }

    private fun loadAccountCharacters(con: Connection, login: String): List<AccountCharacter> {
        val sql = """
            SELECT c.obj_Id,
                   c.char_name,
                   c.title,
                   COALESCE(c.level, 0) AS level,
                   COALESCE(c.race, 0) AS race,
                   COALESCE(c.classid, 0) AS classid,
                   COALESCE(c.base_class, 0) AS base_class,
                   COALESCE(c.sex, 0) AS sex,
                   COALESCE(c.online, 0) AS online,
                   COALESCE(c.pvpkills, 0) AS pvpkills,
                   COALESCE(c.pkkills, 0) AS pkkills,
                   COALESCE(c.karma, 0) AS karma,
                   cd.clan_name
            FROM characters c
            LEFT JOIN clan_data cd ON cd.clan_id = c.clanid
            WHERE c.account_name = ? AND COALESCE(c.deletetime, 0) = 0
            ORDER BY c.level DESC, c.char_name ASC
        """.trimIndent()
        return con.prepareStatement(sql).use { ps ->
            ps.setString(1, login)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val charId = rs.getInt("obj_Id")
                        val items = loadCharacterItems(con, charId)
                        val dbOnline = rs.getInt("online") > 0
                        val worldOnline = isPlayerOnlineInWorld(charId)
                        val online = dbOnline || worldOnline
                        val state = if (online) onlinePlayerState(charId) else OnlinePlayerState(inPeaceZone = true, inCombat = false)
                        add(AccountCharacter(
                            id = charId,
                            name = rs.getString("char_name"),
                            title = rs.getString("title"),
                            level = rs.getInt("level"),
                            race = rs.getInt("race"),
                            classId = rs.getInt("classid"),
                            baseClass = rs.getInt("base_class"),
                            sex = rs.getInt("sex"),
                            online = online,
                            pvpKills = rs.getInt("pvpkills"),
                            pkKills = rs.getInt("pkkills"),
                            karma = rs.getInt("karma"),
                            clan = rs.getString("clan_name"),
                            inPeaceZone = state.inPeaceZone,
                            inCombat = state.inCombat,
                            equipped = items.filter { it.location == "PAPERDOLL" }.sortedBy { it.slot },
                            inventory = items.filter { it.location == "INVENTORY" }.sortedWith(
                                compareByDescending<AccountItem> { it.enchant }.thenBy { it.itemId }
                            )
                        ))
                    }
                }
            }
        }
    }

    private fun loadCharacterItems(con: Connection, charId: Int): List<AccountItem> {
        val sql = """
            SELECT object_id, item_id, count, enchant_level, loc, COALESCE(loc_data, 0) AS loc_data
            FROM items
            WHERE owner_id = ? AND loc IN ('PAPERDOLL', 'INVENTORY')
            ORDER BY CASE WHEN loc = 'PAPERDOLL' THEN 0 ELSE 1 END, loc_data ASC, item_id ASC
            LIMIT 260
        """.trimIndent()
        return con.prepareStatement(sql).use { ps ->
            ps.setInt(1, charId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(AccountItem(
                            objectId = rs.getInt("object_id"),
                            itemId = rs.getInt("item_id"),
                            count = rs.getLong("count"),
                            enchant = rs.getInt("enchant_level"),
                            location = rs.getString("loc") ?: "",
                            slot = rs.getInt("loc_data")
                        ))
                    }
                }
            }
        }
    }

    private fun playerRanking(column: String, limit: Int): List<PlayerRankEntry> {
        val safeColumn = if (column == "pvpkills") "pvpkills" else "pkkills"
        val sql = """
            SELECT c.char_name, COALESCE(c.$safeColumn, 0) AS score, COALESCE(c.level, 0) AS level, cd.clan_name
            FROM characters c
            LEFT JOIN clan_data cd ON cd.clan_id = c.clanid
            WHERE COALESCE(c.accesslevel, 0) >= 0 AND COALESCE(c.deletetime, 0) = 0
            ORDER BY score DESC, c.level DESC, c.char_name ASC
            LIMIT ?
        """.trimIndent()
        return ConnectionPool.getConnection().use { con ->
            con.prepareStatement(sql).use { ps ->
                ps.setInt(1, limit.coerceIn(1, 100))
                ps.executeQuery().use { rs ->
                    buildList {
                        var position = 1
                        while (rs.next()) {
                            add(PlayerRankEntry(
                                position = position++,
                                name = rs.getString("char_name"),
                                value = rs.getInt("score"),
                                level = rs.getInt("level"),
                                clan = rs.getString("clan_name")
                            ))
                        }
                    }
                }
            }
        }
    }

    private fun clanRanking(limit: Int): List<ClanRankEntry> {
        val sql = """
            SELECT cd.clan_name,
                   COALESCE(cd.reputation_score, 0) AS reputation,
                   COALESCE(cd.clan_level, 0) AS level,
                   COUNT(m.obj_Id) AS members,
                   leader.char_name AS leader
            FROM clan_data cd
            LEFT JOIN characters leader ON leader.obj_Id = cd.leader_id
            LEFT JOIN characters m ON m.clanid = cd.clan_id AND COALESCE(m.deletetime, 0) = 0
            WHERE cd.clan_name IS NOT NULL AND cd.clan_name <> ''
            GROUP BY cd.clan_id, cd.clan_name, cd.reputation_score, cd.clan_level, leader.char_name
            ORDER BY reputation DESC, level DESC, cd.clan_name ASC
            LIMIT ?
        """.trimIndent()
        return ConnectionPool.getConnection().use { con ->
            con.prepareStatement(sql).use { ps ->
                ps.setInt(1, limit.coerceIn(1, 100))
                ps.executeQuery().use { rs ->
                    buildList {
                        var position = 1
                        while (rs.next()) {
                            add(ClanRankEntry(
                                position = position++,
                                name = rs.getString("clan_name"),
                                level = rs.getInt("level"),
                                reputation = rs.getInt("reputation"),
                                members = rs.getInt("members"),
                                leader = rs.getString("leader")
                            ))
                        }
                    }
                }
            }
        }
    }

    private data class ClanSkillsMemberRecord(
        val characterId: Int,
        val characterName: String,
        val clanId: Int,
        val clanName: String,
        val isLeader: Boolean
    )

    private fun loadOwnedClanMemberForSkills(con: Connection, login: String, characterId: Int): ClanSkillsMemberRecord? {
        return con.prepareStatement(
            """
            SELECT c.obj_Id,
                   c.char_name,
                   cd.clan_id,
                   cd.clan_name,
                   (cd.leader_id = c.obj_Id) AS is_leader
            FROM characters c
            JOIN clan_data cd ON cd.clan_id = c.clanid
            WHERE c.obj_Id = ?
              AND c.account_name = ?
              AND COALESCE(c.deletetime, 0) = 0
              AND COALESCE(c.clanid, 0) > 0
            LIMIT 1
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, characterId)
            ps.setString(2, login)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    ClanSkillsMemberRecord(
                        characterId = rs.getInt("obj_Id"),
                        characterName = rs.getString("char_name") ?: "",
                        clanId = rs.getInt("clan_id"),
                        clanName = rs.getString("clan_name") ?: "",
                        isLeader = rs.getBoolean("is_leader")
                    )
                } else null
            }
        }
    }

    private fun countAccountInventoryItem(con: Connection, login: String, itemId: Int): Long {
        return con.prepareStatement(
            """
            SELECT COALESCE(SUM(i.count), 0)
            FROM items i
            JOIN characters c ON c.obj_Id = i.owner_id
            WHERE c.account_name = ?
              AND i.item_id = ?
              AND UPPER(COALESCE(i.loc, '')) = 'INVENTORY'
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, login)
            ps.setInt(2, itemId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }
    }

    private data class ClanSkillDef(
        val id: Int,
        val name: String,
        val description: String,
        val effect: String,
        val maxLevel: Int,
        val minClanLevel: Int,
        val baseCost: Int
    )

    private val CLAN_SKILLS_CATALOG = listOf(
        ClanSkillDef(370, "Clan Vitality", "Aumenta a vida máxima (Max HP) dos membros do clan.", "+3% / +5% / +6% Max HP", 3, 5, 500),
        ClanSkillDef(371, "Clan Spirituality", "Aumenta o Combat Points máximo (Max CP) dos membros do clan.", "+6% / +10% / +12% Max CP", 3, 6, 800),
        ClanSkillDef(372, "Clan Essence", "Aumenta a mana máxima (Max MP) dos membros do clan.", "+3% / +5% / +6% Max MP", 3, 8, 3900),
        ClanSkillDef(373, "Clan Lifeblood", "Aumenta a taxa de regeneração de vida (HP Regen) dos membros do clan.", "+3% / +5% / +6% HP Regen", 3, 5, 500),
        ClanSkillDef(374, "Clan Morale", "Aumenta a taxa de regeneração de CP (CP Regen) dos membros do clan.", "+3% / +5% / +6% CP Regen", 3, 6, 900),
        ClanSkillDef(375, "Clan Clarity", "Aumenta a taxa de regeneração de mana (MP Regen) dos membros do clan.", "+3% / +5% / +6% MP Regen", 3, 8, 3900),
        ClanSkillDef(376, "Clan Might", "Aumenta o poder de ataque físico (P. Atk) dos membros do clan.", "+3% / +5% / +6% P. Atk", 3, 6, 1000),
        ClanSkillDef(377, "Clan Aegis", "Aumenta a defesa física (P. Def) dos membros do clan.", "+3% / +5% / +6% P. Def", 3, 6, 1000),
        ClanSkillDef(378, "Clan Empowerment", "Aumenta o poder de ataque mágico (M. Atk) dos membros do clan.", "+3% / +5% / +6% M. Atk", 3, 8, 3900),
        ClanSkillDef(379, "Clan Magic Protection", "Aumenta a defesa mágica (M. Def) dos membros do clan.", "+6% / +10% / +12% M. Def", 3, 5, 500),
        ClanSkillDef(380, "Clan Guidance", "Aumenta a precisão dos ataques (Accuracy) dos membros do clan.", "+1 / +2 / +3 Accuracy", 3, 7, 1900),
        ClanSkillDef(381, "Clan Agility", "Aumenta a esquiva contra ataques físicos (Evasion) dos membros do clan.", "+1 / +2 / +3 Evasion", 3, 8, 4000),
        ClanSkillDef(382, "Clan Withstand-Attack", "Aumenta a taxa de bloqueio com escudo (Shield Defense Rate).", "+3% / +5% / +6% Shield Block", 3, 7, 800),
        ClanSkillDef(383, "Clan Shield Boost", "Aumenta a defesa de escudo (Shield P. Def) dos membros do clan.", "+3% / +5% / +6% Shield P. Def", 3, 6, 800),
        ClanSkillDef(384, "Clan Cyclonic Resistance", "Aumenta a resistência contra ataques de vento e água.", "+3% / +5% / +6% Resist Wind/Water", 3, 7, 1800),
        ClanSkillDef(385, "Clan Magmatic Resistance", "Aumenta a resistência contra ataques de fogo e terra.", "+3% / +5% / +6% Resist Fire/Earth", 3, 7, 1800),
        ClanSkillDef(386, "Clan Fortitude", "Aumenta a resistência contra efeitos de atordoamento (Stun).", "+3% / +5% / +6% Resist Stun", 3, 7, 1000),
        ClanSkillDef(387, "Clan Freedom", "Aumenta a resistência contra efeitos de imobilização (Root).", "+3% / +5% / +6% Resist Root", 3, 7, 1800),
        ClanSkillDef(388, "Clan Vigilance", "Aumenta a resistência contra efeitos de sono (Sleep).", "+3% / +5% / +6% Resist Sleep", 3, 7, 1800),
        ClanSkillDef(389, "Clan March", "Aumenta a velocidade de movimento (Speed) dos membros do clan.", "+3 / +5 / +6 Speed", 3, 8, 3800),
        ClanSkillDef(390, "Clan Luck", "Reduz a perda de experiência e penalidade ao morrer.", "Redução de perda de EXP por morte", 3, 7, 2200),
        ClanSkillDef(391, "Clan Imperium", "Concede autoridade ao líder para criação de Command Channel.", "Permissão de Command Channel", 1, 5, 500)
    )

    fun getClanSkillsServiceData(login: String, characterId: Int): Map<String, Any?> {
        return try {
            ConnectionPool.getConnection().use { con ->
                val current = loadOwnedClanMemberForSkills(con, login, characterId)
                    ?: return mapOf("ok" to false, "message" to "Personagem não encontrado ou não pertence a um clan.")

                var clanLevel = 1
                var reputationScore = 0

                con.prepareStatement("SELECT clan_level, reputation_score FROM clan_data WHERE clan_id=? LIMIT 1").use { ps ->
                    ps.setInt(1, current.clanId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            clanLevel = rs.getInt("clan_level")
                            reputationScore = rs.getInt("reputation_score")
                        }
                    }
                }

                val currentSkillLevels = mutableMapOf<Int, Int>()
                con.prepareStatement("SELECT skill_id, skill_level FROM clan_skills WHERE clan_id=?").use { ps ->
                    ps.setInt(1, current.clanId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            currentSkillLevels[rs.getInt("skill_id")] = rs.getInt("skill_level")
                        }
                    }
                }

                val multiplier = runCatching {
                    val configClansClass = Class.forName("ext.mods.config.ConfigClans")
                    configClansClass.getField("CLAN_SKILLS_PURCHASE_COST_MULTIPLIER").getDouble(null)
                }.getOrDefault(1.0)

                val donateItemId = runCatching {
                    val configClansClass = Class.forName("ext.mods.config.ConfigClans")
                    configClansClass.getField("SITE_CLAN_REPUTATION_DONATE_ITEM_ID").getInt(null)
                }.getOrDefault(4037)

                val donateAmountPerUse = runCatching {
                    val configClansClass = Class.forName("ext.mods.config.ConfigClans")
                    configClansClass.getField("SITE_CLAN_REPUTATION_DONATE_AMOUNT_PER_USE").getInt(null)
                }.getOrDefault(1000)

                val donateItemCost = runCatching {
                    val configClansClass = Class.forName("ext.mods.config.ConfigClans")
                    configClansClass.getField("SITE_CLAN_REPUTATION_DONATE_ITEM_COST").getInt(null)
                }.getOrDefault(1)

                val donateItemBalance = countAccountInventoryItem(con, login, donateItemId)

                val currentSkillsList = mutableListOf<Map<String, Any?>>()
                val availableSkillsList = mutableListOf<Map<String, Any?>>()

                CLAN_SKILLS_CATALOG.forEach { def ->
                    val curLvl = currentSkillLevels[def.id] ?: 0
                    val iconName = "skill0${def.id}.png"
                    val resolvedName = runCatching {
                        val skillTableClass = Class.forName("ext.mods.gameserver.data.SkillTable")
                        val table = skillTableClass.getMethod("getInstance").invoke(null)
                        val skillObj = skillTableClass.getMethod("getInfo", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(table, def.id, 1)
                        skillObj?.javaClass?.getMethod("getName")?.invoke(skillObj) as? String
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: def.name

                    if (curLvl > 0) {
                        currentSkillsList.add(mapOf(
                            "id" to def.id,
                            "name" to resolvedName,
                            "description" to def.description,
                            "effect" to def.effect,
                            "level" to curLvl,
                            "maxLevel" to def.maxLevel,
                            "icon" to iconName
                        ))
                    }

                    if (curLvl < def.maxLevel) {
                        val nextLevel = curLvl + 1
                        val cost = (def.baseCost * nextLevel * multiplier).toInt().coerceAtLeast(100)
                        val meetsLvl = clanLevel >= def.minClanLevel
                        val canAfford = reputationScore >= cost

                        availableSkillsList.add(mapOf(
                            "id" to def.id,
                            "name" to resolvedName,
                            "description" to def.description,
                            "effect" to def.effect,
                            "currentLevel" to curLvl,
                            "nextLevel" to nextLevel,
                            "maxLevel" to def.maxLevel,
                            "reputationCost" to cost,
                            "minClanLevel" to def.minClanLevel,
                            "icon" to iconName,
                            "canAfford" to canAfford,
                            "meetsClanLevel" to meetsLvl
                        ))
                    }
                }

                mapOf(
                    "ok" to true,
                    "isLeader" to current.isLeader,
                    "clanId" to current.clanId,
                    "clanName" to current.clanName,
                    "clanLevel" to clanLevel,
                    "reputationScore" to reputationScore,
                    "donateItemId" to donateItemId,
                    "donateAmountPerUse" to donateAmountPerUse,
                    "donateItemCost" to donateItemCost,
                    "donateItemBalance" to donateItemBalance,
                    "currentSkills" to currentSkillsList,
                    "availableSkills" to availableSkillsList
                )
            }
        } catch (e: Exception) {
            mapOf("ok" to false, "message" to "Erro ao carregar habilidades do clan: ${e.message}")
        }
    }

    fun buyClanSkillService(login: String, characterId: Int, skillId: Int): Map<String, Any?> {
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedClanMemberForSkills(con, login, characterId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem não encontrado ou não pertence a um clan."))

                    if (!current.isLeader) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Apenas o líder principal do clan pode adquirir ou evoluir habilidades."))
                    }

                    val enabled = runCatching {
                        val configClansClass = Class.forName("ext.mods.config.ConfigClans")
                        configClansClass.getField("ENABLE_CLAN_SKILLS_PURCHASE_SITE").getBoolean(null)
                    }.getOrDefault(true)

                    if (!enabled) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "A compra de habilidades de clan via site está desativada no servidor."))
                    }

                    val def = CLAN_SKILLS_CATALOG.find { it.id == skillId }
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Habilidade de clan não encontrada no catálogo."))

                    var clanLevel = 1
                    var reputationScore = 0

                    con.prepareStatement("SELECT clan_level, reputation_score FROM clan_data WHERE clan_id=? LIMIT 1").use { ps ->
                        ps.setInt(1, current.clanId)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                clanLevel = rs.getInt("clan_level")
                                reputationScore = rs.getInt("reputation_score")
                            }
                        }
                    }

                    if (clanLevel < def.minClanLevel) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Seu clan necessita do Nível ${def.minClanLevel} para adquirir ${def.name}."))
                    }

                    val curLvl = con.prepareStatement("SELECT skill_level FROM clan_skills WHERE clan_id=? AND skill_id=? LIMIT 1").use { ps ->
                        ps.setInt(1, current.clanId); ps.setInt(2, skillId)
                        ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                    }

                    if (curLvl >= def.maxLevel) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "A habilidade ${def.name} já atingiu o nível máximo (${def.maxLevel})."))
                    }

                    val nextLevel = curLvl + 1
                    val multiplier = runCatching {
                        val configClansClass = Class.forName("ext.mods.config.ConfigClans")
                        configClansClass.getField("CLAN_SKILLS_PURCHASE_COST_MULTIPLIER").getDouble(null)
                    }.getOrDefault(1.0)
                    val cost = (def.baseCost * nextLevel * multiplier).toInt().coerceAtLeast(100)

                    if (reputationScore < cost) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Pontos de reputação insuficientes. Requer $cost pts (atual: $reputationScore pts)."))
                    }

                    // Deduct reputation
                    con.prepareStatement("UPDATE clan_data SET reputation_score = reputation_score - ? WHERE clan_id=?").use { ps ->
                        ps.setInt(1, cost); ps.setInt(2, current.clanId)
                        ps.executeUpdate()
                    }

                    // Insert or update skill (ANSI SQL compatible across MariaDB and SQLite)
                    con.prepareStatement("DELETE FROM clan_skills WHERE clan_id=? AND skill_id=?").use { ps ->
                        ps.setInt(1, current.clanId); ps.setInt(2, skillId)
                        ps.executeUpdate()
                    }
                    con.prepareStatement("INSERT INTO clan_skills (clan_id, skill_id, skill_level) VALUES (?, ?, ?)").use { ps ->
                        ps.setInt(1, current.clanId); ps.setInt(2, skillId); ps.setInt(3, nextLevel)
                        ps.executeUpdate()
                    }

                    recordClanAudit(con, login, characterId, current.clanId, "buy-clan-skill", "$skillId:lvl=$nextLevel", "cost=$cost")
                    con.commit()

                    // Sync runtime in GameServer
                    runCatching {
                        val clanTableClass = Class.forName("ext.mods.gameserver.data.sql.ClanTable")
                        val table = clanTableClass.getMethod("getInstance").invoke(null)
                        val clan = clanTableClass.getMethod("getClan", Int::class.javaPrimitiveType).invoke(table, current.clanId)
                        if (clan != null) {
                            val skillTableClass = Class.forName("ext.mods.gameserver.data.SkillTable")
                            val skillTable = skillTableClass.getMethod("getInstance").invoke(null)
                            val skillObj = skillTableClass.getMethod("getInfo", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(skillTable, skillId, nextLevel)
                            if (skillObj != null) {
                                val l2SkillClass = Class.forName("ext.mods.gameserver.model.L2Skill")
                                clan.javaClass.getMethod("addClanSkill", l2SkillClass, Boolean::class.javaPrimitiveType).invoke(clan, skillObj, false)
                            }
                            clan.javaClass.getMethod("takeReputationScore", Int::class.javaPrimitiveType).invoke(clan, cost)
                        }
                    }

                    mapOf("ok" to true, "message" to "Habilidade ${def.name} nível $nextLevel adquirida com sucesso para o clan!", "skillId" to skillId, "newLevel" to nextLevel, "remainingReputation" to (reputationScore - cost))
                } catch (e: Exception) {
                    runCatching { con.rollback() }
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                    mapOf("ok" to false, "message" to "Erro ao processar compra de habilidade: $msg")
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (e: Exception) {
            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            mapOf("ok" to false, "message" to "Erro de conexão ao comprar habilidade de clan: $msg")
        }
    }

    fun donateClanReputation(login: String, characterId: Int, count: Int): Map<String, Any?> {
        val qty = count.coerceIn(1, 100)
        return try {
            ConnectionPool.getConnection().use { con ->
                val current = loadOwnedClanMemberForSkills(con, login, characterId)
                    ?: return mapOf("ok" to false, "message" to "Personagem não encontrado ou não pertence a um clan.")

                if (!current.isLeader) {
                    return mapOf("ok" to false, "message" to "Apenas o líder principal do clan pode adicionar pontos de reputação.")
                }

                val itemId = runCatching {
                    val configClansClass = Class.forName("ext.mods.config.ConfigClans")
                    configClansClass.getField("SITE_CLAN_REPUTATION_DONATE_ITEM_ID").getInt(null)
                }.getOrDefault(4037)

                val amountPerUse = runCatching {
                    val configClansClass = Class.forName("ext.mods.config.ConfigClans")
                    configClansClass.getField("SITE_CLAN_REPUTATION_DONATE_AMOUNT_PER_USE").getInt(null)
                }.getOrDefault(1000)

                val itemCost = runCatching {
                    val configClansClass = Class.forName("ext.mods.config.ConfigClans")
                    configClansClass.getField("SITE_CLAN_REPUTATION_DONATE_ITEM_COST").getInt(null)
                }.getOrDefault(1)

                val totalItemRequired = (qty * itemCost).toLong()
                val totalReputationAdded = qty * amountPerUse

                val consumableItems = loadConsumableAccountItems(con, login, itemId, totalItemRequired)
                val totalAvailable = consumableItems.fold(0L) { acc, item -> acc + item.count }
                if (totalAvailable < totalItemRequired) {
                    return mapOf("ok" to false, "message" to "Você necessita de $totalItemRequired x Item (ID $itemId) no inventário para adicionar $totalReputationAdded pts de reputação. (Possui: $totalAvailable)")
                }

                con.autoCommit = false
                try {
                    consumeItems(con, consumableItems, totalItemRequired)

                    con.prepareStatement("UPDATE clan_data SET reputation_score = reputation_score + ? WHERE clan_id=?").use { ps ->
                        ps.setInt(1, totalReputationAdded)
                        ps.setInt(2, current.clanId)
                        ps.executeUpdate()
                    }

                    var newReputationScore = 0
                    con.prepareStatement("SELECT reputation_score FROM clan_data WHERE clan_id=? LIMIT 1").use { ps ->
                        ps.setInt(1, current.clanId)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) newReputationScore = rs.getInt("reputation_score")
                        }
                    }

                    recordClanAudit(con, login, characterId, current.clanId, "donate-clan-reputation", "count=$qty", "added=$totalReputationAdded,newTotal=$newReputationScore")
                    con.commit()

                    // Sync GameServer runtime
                    runCatching {
                        val clanTableClass = Class.forName("ext.mods.gameserver.data.sql.ClanTable")
                        val table = clanTableClass.getMethod("getInstance").invoke(null)
                        val clan = clanTableClass.getMethod("getClan", Int::class.javaPrimitiveType).invoke(table, current.clanId)
                        if (clan != null) {
                            clan.javaClass.getMethod("addReputationScore", Int::class.javaPrimitiveType).invoke(clan, totalReputationAdded)
                        }
                    }

                    mapOf(
                        "ok" to true,
                        "message" to "Doação realizada com sucesso! +${totalReputationAdded} pontos de reputação foram adicionados ao clan.",
                        "newReputationScore" to newReputationScore
                    )
                } catch (e: Exception) {
                    runCatching { con.rollback() }
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                    mapOf("ok" to false, "message" to "Erro ao doar reputação: $msg")
                } finally {
                    runCatching { con.autoCommit = true }
                }
            }
        } catch (e: Exception) {
            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            mapOf("ok" to false, "message" to "Erro de conexão ao adicionar reputação do clan: $msg")
        }
    }

    data class VoteRewardItem(
        val itemId: Int,
        val count: Long,
    )

    data class VoteConfigAndStatusResponse(
        val ok: Boolean,
        val enabled: Boolean,
        val voteUrl: String,
        val boostUrl: String = "https://top.l2jbrasil.com/index.php?a=user_cpl&b=boost_server&server_username=dhousefe",
        val ratingUrl: String = "https://top.l2jbrasil.com/index.php?a=rate&u=dhousefe",
        val serverName: String = "dhousefe",
        val serverId: Int,
        val cooldownHours: Int,
        val remainingCooldownSeconds: Long,
        val canVote: Boolean,
        val canRate: Boolean = true,
        val ratingRemainingCooldownSeconds: Long = 0L,
        val ratingCooldownDays: Int = 30,
        val minLevel: Int = 7,
        val minPlaytimeMinutes: Int = 20,
        val rewards: List<VoteRewardItem>,
        val ratingRewards: List<VoteRewardItem> = emptyList(),
        val boostRewards: List<VoteRewardItem> = emptyList(),
        val lastVoteTime: Long?,
        val message: String = "",
    )

    data class VoteIntentRecord(
        val token: String,
        val characterId: Int,
        val characterName: String,
        val accountName: String,
        val ip: String,
        val eventType: String = "vote",
        val timestamp: Long,
        var delivered: Boolean = false,
        var deliverySummary: String = "",
    )

    data class VoteDeliveryResult(
        val ok: Boolean,
        val message: String,
        val duplicate: Boolean = false,
        val characterName: String = "",
        val rewardsGiven: List<VoteRewardItem> = emptyList(),
        val online: Boolean = false,
    )

    private val voteIntentsByToken = ConcurrentHashMap<String, VoteIntentRecord>()
    private val voteIntentsByIp = ConcurrentHashMap<String, VoteIntentRecord>()

    private fun ensureVoteTable(con: Connection) {
        val isSqlite = con.metaData.databaseProductName.contains("SQLite", ignoreCase = true)
        val sql = if (isSqlite) {
            """
            CREATE TABLE IF NOT EXISTS site_vote_logs (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              created_at INTEGER NOT NULL,
              delivery_id TEXT NOT NULL UNIQUE,
              player_name TEXT NOT NULL,
              account_name TEXT NOT NULL DEFAULT '',
              character_id INTEGER NOT NULL DEFAULT 0,
              ip TEXT NOT NULL DEFAULT '',
              event_type TEXT NOT NULL DEFAULT 'vote',
              hwid TEXT NOT NULL DEFAULT '',
              rewards_summary TEXT NOT NULL DEFAULT '',
              status TEXT NOT NULL DEFAULT 'DELIVERED'
            )
            """.trimIndent()
        } else {
            """
            CREATE TABLE IF NOT EXISTS site_vote_logs (
              id BIGINT NOT NULL AUTO_INCREMENT,
              created_at BIGINT NOT NULL,
              delivery_id VARCHAR(128) NOT NULL,
              player_name VARCHAR(45) NOT NULL,
              account_name VARCHAR(45) NOT NULL DEFAULT '',
              character_id INT NOT NULL DEFAULT 0,
              ip VARCHAR(64) NOT NULL DEFAULT '',
              event_type VARCHAR(32) NOT NULL DEFAULT 'vote',
              hwid VARCHAR(64) NOT NULL DEFAULT '',
              rewards_summary VARCHAR(255) NOT NULL DEFAULT '',
              status VARCHAR(32) NOT NULL DEFAULT 'DELIVERED',
              PRIMARY KEY (id),
              UNIQUE KEY uq_site_vote_delivery (delivery_id),
              KEY idx_site_vote_player_created (player_name, created_at),
              KEY idx_site_vote_account_created (account_name, created_at),
              KEY idx_site_vote_ip_created (ip, created_at),
              KEY idx_site_vote_event_acc (account_name, event_type, created_at),
              KEY idx_site_vote_hwid (hwid, event_type, created_at)
            )
            """.trimIndent()
        }
        con.createStatement().use { it.executeUpdate(sql) }

        // Auto-upgrade schema if columns don't exist yet
        runCatching {
            con.createStatement().use { stmt ->
                stmt.executeUpdate("ALTER TABLE site_vote_logs ADD COLUMN event_type TEXT NOT NULL DEFAULT 'vote'")
            }
        }
        runCatching {
            con.createStatement().use { stmt ->
                stmt.executeUpdate("ALTER TABLE site_vote_logs ADD COLUMN hwid TEXT NOT NULL DEFAULT ''")
            }
        }
    }

    private fun getVoteConfig(): Triple<Boolean, String, List<VoteRewardItem>> {
        val enabled = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("ENABLE_VOTE_SYSTEM").getBoolean(null)
        }.getOrDefault(true)

        val voteUrl = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("TOP_L2JBRASIL_VOTE_URL").get(null) as? String
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "https://top.l2jbrasil.com/index.php?a=in&u=dhousefe"

        val rewardsRaw = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("VOTE_REWARD_ITEMS_RAW").get(null) as? String
        }.getOrNull() ?: "4037,5; 57,5000000"

        val rewardsList = mutableListOf<VoteRewardItem>()
        rewardsRaw.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
            val parts = token.split(",")
            if (parts.size == 2) {
                val itemId = parts[0].trim().toIntOrNull() ?: 0
                val count = parts[1].trim().toLongOrNull() ?: 0L
                if (itemId > 0 && count > 0) {
                    rewardsList.add(VoteRewardItem(itemId, count))
                }
            }
        }
        if (rewardsList.isEmpty()) {
            rewardsList.add(VoteRewardItem(4037, 5L))
            rewardsList.add(VoteRewardItem(57, 5000000L))
        }

        return Triple(enabled, voteUrl, rewardsList)
    }

    fun getVoteStatus(login: String?, ip: String): VoteConfigAndStatusResponse {
        val (enabled, voteUrl, rewards) = getVoteConfig()
        val serverName = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("TOP_L2JBRASIL_SERVER_NAME").get(null) as? String
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "dhousefe"

        val cooldownHours = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("VOTE_COOLDOWN_HOURS").getInt(null)
        }.getOrDefault(24)

        val ratingCooldownDays = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("RATING_COOLDOWN_DAYS").getInt(null)
        }.getOrDefault(30)

        val minLevel = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("VOTE_MIN_CHARACTER_LEVEL").getInt(null)
        }.getOrDefault(40)

        val minPlaytimeMinutes = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("VOTE_MIN_PLAYTIME_MINUTES").getInt(null)
        }.getOrDefault(60)

        val serverId = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("TOP_L2JBRASIL_SERVER_ID").getInt(null)
        }.getOrDefault(0)

        val voteCooldownMillis = cooldownHours.toLong() * 3600_000L
        val ratingCooldownMillis = ratingCooldownDays.toLong() * 86400_000L
        val now = System.currentTimeMillis()
        var lastVoteTime: Long? = null
        var lastRatingTime: Long? = null

        try {
            ConnectionPool.getConnection().use { con ->
                ensureVoteTable(con)

                // Query vote cooldown (event_type = 'vote' or empty)
                val sqlVote = if (login != null && login.isNotBlank()) {
                    "SELECT created_at FROM site_vote_logs WHERE (account_name = ? OR ip = ?) AND (event_type = 'vote' OR event_type = '' OR event_type IS NULL) AND status = 'DELIVERED' ORDER BY created_at DESC LIMIT 1"
                } else {
                    "SELECT created_at FROM site_vote_logs WHERE ip = ? AND (event_type = 'vote' OR event_type = '' OR event_type IS NULL) AND status = 'DELIVERED' ORDER BY created_at DESC LIMIT 1"
                }
                con.prepareStatement(sqlVote).use { ps ->
                    if (login != null && login.isNotBlank()) {
                        ps.setString(1, login)
                        ps.setString(2, ip)
                    } else {
                        ps.setString(1, ip)
                    }
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            lastVoteTime = rs.getLong("created_at")
                        }
                    }
                }

                // Query rating cooldown (event_type = 'rating')
                val sqlRating = if (login != null && login.isNotBlank()) {
                    "SELECT created_at FROM site_vote_logs WHERE (account_name = ? OR ip = ?) AND event_type = 'rating' AND status = 'DELIVERED' ORDER BY created_at DESC LIMIT 1"
                } else {
                    "SELECT created_at FROM site_vote_logs WHERE ip = ? AND event_type = 'rating' AND status = 'DELIVERED' ORDER BY created_at DESC LIMIT 1"
                }
                con.prepareStatement(sqlRating).use { ps ->
                    if (login != null && login.isNotBlank()) {
                        ps.setString(1, login)
                        ps.setString(2, ip)
                    } else {
                        ps.setString(1, ip)
                    }
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            lastRatingTime = rs.getLong("created_at")
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val remainingVoteSeconds = if (lastVoteTime != null) {
            val elapsed = now - lastVoteTime!!
            val diff = voteCooldownMillis - elapsed
            if (diff > 0) diff / 1000L else 0L
        } else 0L

        val remainingRatingSeconds = if (lastRatingTime != null) {
            val elapsed = now - lastRatingTime!!
            val diff = ratingCooldownMillis - elapsed
            if (diff > 0) diff / 1000L else 0L
        } else 0L

        val canVote = enabled && remainingVoteSeconds <= 0L
        val canRate = enabled && remainingRatingSeconds <= 0L

        val boostUrl = "https://top.l2jbrasil.com/index.php?a=user_cpl&b=boost_server&server_username=$serverName"
        val ratingUrl = "https://top.l2jbrasil.com/index.php?a=rate&u=$serverName"
        val ratingRewards = getRatingRewards()
        val boostRewards = getBoostRewards()

        return VoteConfigAndStatusResponse(
            ok = true,
            enabled = enabled,
            voteUrl = voteUrl,
            boostUrl = boostUrl,
            ratingUrl = ratingUrl,
            serverName = serverName,
            serverId = serverId,
            cooldownHours = cooldownHours,
            remainingCooldownSeconds = remainingVoteSeconds,
            canVote = canVote,
            canRate = canRate,
            ratingRemainingCooldownSeconds = remainingRatingSeconds,
            ratingCooldownDays = ratingCooldownDays,
            minLevel = minLevel,
            minPlaytimeMinutes = minPlaytimeMinutes,
            rewards = rewards,
            ratingRewards = ratingRewards,
            boostRewards = boostRewards,
            lastVoteTime = lastVoteTime,
            message = if (canVote) "Pronto para votar!" else "Aguarde o tempo de cooldown."
        )
    }

    fun registerVoteIntent(login: String?, characterId: Int, characterName: String, ip: String, eventType: String = "vote"): Map<String, Any?> {
        val (enabled, baseVoteUrl, _) = getVoteConfig()
        if (!enabled) {
            return mapOf("ok" to false, "message" to "O sistema de votos está temporariamente desativado.")
        }

        val requireHmac = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("VOTE_REQUIRE_HMAC_AUTH").getBoolean(null)
        }.getOrDefault(false)

        if (requireHmac && login.isNullOrBlank()) {
            return mapOf("ok" to false, "message" to "É necessário estar conectado à sua conta no painel para registrar intenção de voto.")
        }

        val minLevel = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("VOTE_MIN_CHARACTER_LEVEL").getInt(null)
        }.getOrDefault(40)

        val minPlaytimeMinutes = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("VOTE_MIN_PLAYTIME_MINUTES").getInt(null)
        }.getOrDefault(60)

        var resolvedName = characterName.trim()
        var resolvedId = characterId
        var accountName = login?.trim() ?: ""
        var charLevel = 0
        var charPlaytime = 0

        try {
            ConnectionPool.getConnection().use { con ->
                ensureVoteTable(con)
                if (resolvedId > 0) {
                    con.prepareStatement("SELECT obj_Id, char_name, account_name, level, onlinetime FROM characters WHERE obj_Id=?").use { ps ->
                        ps.setInt(1, resolvedId)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                resolvedName = rs.getString("char_name")
                                resolvedId = rs.getInt("obj_Id")
                                charLevel = rs.getInt("level")
                                charPlaytime = rs.getInt("onlinetime")
                                if (accountName.isEmpty()) accountName = rs.getString("account_name")
                            } else {
                                return mapOf("ok" to false, "message" to "Personagem selecionado não foi encontrado.")
                            }
                        }
                    }
                } else if (resolvedName.isNotEmpty()) {
                    con.prepareStatement("SELECT obj_Id, char_name, account_name, level, onlinetime FROM characters WHERE LOWER(char_name)=LOWER(?)").use { ps ->
                        ps.setString(1, resolvedName)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                resolvedName = rs.getString("char_name")
                                resolvedId = rs.getInt("obj_Id")
                                charLevel = rs.getInt("level")
                                charPlaytime = rs.getInt("onlinetime")
                                if (accountName.isEmpty()) accountName = rs.getString("account_name")
                            } else {
                                return mapOf("ok" to false, "message" to "Personagem '$resolvedName' não foi encontrado.")
                            }
                        }
                    }
                } else {
                    return mapOf("ok" to false, "message" to "Nenhum personagem informado.")
                }

                // Check ownership if login is specified
                if (login != null && login.isNotBlank() && !accountName.equals(login, ignoreCase = true)) {
                    return mapOf("ok" to false, "message" to "O personagem não pertence à conta conectada.")
                }

                // Level requirement check
                if (charLevel < minLevel) {
                    return mapOf("ok" to false, "message" to "O personagem $resolvedName precisa ser nível $minLevel ou superior para receber recompensas de voto (Nível atual: $charLevel).")
                }

                // Playtime requirement check (onlinetime is stored in seconds in L2J)
                val requiredSeconds = minPlaytimeMinutes * 60
                if (charPlaytime < requiredSeconds) {
                    val currentMins = charPlaytime / 60
                    return mapOf("ok" to false, "message" to "O personagem $resolvedName precisa ter no mínimo $minPlaytimeMinutes minutos de jogo acumulados (Tempo atual: ${currentMins}m).")
                }
            }
        } catch (e: Exception) {
            return mapOf("ok" to false, "message" to "Erro ao validar personagem: ${e.message}")
        }

        val normEvent = eventType.lowercase().trim().ifEmpty { "vote" }
        val token = UUID.randomUUID().toString().replace("-", "")
        val record = VoteIntentRecord(
            token = token,
            characterId = resolvedId,
            characterName = resolvedName,
            accountName = accountName,
            ip = ip,
            eventType = normEvent,
            timestamp = System.currentTimeMillis(),
            delivered = false,
            deliverySummary = ""
        )

        voteIntentsByToken[token] = record
        if (ip.isNotBlank()) {
            voteIntentsByIp[ip] = record
        }

        // Clean old intents (> 30 mins)
        val cutoff = System.currentTimeMillis() - 1800_000L
        voteIntentsByToken.entries.removeIf { it.value.timestamp < cutoff }
        voteIntentsByIp.entries.removeIf { it.value.timestamp < cutoff }

        val finalUrl = if (baseVoteUrl.contains("?")) {
            "$baseVoteUrl&track=$token"
        } else {
            "$baseVoteUrl?track=$token"
        }

        return mapOf(
            "ok" to true,
            "token" to token,
            "characterId" to resolvedId,
            "characterName" to resolvedName,
            "eventType" to normEvent,
            "voteUrl" to finalUrl,
            "message" to "Intenção registrada com sucesso. Redirecionando para o Top L2JBrasil..."
        )
    }

    fun checkVoteIntentStatus(token: String): Map<String, Any?> {
        val trimmed = token.trim()
        val record = voteIntentsByToken[trimmed]
            ?: return mapOf("ok" to true, "found" to false, "delivered" to false)

        return mapOf(
            "ok" to true,
            "found" to true,
            "token" to record.token,
            "delivered" to record.delivered,
            "eventType" to record.eventType,
            "characterName" to record.characterName,
            "deliverySummary" to record.deliverySummary
        )
    }

    private fun getRatingRewards(): List<VoteRewardItem> {
        val raw = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("RATING_REWARD_ITEMS_RAW").get(null) as? String
        }.getOrNull() ?: "4037,20; 57,1000000"
        return parseRewardList(raw, fallback = listOf(VoteRewardItem(4037, 20L), VoteRewardItem(57, 1000000L)))
    }

    private fun getBoostRewards(): List<VoteRewardItem> {
        val raw = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("BOOST_REWARD_ITEMS_RAW").get(null) as? String
        }.getOrNull() ?: "4037,50; 57,10000000"
        return parseRewardList(raw, fallback = listOf(VoteRewardItem(4037, 50L), VoteRewardItem(57, 10000000L)))
    }

    private fun parseRewardList(raw: String, fallback: List<VoteRewardItem>): List<VoteRewardItem> {
        val list = mutableListOf<VoteRewardItem>()
        raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
            val parts = token.split(",")
            if (parts.size == 2) {
                val itemId = parts[0].trim().toIntOrNull() ?: 0
                val count = parts[1].trim().toLongOrNull() ?: 0L
                if (itemId > 0 && count > 0) {
                    list.add(VoteRewardItem(itemId, count))
                }
            }
        }
        return if (list.isNotEmpty()) list else fallback
    }

    fun deliverVoteReward(
        deliveryId: String,
        charNameOrTrack: String?,
        voterIp: String,
        eventType: String,
        rating: Int? = null,
        quantity: Int = 1,
        serverName: String? = null
    ): VoteDeliveryResult {
        if (deliveryId.isBlank()) {
            return VoteDeliveryResult(ok = false, message = "delivery_id não pode ser vazio.")
        }

        // 1. Deduplication check on unique delivery_id
        try {
            ConnectionPool.getConnection().use { con ->
                ensureVoteTable(con)
                con.prepareStatement("SELECT id, player_name, status FROM site_vote_logs WHERE delivery_id = ?").use { ps ->
                    ps.setString(1, deliveryId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            val status = rs.getString("status")
                            if (status == "DELIVERED") {
                                return VoteDeliveryResult(
                                    ok = true,
                                    duplicate = true,
                                    characterName = rs.getString("player_name"),
                                    message = "Evento já processado anteriormente (deduplicado)."
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return VoteDeliveryResult(ok = false, message = "Erro ao checar deduplicação: ${e.message}")
        }

        val normEvent = eventType.lowercase().trim().ifEmpty { "vote" }
        val effectiveQty = Math.max(1, quantity)

        // Select rewards & announcement configuration based on event type
        val (rewards: List<VoteRewardItem>, announceEnabled: Boolean, announceTemplate: String, eventTag: String) = when (normEvent) {
            "rating" -> {
                val rList = getRatingRewards()
                val ann = runCatching {
                    val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
                    cls.getField("RATING_REWARD_ANNOUNCE").getBoolean(null)
                }.getOrDefault(true)
                val msg = runCatching {
                    val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
                    cls.getField("RATING_REWARD_ANNOUNCE_MSG").get(null) as? String
                }.getOrNull() ?: "O jogador %s avaliou o servidor no Top L2JBrasil com nota %d estrelas!"
                val stars = rating ?: 5
                Quad(rList, ann, msg, "[RATING:${stars}★]")
            }
            "boost" -> {
                val rList = getBoostRewards().map { it.copy(count = it.count * effectiveQty) }
                val ann = runCatching {
                    val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
                    cls.getField("BOOST_REWARD_ANNOUNCE").getBoolean(null)
                }.getOrDefault(true)
                val msg = runCatching {
                    val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
                    cls.getField("BOOST_REWARD_ANNOUNCE_MSG").get(null) as? String
                }.getOrNull() ?: "O jogador %s ativou um BOOST (%dx) no Top L2JBrasil e turbinou o servidor!"
                Quad(rList, ann, msg, "[BOOST:${effectiveQty}x]")
            }
            else -> {
                val (_, _, rList) = getVoteConfig()
                val ann = runCatching {
                    val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
                    cls.getField("VOTE_REWARD_ANNOUNCE").getBoolean(null)
                }.getOrDefault(true)
                val msg = runCatching {
                    val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
                    cls.getField("VOTE_REWARD_ANNOUNCE_MSG").get(null) as? String
                }.getOrNull() ?: "O jogador %s votou no Top L2JBrasil e recebeu sua recompensa!"
                Quad(rList, ann, msg, "[VOTE]")
            }
        }

        val summary = "$eventTag " + rewards.joinToString("; ") { "${it.itemId}x${it.count}" }

        val allowOffline = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("ALLOW_OFFLINE_DELIVERY").getBoolean(null)
        }.getOrDefault(true)

        val minLevel = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("VOTE_MIN_CHARACTER_LEVEL").getInt(null)
        }.getOrDefault(40)

        val minPlaytimeMinutes = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("VOTE_MIN_PLAYTIME_MINUTES").getInt(null)
        }.getOrDefault(60)

        val checkHwid = runCatching {
            val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
            cls.getField("VOTE_CHECK_HWID").getBoolean(null)
        }.getOrDefault(true)

        // 2. Resolve Target Character
        var targetCharName: String? = null
        var targetCharId = 0
        var targetAccountName = ""
        var targetCharLevel = 0
        var targetCharPlaytime = 0
        var targetHwid = ""

        // 2.1 Check if charNameOrTrack matches a pending intent token
        if (!charNameOrTrack.isNullOrBlank()) {
            val intent = voteIntentsByToken[charNameOrTrack]
            if (intent != null) {
                targetCharName = intent.characterName
                targetCharId = intent.characterId
                targetAccountName = intent.accountName
            }
        }

        // 2.2 Check pending intent by IP
        if (targetCharName == null && voterIp.isNotBlank()) {
            val intent = voteIntentsByIp[voterIp]
            if (intent != null) {
                targetCharName = intent.characterName
                targetCharId = intent.characterId
                targetAccountName = intent.accountName
            }
        }

        // 2.3 Check directly by character name (case-insensitive)
        if (targetCharName == null && !charNameOrTrack.isNullOrBlank()) {
            try {
                ConnectionPool.getConnection().use { con ->
                    con.prepareStatement("SELECT obj_Id, char_name, account_name, level, onlinetime FROM characters WHERE LOWER(char_name) = LOWER(?)").use { ps ->
                        ps.setString(1, charNameOrTrack.trim())
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                targetCharId = rs.getInt("obj_Id")
                                targetCharName = rs.getString("char_name")
                                targetAccountName = rs.getString("account_name")
                                targetCharLevel = rs.getInt("level")
                                targetCharPlaytime = rs.getInt("onlinetime")
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2.4 Check directly by account name (highest level character on account)
        if (targetCharName == null && !charNameOrTrack.isNullOrBlank()) {
            try {
                ConnectionPool.getConnection().use { con ->
                    con.prepareStatement("SELECT obj_Id, char_name, account_name, level, onlinetime FROM characters WHERE LOWER(account_name) = LOWER(?) ORDER BY level DESC LIMIT 1").use { ps ->
                        ps.setString(1, charNameOrTrack.trim())
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                targetCharId = rs.getInt("obj_Id")
                                targetCharName = rs.getString("char_name")
                                targetAccountName = rs.getString("account_name")
                                targetCharLevel = rs.getInt("level")
                                targetCharPlaytime = rs.getInt("onlinetime")
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2.5 Check most recent intent in memory if only 1 voter recently clicked
        if (targetCharName == null && voteIntentsByToken.isNotEmpty()) {
            val recent = voteIntentsByToken.values.maxByOrNull { it.timestamp }
            if (recent != null && (System.currentTimeMillis() - recent.timestamp) <= 15 * 60 * 1000L) {
                targetCharName = recent.characterName
                targetCharId = recent.characterId
                targetAccountName = recent.accountName
            }
        }

        // 2.6 Fallback: query online character with activity
        if (targetCharName == null) {
            try {
                ConnectionPool.getConnection().use { con ->
                    con.prepareStatement("SELECT obj_Id, char_name, account_name, level, onlinetime FROM characters WHERE online=1 ORDER BY level DESC LIMIT 1").use { ps ->
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                targetCharId = rs.getInt("obj_Id")
                                targetCharName = rs.getString("char_name")
                                targetAccountName = rs.getString("account_name")
                                targetCharLevel = rs.getInt("level")
                                targetCharPlaytime = rs.getInt("onlinetime")
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // If target was found via intent but char level/playtime isn't loaded yet, load it now
        if (targetCharId > 0 && targetCharLevel == 0) {
            try {
                ConnectionPool.getConnection().use { con ->
                    con.prepareStatement("SELECT level, onlinetime FROM characters WHERE obj_Id=?").use { ps ->
                        ps.setInt(1, targetCharId)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                targetCharLevel = rs.getInt("level")
                                targetCharPlaytime = rs.getInt("onlinetime")
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // If still cannot resolve character, record failed log and return
        if (targetCharName == null || targetCharId <= 0) {
            recordVoteLog(deliveryId, charNameOrTrack ?: "UNKNOWN", "", 0, voterIp, normEvent, targetHwid, summary, "FAILED_CHAR_NOT_FOUND")
            return VoteDeliveryResult(ok = false, message = "Não foi possível identificar o personagem para creditar a recompensa do evento $normEvent.")
        }

        var isOnline = false

        // 3. Check if player is online via World and extract HWID
        val onlinePlayer = runCatching {
            val worldClass = Class.forName("ext.mods.gameserver.model.World")
            worldClass.getMethod("getPlayer", String::class.java).invoke(null, targetCharName)
        }.getOrNull()

        if (onlinePlayer != null) {
            isOnline = true
            // Read HWID from online player
            if (checkHwid) {
                targetHwid = runCatching {
                    val getHwidMethod = onlinePlayer.javaClass.methods.firstOrNull { it.name == "getHWid" || it.name == "getHWID" }
                    getHwidMethod?.invoke(onlinePlayer) as? String
                }.getOrNull() ?: ""
            }
        }

        // 4. Anti-Exploit Level & Playtime Requirements Check
        if (targetCharLevel < minLevel) {
            recordVoteLog(deliveryId, targetCharName, targetAccountName, targetCharId, voterIp, normEvent, targetHwid, summary, "FAILED_MIN_LEVEL")
            return VoteDeliveryResult(
                ok = false,
                duplicate = false,
                characterName = targetCharName,
                message = "Personagem $targetCharName (Nível $targetCharLevel) não atingiu o nível mínimo requerido ($minLevel) para receber recompensas."
            )
        }

        val requiredPlaytimeSeconds = minPlaytimeMinutes * 60
        if (targetCharPlaytime < requiredPlaytimeSeconds) {
            recordVoteLog(deliveryId, targetCharName, targetAccountName, targetCharId, voterIp, normEvent, targetHwid, summary, "FAILED_MIN_PLAYTIME")
            return VoteDeliveryResult(
                ok = false,
                duplicate = false,
                characterName = targetCharName,
                message = "Personagem $targetCharName não possui tempo de jogo online suficiente (${targetCharPlaytime / 60}m / ${minPlaytimeMinutes}m requeridos)."
            )
        }

        // 5. Anti-Exploit Cooldown Check (Segregated by Event Type & HWID)
        val now = System.currentTimeMillis()
        if (normEvent == "vote") {
            val cooldownHours = runCatching {
                val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
                cls.getField("VOTE_COOLDOWN_HOURS").getInt(null)
            }.getOrDefault(24)
            val cooldownMillis = cooldownHours.toLong() * 3600_000L

            var isCooldownActive = false
            try {
                ConnectionPool.getConnection().use { con ->
                    val sql = if (targetHwid.isNotBlank()) {
                        "SELECT created_at FROM site_vote_logs WHERE (account_name = ? OR ip = ? OR (hwid != '' AND hwid = ?)) AND (event_type = 'vote' OR event_type = '' OR event_type IS NULL) AND status = 'DELIVERED' ORDER BY created_at DESC LIMIT 1"
                    } else {
                        "SELECT created_at FROM site_vote_logs WHERE (account_name = ? OR ip = ?) AND (event_type = 'vote' OR event_type = '' OR event_type IS NULL) AND status = 'DELIVERED' ORDER BY created_at DESC LIMIT 1"
                    }
                    con.prepareStatement(sql).use { ps ->
                        ps.setString(1, targetAccountName)
                        ps.setString(2, voterIp)
                        if (targetHwid.isNotBlank()) ps.setString(3, targetHwid)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                val lastAt = rs.getLong("created_at")
                                if (now - lastAt < cooldownMillis) {
                                    isCooldownActive = true
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            if (isCooldownActive) {
                recordVoteLog(deliveryId, targetCharName, targetAccountName, targetCharId, voterIp, normEvent, targetHwid, summary, "FAILED_COOLDOWN_ACTIVE")
                return VoteDeliveryResult(
                    ok = true,
                    duplicate = true,
                    characterName = targetCharName,
                    message = "Cooldown de voto ativo para esta conta/IP/máquina. Recompensa ignorada para evitar abuso."
                )
            }
        } else if (normEvent == "rating") {
            val ratingCooldownDays = runCatching {
                val cls = Class.forName("ext.mods.config.ConfigVoteL2JBrasil")
                cls.getField("RATING_COOLDOWN_DAYS").getInt(null)
            }.getOrDefault(30)
            val ratingCooldownMillis = ratingCooldownDays.toLong() * 86400_000L

            var isRatingUsed = false
            try {
                ConnectionPool.getConnection().use { con ->
                    val sql = if (targetHwid.isNotBlank()) {
                        "SELECT created_at FROM site_vote_logs WHERE (account_name = ? OR ip = ? OR (hwid != '' AND hwid = ?)) AND event_type = 'rating' AND status = 'DELIVERED' ORDER BY created_at DESC LIMIT 1"
                    } else {
                        "SELECT created_at FROM site_vote_logs WHERE (account_name = ? OR ip = ?) AND event_type = 'rating' AND status = 'DELIVERED' ORDER BY created_at DESC LIMIT 1"
                    }
                    con.prepareStatement(sql).use { ps ->
                        ps.setString(1, targetAccountName)
                        ps.setString(2, voterIp)
                        if (targetHwid.isNotBlank()) ps.setString(3, targetHwid)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                val lastAt = rs.getLong("created_at")
                                if (now - lastAt < ratingCooldownMillis) {
                                    isRatingUsed = true
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            if (isRatingUsed) {
                recordVoteLog(deliveryId, targetCharName, targetAccountName, targetCharId, voterIp, normEvent, targetHwid, summary, "FAILED_RATING_ALREADY_USED")
                return VoteDeliveryResult(
                    ok = true,
                    duplicate = true,
                    characterName = targetCharName,
                    message = "Avaliação já computada e recompensada anteriormente para esta conta/IP/máquina."
                )
            }
        }

        // 6. Deliver Item Rewards (Online or Offline)
        if (onlinePlayer != null) {
            rewards.forEach { r ->
                val delivered = deliverOnlineItem(onlinePlayer, r.itemId, r.count)
                if (!delivered && allowOffline) {
                    deliverOfflineItem(targetCharId, r.itemId, r.count)
                }
            }
            // Update inventory packet
            runCatching {
                val itemListClass = Class.forName("ext.mods.gameserver.network.serverpackets.ItemList")
                val constructor = itemListClass.getConstructor(onlinePlayer.javaClass, Boolean::class.javaPrimitiveType)
                val packet = constructor.newInstance(onlinePlayer, false)
                val l2PacketClass = Class.forName("ext.mods.gameserver.network.serverpackets.L2GameServerPacket")
                onlinePlayer.javaClass.getMethod("sendPacket", l2PacketClass).invoke(onlinePlayer, packet)
            }
            // Play Sound
            runCatching {
                val playSoundClass = Class.forName("ext.mods.gameserver.network.serverpackets.PlaySound")
                val soundPacket = playSoundClass.getConstructor(String::class.java).newInstance("ItemSound.quest_finish")
                val l2PacketClass = Class.forName("ext.mods.gameserver.network.serverpackets.L2GameServerPacket")
                onlinePlayer.javaClass.getMethod("sendPacket", l2PacketClass).invoke(onlinePlayer, soundPacket)
            }
        } else if (allowOffline) {
            // Offline item delivery directly into DB
            try {
                rewards.forEach { r ->
                    deliverOfflineItem(targetCharId, r.itemId, r.count)
                }
            } catch (e: Exception) {
                recordVoteLog(deliveryId, targetCharName, targetAccountName, targetCharId, voterIp, normEvent, targetHwid, summary, "FAILED_DB")
                return VoteDeliveryResult(ok = false, message = "Falha ao gravar recompensa offline: ${e.message}")
            }
        } else {
            recordVoteLog(deliveryId, targetCharName, targetAccountName, targetCharId, voterIp, normEvent, targetHwid, summary, "FAILED_OFFLINE_DISABLED")
            return VoteDeliveryResult(ok = false, message = "Personagem offline e AllowOfflineDelivery está desativado.")
        }

        // 7. Global Announcement if configured
        if (announceEnabled) {
            val announcementText = runCatching {
                val extraNum = if (normEvent == "rating") (rating ?: 5) else effectiveQty
                if (announceTemplate.contains("%d") && announceTemplate.contains("%s")) {
                    String.format(announceTemplate, targetCharName, extraNum)
                } else if (announceTemplate.contains("%s")) {
                    String.format(announceTemplate, targetCharName)
                } else {
                    announceTemplate
                }
            }.getOrElse { announceTemplate.replace("%s", targetCharName) }

            runCatching {
                val worldClass = Class.forName("ext.mods.gameserver.model.World")
                worldClass.getMethod("announceToOnlinePlayers", String::class.java, Boolean::class.javaPrimitiveType)
                    .invoke(null, announcementText, false)
            }
        }

        // 8. Record successful log into site_vote_logs
        recordVoteLog(deliveryId, targetCharName, targetAccountName, targetCharId, voterIp, normEvent, targetHwid, summary, "DELIVERED")

        // 9. Mark matching pending intents as delivered
        if (!charNameOrTrack.isNullOrBlank()) {
            voteIntentsByToken[charNameOrTrack]?.let {
                it.delivered = true
                it.deliverySummary = summary
            }
        }
        voteIntentsByToken.values.filter {
            it.characterName.equals(targetCharName, ignoreCase = true) || (voterIp.isNotBlank() && it.ip == voterIp)
        }.forEach {
            it.delivered = true
            it.deliverySummary = summary
        }

        return VoteDeliveryResult(
            ok = true,
            characterName = targetCharName,
            rewardsGiven = rewards,
            online = isOnline,
            message = "Recompensa de $normEvent entregue com sucesso para $targetCharName (${if (isOnline) "Online" else "Offline"})."
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun recordVoteLog(
        deliveryId: String,
        playerName: String,
        accountName: String,
        characterId: Int,
        ip: String,
        eventType: String,
        hwid: String,
        rewardsSummary: String,
        status: String
    ) {
        try {
            ConnectionPool.getConnection().use { con ->
                ensureVoteTable(con)
                con.prepareStatement(
                    """
                    INSERT INTO site_vote_logs (created_at, delivery_id, player_name, account_name, character_id, ip, event_type, hwid, rewards_summary, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { ps ->
                    ps.setLong(1, System.currentTimeMillis())
                    ps.setString(2, deliveryId)
                    ps.setString(3, playerName.take(45))
                    ps.setString(4, accountName.take(45))
                    ps.setInt(5, characterId)
                    ps.setString(6, ip.take(64))
                    ps.setString(7, eventType.take(32))
                    ps.setString(8, hwid.take(64))
                    ps.setString(9, rewardsSummary.take(255))
                    ps.setString(10, status.take(32))
                    ps.executeUpdate()
                }
            }
        } catch (_: Exception) {}
    }

    private fun resolveItemName(itemId: Int, fallbackName: String = ""): String {
        val fromTemplate = runCatching {
            val itemDataClass = Class.forName("ext.mods.gameserver.data.xml.ItemData")
            val itemData = itemDataClass.getMethod("getInstance").invoke(null)
            val template = itemDataClass.getMethod("getTemplate", Int::class.javaPrimitiveType).invoke(itemData, itemId)
            template?.javaClass?.getMethod("getName")?.invoke(template) as? String
        }.getOrNull()

        if (!fromTemplate.isNullOrBlank()) return fromTemplate
        if (itemId == 57) return "Adena"
        if (itemId == 4037) return "Coin of Luck"
        return if (fallbackName.isNotBlank()) fallbackName else "Item #$itemId"
    }

    private fun ensureShopPurchasesTable(con: java.sql.Connection) {
        val isSqlite = con.metaData.databaseProductName.contains("SQLite", ignoreCase = true)
        val sql = if (isSqlite) {
            """
            CREATE TABLE IF NOT EXISTS site_shop_purchases (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              created_at INTEGER NOT NULL,
              account_name TEXT NOT NULL,
              character_id INTEGER NOT NULL DEFAULT 0,
              character_name TEXT NOT NULL,
              item_key TEXT NOT NULL,
              item_id INTEGER NOT NULL,
              item_name TEXT NOT NULL,
              item_count INTEGER NOT NULL,
              coin_price INTEGER NOT NULL,
              quantity INTEGER NOT NULL,
              total_coins INTEGER NOT NULL,
              delivery_mode TEXT NOT NULL DEFAULT 'ONLINE',
              status TEXT NOT NULL DEFAULT 'COMPLETED'
            )
            """.trimIndent()
        } else {
            """
            CREATE TABLE IF NOT EXISTS site_shop_purchases (
              id BIGINT NOT NULL AUTO_INCREMENT,
              created_at BIGINT NOT NULL,
              account_name VARCHAR(45) NOT NULL,
              character_id INT NOT NULL DEFAULT 0,
              character_name VARCHAR(45) NOT NULL,
              item_key VARCHAR(100) NOT NULL,
              item_id INT NOT NULL,
              item_name VARCHAR(100) NOT NULL,
              item_count BIGINT NOT NULL,
              coin_price BIGINT NOT NULL,
              quantity INT NOT NULL,
              total_coins BIGINT NOT NULL,
              delivery_mode VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
              status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
              PRIMARY KEY (id),
              KEY idx_site_shop_acc_created (account_name, created_at)
            )
            """.trimIndent()
        }
        con.createStatement().use { it.executeUpdate(sql) }
        if (isSqlite) {
            runCatching {
                con.createStatement().use { it.executeUpdate("CREATE INDEX IF NOT EXISTS idx_site_shop_acc_created ON site_shop_purchases(account_name, created_at)") }
            }
        }
    }

    data class ShopPurchaseRecord(
        val id: Long,
        val createdAt: Long,
        val accountName: String,
        val characterId: Int,
        val characterName: String,
        val itemKey: String,
        val itemId: Int,
        val itemName: String,
        val itemCount: Long,
        val coinPrice: Long,
        val quantity: Int,
        val totalCoins: Long,
        val deliveryMode: String,
        val status: String,
    )

    fun getShopPurchasesForAccount(accountName: String): List<ShopPurchaseRecord> {
        if (accountName.isBlank()) return emptyList()
        val list = mutableListOf<ShopPurchaseRecord>()
        try {
            ConnectionPool.getConnection().use { con ->
                ensureShopPurchasesTable(con)
                con.prepareStatement(
                    """
                    SELECT id, created_at, account_name, character_id, character_name, item_key, item_id, item_name, item_count, coin_price, quantity, total_coins, delivery_mode, status
                    FROM site_shop_purchases
                    WHERE account_name = ?
                    ORDER BY created_at DESC
                    LIMIT 50
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, accountName)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            list.add(
                                ShopPurchaseRecord(
                                    id = rs.getLong("id"),
                                    createdAt = rs.getLong("created_at"),
                                    accountName = rs.getString("account_name"),
                                    characterId = rs.getInt("character_id"),
                                    characterName = rs.getString("character_name") ?: "",
                                    itemKey = rs.getString("item_key") ?: "",
                                    itemId = rs.getInt("item_id"),
                                    itemName = rs.getString("item_name") ?: "",
                                    itemCount = rs.getLong("item_count"),
                                    coinPrice = rs.getLong("coin_price"),
                                    quantity = rs.getInt("quantity"),
                                    totalCoins = rs.getLong("total_coins"),
                                    deliveryMode = rs.getString("delivery_mode") ?: "ONLINE",
                                    status = rs.getString("status") ?: "COMPLETED"
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun deliverOnlineItem(player: Any, itemId: Int, count: Long): Boolean {
        val safeCount = count.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        val method = runCatching {
            player.javaClass.methods.firstOrNull {
                it.name == "addItem" && it.parameterCount == 3 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                it.parameterTypes[2] == Boolean::class.javaPrimitiveType
            }
        }.getOrNull()

        if (method != null) {
            val res = runCatching { method.invoke(player, itemId, safeCount, true) }
            if (res.isSuccess) return true
        }

        val invMethod = runCatching { player.javaClass.getMethod("getInventory") }.getOrNull()
        if (invMethod != null) {
            val inv = runCatching { invMethod.invoke(player) }.getOrNull()
            if (inv != null) {
                val addInvMethod = inv.javaClass.methods.firstOrNull {
                    it.name == "addItem" && it.parameterCount >= 2 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType
                }
                if (addInvMethod != null) {
                    val countArg = if (addInvMethod.parameterTypes[1] == Long::class.javaPrimitiveType) count else safeCount
                    val res = runCatching {
                        if (addInvMethod.parameterCount == 2) {
                            addInvMethod.invoke(inv, itemId, countArg)
                        } else {
                            addInvMethod.invoke(inv, itemId, countArg, true)
                        }
                    }
                    if (res.isSuccess) return true
                }
            }
        }
        return false
    }

    private fun deliverOfflineItem(con: java.sql.Connection, targetCharId: Int, itemId: Int, count: Long) {
        var updated = false
        con.prepareStatement(
            "SELECT object_id, count FROM items WHERE owner_id=? AND item_id=? AND loc='INVENTORY' LIMIT 1"
        ).use { ps ->
            ps.setInt(1, targetCharId)
            ps.setInt(2, itemId)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    val objId = rs.getInt("object_id")
                    con.prepareStatement("UPDATE items SET count = count + ? WHERE object_id = ?").use { ups ->
                        ups.setLong(1, count)
                        ups.setInt(2, objId)
                        ups.executeUpdate()
                    }
                    updated = true
                }
            }
        }

        if (!updated) {
            val nextObjId = runCatching {
                val idFactoryClass = Class.forName("ext.mods.gameserver.idfactory.IdFactory")
                val idFactory = idFactoryClass.getMethod("getInstance").invoke(null)
                idFactoryClass.getMethod("getNextId").invoke(idFactory) as Int
            }.getOrElse {
                con.prepareStatement("SELECT COALESCE(MAX(object_id), 268435456) + 1 AS next_id FROM items").use { ps ->
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("next_id") else 268435457 }
                }
            }

            con.prepareStatement(
                "INSERT INTO items (owner_id, object_id, item_id, count, enchant_level, loc, loc_data, custom_type1, custom_type2, mana_left, time) VALUES (?, ?, ?, ?, 0, 'INVENTORY', 0, 0, 0, -1, 0)"
            ).use { ins ->
                ins.setInt(1, targetCharId)
                ins.setInt(2, nextObjId)
                ins.setInt(3, itemId)
                ins.setLong(4, count)
                ins.executeUpdate()
            }
        }
    }

    private fun deliverOfflineItem(targetCharId: Int, itemId: Int, count: Long) {
        ConnectionPool.getConnection().use { con ->
            con.autoCommit = false
            try {
                deliverOfflineItem(con, targetCharId, itemId, count)
                con.commit()
            } catch (e: Exception) {
                con.rollback()
                throw e
            } finally {
                con.autoCommit = true
            }
        }
    }

    /** Checks if a character belongs to the given account and is not pending deletion. */
    fun ownsCharacter(login: String, characterId: Int): Boolean {
        if (login.isBlank() || characterId <= 0) return false
        return try {
            ConnectionPool.getConnection().use { con ->
                con.prepareStatement(
                    "SELECT 1 FROM characters WHERE obj_Id=? AND account_name=? AND COALESCE(deletetime, 0)=0 LIMIT 1"
                ).use { ps ->
                    ps.setInt(1, characterId)
                    ps.setString(2, login)
                    ps.executeQuery().use { rs -> rs.next() }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    data class DonationShopBuyResult(
        val ok: Boolean,
        val message: String,
        val remainingCoins: Long = 0L,
        val itemId: Int = 0,
        val itemCount: Long = 0L,
    )

    fun buyDonationShopItem(
        login: String,
        characterId: Int,
        itemKey: String,
        itemId: Int,
        itemCount: Long,
        coinPrice: Long,
        quantity: Int,
        clientIp: String,
        idempotencyKey: String,
    ): DonationShopBuyResult {
        if (!ownsCharacter(login, characterId)) {
            return DonationShopBuyResult(false, "Personagem não pertence à sua conta.")
        }
        if (itemId <= 0 || itemCount <= 0L || coinPrice <= 0L) {
            return DonationShopBuyResult(false, "Configuração de item inválida.")
        }
        val safeQty = quantity.coerceIn(1, 100)
        val totalCost = coinPrice * safeQty
        val totalDeliverCount = itemCount * safeQty

        val coinItemId = runCatching {
            val configPixClass = Class.forName("ext.mods.config.ConfigPix")
            configPixClass.getField("PURCHASABLE_ITEM_ID").getInt(null)
        }.getOrNull() ?: 4037

        return try {
            ConnectionPool.getConnection().use { con ->
                ensureShopPurchasesTable(con)
                con.autoCommit = false
                try {
                    // 1. Carregar moedas da conta sob a mesma conexão transacional
                    val consumableCoins = loadConsumableAccountItems(con, login, coinItemId, totalCost)
                    val currentBalance = consumableCoins.sumOf { it.count }
                    if (currentBalance < totalCost) {
                        con.rollback()
                        return@use DonationShopBuyResult(
                            false,
                            "Saldo insuficiente de coins. Necessário: $totalCost Coins, Saldo atual: $currentBalance Coins.",
                            currentBalance
                        )
                    }

                    // 2. Consumir as moedas de doação na mesma conexão
                    consumeItems(con, consumableCoins, totalCost)

                    // 3. Resolver nome do personagem para entrega online/offline
                    var targetCharName: String? = null
                    con.prepareStatement("SELECT char_name FROM characters WHERE obj_Id=? LIMIT 1").use { ps ->
                        ps.setInt(1, characterId)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) targetCharName = rs.getString("char_name")
                        }
                    }

                    if (targetCharName.isNullOrBlank()) {
                        con.rollback()
                        return@use DonationShopBuyResult(false, "Personagem não encontrado.")
                    }

                    // 4. Checar se o jogador está online no mundo via World.getInstance()
                    val onlinePlayer = runCatching {
                        val worldClass = Class.forName("ext.mods.gameserver.model.World")
                        val worldInstance = worldClass.getMethod("getInstance").invoke(null)
                        var p = runCatching {
                            worldClass.getMethod("getPlayer", Int::class.javaPrimitiveType).invoke(worldInstance, characterId)
                        }.getOrNull()
                        if (p == null && !targetCharName.isNullOrBlank()) {
                            p = runCatching {
                                worldClass.getMethod("getPlayer", String::class.java).invoke(worldInstance, targetCharName)
                            }.getOrNull()
                        }
                        p
                    }.getOrNull()

                    var deliveredOnline = false
                    if (onlinePlayer != null) {
                        deliveredOnline = deliverOnlineItem(onlinePlayer, itemId, totalDeliverCount)
                        if (!deliveredOnline) {
                            // Se falhou entrega online, fallback para entrega offline na mesma conexão
                            deliverOfflineItem(con, characterId, itemId, totalDeliverCount)
                        } else {
                            // Notificação de inventário e som de conquista
                            runCatching {
                                val itemListClass = Class.forName("ext.mods.gameserver.network.serverpackets.ItemList")
                                val constructor = itemListClass.getConstructor(onlinePlayer.javaClass, Boolean::class.javaPrimitiveType)
                                val packet = constructor.newInstance(onlinePlayer, false)
                                val l2PacketClass = Class.forName("ext.mods.gameserver.network.serverpackets.L2GameServerPacket")
                                onlinePlayer.javaClass.getMethod("sendPacket", l2PacketClass).invoke(onlinePlayer, packet)
                            }
                            runCatching {
                                val playSoundClass = Class.forName("ext.mods.gameserver.network.serverpackets.PlaySound")
                                val soundPacket = playSoundClass.getConstructor(String::class.java).newInstance("ItemSound.quest_finish")
                                val l2PacketClass = Class.forName("ext.mods.gameserver.network.serverpackets.L2GameServerPacket")
                                onlinePlayer.javaClass.getMethod("sendPacket", l2PacketClass).invoke(onlinePlayer, soundPacket)
                            }
                        }
                    } else {
                        // Offline: entrega direta usando a conexão con
                        deliverOfflineItem(con, characterId, itemId, totalDeliverCount)
                    }

                    // 5. Registrar no histórico auditável de compras da loja (site_shop_purchases)
                    val friendlyItemName = resolveItemName(itemId, itemKey)
                    val deliveryMode = if (deliveredOnline) "ONLINE" else "OFFLINE"
                    con.prepareStatement(
                        """
                        INSERT INTO site_shop_purchases (
                            created_at, account_name, character_id, character_name, item_key, item_id, item_name, item_count, coin_price, quantity, total_coins, delivery_mode, status
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'COMPLETED')
                        """.trimIndent()
                    ).use { ins ->
                        ins.setLong(1, System.currentTimeMillis())
                        ins.setString(2, login)
                        ins.setInt(3, characterId)
                        ins.setString(4, targetCharName ?: "")
                        ins.setString(5, itemKey)
                        ins.setInt(6, itemId)
                        ins.setString(7, friendlyItemName)
                        ins.setLong(8, totalDeliverCount)
                        ins.setLong(9, coinPrice)
                        ins.setInt(10, safeQty)
                        ins.setLong(11, totalCost)
                        ins.setString(12, deliveryMode)
                        ins.executeUpdate()
                    }

                    con.commit()

                    val newBalance = currentBalance - totalCost
                    DonationShopBuyResult(
                        ok = true,
                        message = "Item adquirido com sucesso e entregue ao personagem $targetCharName!",
                        remainingCoins = newBalance,
                        itemId = itemId,
                        itemCount = totalDeliverCount
                    )
                } catch (e: Exception) {
                    con.rollback()
                    DonationShopBuyResult(false, "Erro ao processar a compra: ${e.message}")
                } finally {
                    con.autoCommit = true
                }
            }
        } catch (e: Exception) {
            DonationShopBuyResult(false, "Erro de conexão ao processar a compra.")
        }
    }

    // BCrypt hash for a fixed non-secret password, used only to reduce user-not-found timing leaks.
    private const val DUMMY_BCRYPT_HASH = "${'$'}2a${'$'}10${'$'}uU7ddPxn.YRi/dMrYdMEReaXZWWkVWpW4gJkVhC4hU25Dxj6ZXpK2"
}
