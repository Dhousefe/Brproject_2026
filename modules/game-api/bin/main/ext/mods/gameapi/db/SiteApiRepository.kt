package ext.mods.gameapi.db

import ext.mods.commons.crypt.BCrypt
import ext.mods.commons.jdbc.DatabaseDialect
import ext.mods.commons.pool.ConnectionPool
import ext.mods.gameapi.GameApiConfig
import java.sql.Connection
import java.util.ArrayDeque
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

    fun ranking(type: String, limit: Int = 20): Any = when (type) {
        "pvp" -> playerRanking("pvpkills", limit)
        "pk" -> playerRanking("pkkills", limit)
        "clan" -> clanRanking(limit)
        else -> emptyList<PlayerRankEntry>()
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

                val members = mutableListOf<Map<String, Any?>>()
                con.prepareStatement(
                    "SELECT obj_Id, char_name, COALESCE(subpledge, 0) AS subpledge, COALESCE(power_grade, 0) AS power_grade FROM characters WHERE clanid=? AND COALESCE(deletetime, 0)=0 ORDER BY subpledge ASC, char_name ASC"
                ).use { ps ->
                    ps.setInt(1, current.clanId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            members.add(mapOf(
                                "characterId" to rs.getInt("obj_Id"),
                                "characterName" to (rs.getString("char_name") ?: ""),
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
                val row = con.prepareStatement(
                    "SELECT c.obj_Id, c.clanid, cd.clan_name FROM characters c LEFT JOIN clan_data cd ON cd.clan_id = c.clanid WHERE c.obj_Id=? AND c.account_name=? AND COALESCE(c.deletetime, 0)=0 LIMIT 1"
                ).use { ps ->
                    ps.setInt(1, characterId)
                    ps.setString(2, login)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return@use mapOf("ok" to false, "message" to "Personagem inválido para esta conta.", "characterId" to characterId)
                        Triple(rs.getInt("obj_Id"), rs.getInt("clanid"), rs.getString("clan_name") ?: "")
                    }
                } as? Triple<Int, Int, String> ?: return@use mapOf("ok" to false, "message" to "Personagem inválido.", "characterId" to characterId)

                val clanId = row.second
                val castles = mutableListOf<Map<String, Any?>>()
                con.prepareStatement("SELECT id, currentTaxPercent, treasury, siegeDate, regTimeOver FROM castle ORDER BY id ASC").use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            castles.add(mapOf(
                                "castleId" to rs.getInt("id"),
                                "currentTaxPercent" to rs.getInt("currentTaxPercent"),
                                "treasury" to rs.getLong("treasury"),
                                "siegeDate" to rs.getLong("siegeDate"),
                                "regTimeOver" to rs.getLong("regTimeOver")
                            ))
                        }
                    }
                }

                val registrations = mutableListOf<Map<String, Any?>>()
                if (clanId > 0) {
                    con.prepareStatement("SELECT castle_id, type FROM siege_clans WHERE clan_id=? ORDER BY castle_id ASC").use { ps ->
                        ps.setInt(1, clanId)
                        ps.executeQuery().use { rs ->
                            while (rs.next()) {
                                registrations.add(mapOf(
                                    "castleId" to rs.getInt("castle_id"),
                                    "type" to rs.getInt("type")
                                ))
                            }
                        }
                    }
                }

                mapOf(
                    "ok" to true,
                    "message" to "OK",
                    "characterId" to characterId,
                    "clanId" to clanId,
                    "clanName" to row.third,
                    "castles" to castles,
                    "registrations" to registrations
                )
            }
        } catch (_: Exception) {
            mapOf("ok" to false, "message" to "Erro interno ao listar castle siege.")
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
                val oldName = con.prepareStatement("SELECT name FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id=? LIMIT 1").use { ps -> ps.setInt(1, current.clanId); ps.setInt(2, subPledgeId); ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) ?: "" else null } } ?: return@use rollback(con, mapOf("ok" to false, "message" to "Royal/ordem não encontrada."))
                if (moveToSubPledgeId != 0) {
                    val destExists = con.prepareStatement("SELECT 1 FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id=? LIMIT 1").use { ps -> ps.setInt(1, current.clanId); ps.setInt(2, moveToSubPledgeId); ps.executeQuery().use { it.next() } }
                    if (!destExists) return@use rollback(con, mapOf("ok" to false, "message" to "Ordem destino não existe neste clan."))
                }
                val moved = con.prepareStatement("UPDATE characters SET subpledge=? WHERE clanid=? AND COALESCE(subpledge,0)=?").use { ps -> ps.setInt(1, moveToSubPledgeId); ps.setInt(2, current.clanId); ps.setInt(3, subPledgeId); ps.executeUpdate() }
                con.prepareStatement("DELETE FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id=?").use { ps -> ps.setInt(1, current.clanId); ps.setInt(2, subPledgeId); if (ps.executeUpdate() != 1) return@use rollback(con, mapOf("ok" to false, "message" to "Não foi possível deletar a Royal.")) }
                val stillExists = con.prepareStatement("SELECT 1 FROM clan_subpledges WHERE clan_id=? AND sub_pledge_id=? LIMIT 1").use { ps -> ps.setInt(1, current.clanId); ps.setInt(2, subPledgeId); ps.executeQuery().use { it.next() } }
                if (stillExists) return@use rollback(con, mapOf("ok" to false, "message" to "Exclusão não confirmada no banco."))
                recordClanAudit(con, login, characterId, current.clanId, "royal-guard-delete", "$subPledgeId:$oldName", "moved=$moved:to=$moveToSubPledgeId")
                con.commit(); refreshClanRuntime(current.clanId)
                mapOf("ok" to true, "message" to "Royal Guard deletada. $moved membro(s) movido(s).", "deletedSubPledgeId" to subPledgeId, "moveToSubPledgeId" to moveToSubPledgeId, "movedMembers" to moved)
            } catch (_: Exception) { runCatching { con.rollback() }; mapOf("ok" to false, "message" to "Erro interno ao deletar Royal Guard.") } finally { runCatching { con.autoCommit = true } }
        } } catch (_: Exception) { mapOf("ok" to false, "message" to "Erro interno ao deletar Royal Guard.") }
    }

    fun clanChat(login: String, characterId: Int, since: Long): Map<String, Any?> {
        return try { ConnectionPool.getConnection().use { con ->
            val member = loadOwnedClanMemberForChat(con, login, characterId) ?: return@use mapOf("ok" to false, "message" to "Personagem inválido ou sem clan para esta conta.")
            val sinceSafe = since.coerceAtLeast(0L)
            val messages = ext.mods.gameapi.clan.ClanChatRing.fetchSince(member.clanId, sinceSafe)
                .map { e -> mapOf("seq" to e.seq, "time" to e.time, "characterId" to e.characterId, "characterName" to e.characterName, "text" to e.text) }
            mapOf("ok" to true, "message" to "OK", "clanId" to member.clanId, "clanName" to member.clanName, "messages" to messages, "latestSeq" to (messages.lastOrNull()?.get("seq") ?: sinceSafe), "allowOffline" to GameApiConfig.clanChatAllowOffline, "cooldownMs" to GameApiConfig.clanChatCooldownMs, "maxPerMinute" to GameApiConfig.clanChatMaxPerMinute)
        } } catch (_: Exception) { mapOf("ok" to false, "message" to "Erro interno ao consultar chat do clan.") }
    }

    fun sendClanChat(login: String, characterId: Int, text: String): Map<String, Any?> {
        val safeText = sanitizeChatText(text)
        if (safeText.isBlank()) return mapOf("ok" to false, "message" to "Mensagem vazia.")
        return try { ConnectionPool.getConnection().use { con ->
            val member = loadOwnedClanMemberForChat(con, login, characterId) ?: return@use mapOf("ok" to false, "message" to "Personagem inválido ou sem clan para esta conta.")
            val entry = ext.mods.gameapi.clan.ClanChatRing.append(member.clanId, member.characterId, member.characterName, safeText)
            broadcastClanChat(member.clanId, member.characterId, member.characterName, safeText)
            mapOf("ok" to true, "message" to "Mensagem enviada.", "seq" to entry.seq, "time" to entry.time, "clanId" to member.clanId, "characterName" to member.characterName, "text" to safeText, "historySize" to ext.mods.gameapi.clan.ClanChatRing.size(member.clanId))
        } } catch (_: Exception) { mapOf("ok" to false, "message" to "Erro interno ao enviar mensagem do clan.") }
    }

    fun registerSiege(login: String, characterId: Int, castleId: Int, type: String): Map<String, Any?> {
        val safeType = type.uppercase().takeIf { it in setOf("ATTACKER", "DEFENDER", "PENDING") } ?: "PENDING"
        return try {
            ConnectionPool.getConnection().use { con ->
                con.autoCommit = false
                try {
                    val current = loadOwnedClanLeaderForRename(con, login, characterId)
                        ?: return@use rollback(con, mapOf("ok" to false, "message" to "Personagem inválido, sem clan ou sem liderança."))
                    if (current.clanId <= 0) return@use rollback(con, mapOf("ok" to false, "message" to "Sem clan."))
                    // Verify castle exists and registration is open
                    val castle = con.prepareStatement("SELECT id, regTimeOver FROM castle WHERE id=?").use { ps ->
                        ps.setInt(1, castleId); ps.executeQuery().use { rs -> if (rs.next()) Pair(rs.getInt(1), rs.getString("regTimeOver") ?: "true") else null }
                    } ?: return@use rollback(con, mapOf("ok" to false, "message" to "Castelo não encontrado."))
                    if (castle.second.equals("true", ignoreCase = true)) {
                        return@use rollback(con, mapOf("ok" to false, "message" to "Período de registro para siege está encerrado neste castelo."))
                    }
                    // Check if already registered
                    val already = con.prepareStatement("SELECT 1 FROM siege_clans WHERE castle_id=? AND clan_id=? LIMIT 1").use { ps ->
                        ps.setInt(1, castleId); ps.setInt(2, current.clanId); ps.executeQuery().use { it.next() }
                    }
                    if (already) {
                        // Update type
                        con.prepareStatement("UPDATE siege_clans SET type=? WHERE castle_id=? AND clan_id=?").use { ps ->
                            ps.setString(1, safeType); ps.setInt(2, castleId); ps.setInt(3, current.clanId); ps.executeUpdate()
                        }
                    } else {
                        con.prepareStatement("INSERT INTO siege_clans (castle_id, clan_id, type) VALUES (?, ?, ?)").use { ps ->
                            ps.setInt(1, castleId); ps.setInt(2, current.clanId); ps.setString(3, safeType); ps.executeUpdate()
                        }
                    }
                    // Confirm
                    val persisted = con.prepareStatement("SELECT type FROM siege_clans WHERE castle_id=? AND clan_id=? LIMIT 1").use { ps ->
                        ps.setInt(1, castleId); ps.setInt(2, current.clanId); ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
                    }
                    if (persisted == null) return@use rollback(con, mapOf("ok" to false, "message" to "Registro não confirmado no banco."))
                    recordClanAudit(con, login, characterId, current.clanId, "castle-siege-register", "castle=$castleId", "type=$safeType")
                    con.commit()
                    mapOf("ok" to true, "message" to "Clan registrado para siege no castelo $castleId como $safeType.", "castleId" to castleId, "type" to safeType, "updated" to already)
                } catch (_: Exception) { runCatching { con.rollback() }; mapOf("ok" to false, "message" to "Erro interno.") }
                finally { runCatching { con.autoCommit = true } }
            }
        } catch (_: Exception) { mapOf("ok" to false, "message" to "Erro interno.") }
    }

        private data class RenameCharacterRecord(val id: Int, val name: String, val online: Boolean)
    private data class PkResetCharacterRecord(val id: Int, val name: String, val online: Boolean, val pkKills: Int, val karma: Int)
    private data class PlayerResetCharacterRecord(val id: Int, val name: String, val online: Boolean, val inPeaceZone: Boolean = false, val inCombat: Boolean = false)
    private data class ClanLeaderRecord(val characterId: Int, val characterName: String, val online: Boolean, val clanId: Int, val clanName: String, val dissolving: Boolean)
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

    private data class ClanChatMemberRecord(val characterId: Int, val characterName: String, val clanId: Int, val clanName: String)

    private fun loadOwnedClanMemberForChat(con: Connection, login: String, characterId: Int): ClanChatMemberRecord? {
        return con.prepareStatement(
            """
            SELECT c.obj_Id, c.char_name, c.clanid, cd.clan_name
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
                if (rs.next()) ClanChatMemberRecord(rs.getInt("obj_Id"), rs.getString("char_name") ?: "", rs.getInt("clanid"), rs.getString("clan_name") ?: "") else null
            }
        }
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

    private fun broadcastClanChat(clanId: Int, characterId: Int, characterName: String, text: String) {
        runCatching {
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
            ).newInstance(characterId, clanSayType, characterName, text)

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

    // BCrypt hash for a fixed non-secret password, used only to reduce user-not-found timing leaks.
    private const val DUMMY_BCRYPT_HASH = "${'$'}2a${'$'}10${'$'}uU7ddPxn.YRi/dMrYdMEReaXZWWkVWpW4gJkVhC4hU25Dxj6ZXpK2"
}
