package ext.mods.gameapi

import br.project.spi.donation.DonationService
import com.google.gson.Gson
import ext.mods.commons.logging.CLogger
import ext.mods.gameapi.db.SiteApiRepository
import ext.mods.gameapi.security.HmacVerifier
import ext.mods.gameapi.security.NonceCache
import ext.mods.gameapi.security.RateLimiter
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.util.AttributeKey
import io.netty.util.CharsetUtil
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Internal HTTP API used by the Ktor site. Bind to 127.0.0.1 by default.
 * This API is not a public web API; every non-ping route requires HMAC + nonce.
 */
class GameApiServer private constructor() : AutoCloseable {
    private val bossGroup: EventLoopGroup = NioEventLoopGroup(1)
    private val workerGroup: EventLoopGroup = NioEventLoopGroup()
    private var channel: Channel? = null
    private val limiter = RateLimiter(GameApiConfig.rateLimitPerMinute)
    private var cleanExecutor: ScheduledExecutorService? = null

    fun start() {
        if (!GameApiConfig.enabled) {
            LOGGER.info("[game-api] disabled")
            return
        }
        require(GameApiConfig.port in 1..65535) { "GameApiPort must be 1..65535" }
        require(GameApiConfig.secret.size >= 32) { "GameApiSecret must be base64 and decode to at least 32 bytes" }
        require(isSafeBindHost(GameApiConfig.host)) {
            "Refusing unsafe GameApiHost='${GameApiConfig.host}'. Use 127.0.0.1 for same-host site/game deployment."
        }

        cleanExecutor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "game-api-cleaner").apply { isDaemon = true }
        }
        cleanExecutor?.scheduleAtFixedRate({
            try {
                limiter.gc()
                NonceCache.purgeExpired()
            } catch (_: Exception) {
            }
        }, 60, 60, TimeUnit.SECONDS)

        val bootstrap = ServerBootstrap()
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(ReadTimeoutHandler(30, TimeUnit.SECONDS))
                    ch.pipeline().addLast(HttpServerCodec())
                    ch.pipeline().addLast(HttpObjectAggregator(128 * 1024))
                    ch.pipeline().addLast(GameApiHandler(limiter))
                }
            })

        channel = bootstrap.bind(GameApiConfig.host, GameApiConfig.port).sync().channel()
        LOGGER.info("[game-api] listening on http://{}:{}", GameApiConfig.host, GameApiConfig.port)

        // Initialize WebSocket push notifier for real-time clan chat
        ext.mods.gameapi.clan.SitePushNotifier.init(GameApiConfig.siteWsPushUrl, GameApiConfig.secret)
    }

    override fun close() {
        try {
            channel?.close()?.syncUninterruptibly()
        } catch (_: Exception) {
        }
        cleanExecutor?.shutdownNow()
        cleanExecutor = null
        ext.mods.gameapi.clan.SitePushNotifier.shutdown()
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
        NonceCache.clear()
        LOGGER.info("[game-api] stopped")
    }

    private class GameApiHandler(private val limiter: RateLimiter) : SimpleChannelInboundHandler<FullHttpRequest>() {
        private val ATTR_KEEP_ALIVE = AttributeKey.valueOf<Boolean>("game_api_keep_alive")

        override fun channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest) {
            val keepAlive = HttpUtil.isKeepAlive(req)
            ctx.channel().attr(ATTR_KEEP_ALIVE).set(keepAlive)

            val ip = remoteIp(ctx)
            if (!limiter.tryAcquire(ip)) {
                return respond(ctx, HttpResponseStatus.TOO_MANY_REQUESTS, mapOf("ok" to false, "message" to "rate limit"))
            }

            val path = req.uri().substringBefore('?')
            if (req.method() == HttpMethod.GET && path == "/internal/site/ping") {
                return respond(ctx, HttpResponseStatus.OK, mapOf("ok" to true, "message" to "pong"))
            }

            val bodyBytes = ByteArray(req.content().readableBytes())
            req.content().getBytes(0, bodyBytes)

            if (!verify(req, path, bodyBytes)) {
                return respond(ctx, HttpResponseStatus.UNAUTHORIZED, mapOf("ok" to false, "message" to "unauthorized"))
            }

            try {
                when {
                    req.method() == HttpMethod.GET && path.startsWith("/internal/site/rankings/") -> {
                        val type = path.substringAfterLast('/').lowercase()
                        if (type !in setOf("pvp", "pk", "clan")) {
                            respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Ranking inválido"))
                        } else {
                            respond(ctx, HttpResponseStatus.OK, SiteApiRepository.ranking(type))
                        }
                    }
                    req.method() == HttpMethod.POST && path == "/internal/site/register" -> handleRegister(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/login" -> handleLogin(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/characters" -> handleAccountCharacters(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/rename" -> handleAccountRename(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/rename" -> handleAccountClanRename(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/validate" -> handleAccountClanValidate(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/rename-ally" -> handleAccountClanRenameAlly(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/rename-ally/info" -> handleAccountClanAllyInfo(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/level-up" -> handleAccountClanLevelUp(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/level-down" -> handleAccountClanLevelDown(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/transfer-leadership" -> handleAccountClanTransferLeadership(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/members" -> handleAccountClanMembers(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/ban-member" -> handleAccountClanBanMember(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/royal-guard" -> handleAccountClanRoyalGuard(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/castle-siege" -> handleAccountClanCastleSiege(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/royal-guard/move-member" -> handleAccountClanRoyalGuardMove(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/royal-guard/create" -> handleAccountClanRoyalGuardCreate(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/royal-guard/rename" -> handleAccountClanRoyalGuardRename(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/royal-guard/delete" -> handleAccountClanRoyalGuardDelete(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/royal-guard/assign-captain" -> handleAccountClanRoyalGuardAssignCaptain(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/royal-guard/remove-captain" -> handleAccountClanRoyalGuardRemoveCaptain(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/invite/candidates" -> handleAccountClanInviteCandidates(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/invite/send" -> handleAccountClanInviteSend(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/skills/list" -> handleAccountClanSkillsList(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/skills/buy" -> handleAccountClanSkillsBuy(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/skills/donate-reputation" -> handleAccountClanSkillsDonateReputation(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/chat" -> handleAccountClanChat(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/chat/send" -> handleAccountClanChatSend(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/castle-siege/register" -> handleAccountClanSiegeRegister(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/wars" -> handleAccountClanWars(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/wars/declare" -> handleAccountClanWarDeclare(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/wars/stop" -> handleAccountClanWarStop(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/player-reset" -> handleAccountPlayerReset(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/pk-reset" -> handleAccountPkReset(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/change-password" -> handleAccountChangePassword(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/reset-password" -> handleAccountResetPassword(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/hardware/get" -> handleAccountHardwareGet(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/hardware/save" -> handleAccountHardwareSave(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/hardware/remove" -> handleAccountHardwareRemove(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/hardware/update-count" -> handleAccountHardwareUpdateCount(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/vote/status" -> handleVoteStatus(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/vote/intent" -> handleVoteIntent(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/vote/intent/status" -> handleVoteIntentStatus(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/vote/deliver" -> handleVoteDeliver(ctx, bodyBytes)
                    req.method() == HttpMethod.GET && path == "/internal/site/donation/config" -> handleDonationConfig(ctx)
                    req.method() == HttpMethod.POST && path == "/internal/site/donation/create" -> handleDonationCreate(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/donation/status" -> handleDonationStatus(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/donation/history" -> handleDonationHistory(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/donation/shop/buy" -> handleDonationShopBuy(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path in setOf("/internal/site/donation", "/internal/site/shop") -> {
                        respond(ctx, HttpResponseStatus.ACCEPTED, mapOf("ok" to false, "message" to "Endpoint reservado; utilize /internal/site/donation/create"))
                    }
                    else -> respond(ctx, HttpResponseStatus.NOT_FOUND, mapOf("ok" to false, "message" to "not found"))
                }
            } catch (e: Exception) {
                LOGGER.error("[game-api] request failed {}", e, path)
                respond(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, mapOf("ok" to false, "message" to "Erro interno"))
            }
        }

        private fun handleRegister(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), RegisterPayload::class.java)
            val login = req.login
            val plainPassword = req.password
            val error = validateCredentials(login, plainPassword)
            if (error != null) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to error))
            }
            val password = plainPassword!!.toCharArray()
            try {
                when (SiteApiRepository.register(login!!, password)) {
                    SiteApiRepository.RegisterResult.CREATED -> respond(ctx, HttpResponseStatus.OK, mapOf("ok" to true, "message" to "Conta criada"))
                    SiteApiRepository.RegisterResult.DUPLICATE -> respond(ctx, HttpResponseStatus.CONFLICT, mapOf("ok" to false, "message" to "Login já existe"))
                    SiteApiRepository.RegisterResult.ERROR -> respond(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, mapOf("ok" to false, "message" to "Erro interno"))
                }
            } finally {
                password.fill('\u0000')
            }
        }

        private fun handleLogin(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), LoginPayload::class.java)
            val login = req.login
            val plainPassword = req.password
            val error = validateCredentials(login, plainPassword)
            if (error != null) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to error))
            }
            val password = plainPassword!!.toCharArray()
            try {
                val result = SiteApiRepository.login(login!!, password)
                if (!result.ok) {
                    respond(ctx, HttpResponseStatus.UNAUTHORIZED, result)
                } else {
                    respond(ctx, HttpResponseStatus.OK, result)
                }
            } finally {
                password.fill('\u0000')
            }
        }

        private fun handleAccountCharacters(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), AccountCharactersPayload::class.java)
            val login = req.login
            if (login == null || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            val result = SiteApiRepository.accountCharacters(login)
            respond(ctx, if (result.ok) HttpResponseStatus.OK else HttpResponseStatus.INTERNAL_SERVER_ERROR, result)
        }

        private fun handleAccountRename(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), RenameCharacterPayload::class.java)
            val login = req.login
            val charId = req.characterId
            val newName = req.newName?.trim()
            if (login == null || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            if (charId == null || charId <= 0) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            }
            if (newName.isNullOrEmpty()) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Informe um novo nome"))
            }
            val result = SiteApiRepository.renameCharacter(login, charId, newName)
            val status = when {
                result.ok -> HttpResponseStatus.OK
                result.message.contains("inexistente", true) || result.message.contains("inválido", true) -> HttpResponseStatus.BAD_REQUEST
                result.message.contains("offline", true) -> HttpResponseStatus.CONFLICT
                result.message.contains("em uso", true) -> HttpResponseStatus.CONFLICT
                result.message.contains("possui", true) || result.message.contains("item", true) && !result.ok -> HttpResponseStatus.PAYMENT_REQUIRED
                else -> HttpResponseStatus.INTERNAL_SERVER_ERROR
            }
            respond(ctx, status, result)
        }

        private fun handleAccountClanRename(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), RenameClanPayload::class.java)
            val login = req.login
            val charId = req.characterId
            val newName = req.newClanName?.trim()
            if (login == null || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            if (charId == null || charId <= 0) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            }
            if (newName.isNullOrEmpty()) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Informe um novo nome de clan"))
            }
            val result = SiteApiRepository.renameClan(login, charId, newName)
            val status = when {
                result.ok -> HttpResponseStatus.OK
                result.message.contains("inválido", true) -> HttpResponseStatus.BAD_REQUEST
                result.message.contains("offline", true) ||
                    result.message.contains("liderança", true) ||
                    result.message.contains("líder", true) ||
                    result.message.contains("em uso", true) ||
                    result.message.contains("confirmada", true) ||
                    result.message.contains("dissolução", true) -> HttpResponseStatus.CONFLICT
                result.message.contains("possui", true) || result.message.contains("item", true) && !result.ok -> HttpResponseStatus.PAYMENT_REQUIRED
                else -> HttpResponseStatus.INTERNAL_SERVER_ERROR
            }
            respond(ctx, status, result)
        }



        private fun handleAccountClanRenameAlly(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanServiceWithNamePayload::class.java)
            val login = req.login; val charId = req.characterId; val newName = req.newName?.trim()
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            if (newName.isNullOrEmpty()) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Informe um novo nome de aliança"))
            val result = SiteApiRepository.renameAlly(login, charId, newName)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanAllyInfo(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanServiceBasePayload::class.java)
            val login = req.login; val charId = req.characterId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            val result = SiteApiRepository.getClanAllianceInfo(login, charId)
            respond(ctx, if (result.ok) HttpResponseStatus.OK else HttpResponseStatus.BAD_REQUEST, result)
        }

        private fun handleAccountClanLevelUp(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanServiceBasePayload::class.java)
            val login = req.login; val charId = req.characterId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            val result = SiteApiRepository.levelUpClan(login, charId)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanLevelDown(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanServiceBasePayload::class.java)
            val login = req.login; val charId = req.characterId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            val result = SiteApiRepository.levelDownClan(login, charId)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanTransferLeadership(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanServiceWithTargetPayload::class.java)
            val login = req.login; val charId = req.characterId; val targetId = req.targetCharacterId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            if (targetId == null || targetId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Membro alvo inválido"))
            val result = SiteApiRepository.transferLeadership(login, charId, targetId)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanMembers(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanServiceBasePayload::class.java)
            val login = req.login; val charId = req.characterId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            val result = SiteApiRepository.getClanMembersForTransfer(login, charId)
            respond(ctx, if (result.ok) HttpResponseStatus.OK else HttpResponseStatus.BAD_REQUEST, result)
        }

        private fun handleAccountClanBanMember(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanServiceWithTargetPayload::class.java)
            val login = req.login; val charId = req.characterId; val targetId = req.targetCharacterId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            if (targetId == null || targetId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Membro alvo inválido"))
            val result = SiteApiRepository.banMember(login, charId, targetId)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanRoyalGuard(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanServiceBasePayload::class.java)
            val login = req.login; val charId = req.characterId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            val result = SiteApiRepository.listRoyalGuards(login, charId)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanCastleSiege(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanServiceBasePayload::class.java)
            val login = req.login; val charId = req.characterId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            val result = SiteApiRepository.listCastleSiege(login, charId)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanRoyalGuardMove(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), RoyalGuardMovePayload::class.java)
            val login = req.login; val charId = req.characterId; val targetId = req.targetCharacterId; val subPledgeId = req.targetSubPledgeId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            if (targetId == null || targetId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Membro alvo inválido"))
            if (subPledgeId == null) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Ordem destino inválida"))
            val result = SiteApiRepository.moveRoyalGuardMember(login, charId, targetId, subPledgeId)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanRoyalGuardCreate(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), RoyalGuardCreatePayload::class.java)
            val login = req.login; val charId = req.characterId; val name = req.name?.trim()
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            if (name.isNullOrEmpty()) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Nome da Royal inválido"))
            val result = SiteApiRepository.createRoyalGuard(login, charId, name)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanRoyalGuardRename(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), RoyalGuardRenamePayload::class.java)
            val login = req.login; val charId = req.characterId; val subPledgeId = req.subPledgeId; val name = req.name?.trim()
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            if (subPledgeId == null) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Ordem inválida"))
            if (name.isNullOrEmpty()) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Nome da ordem inválido"))
            val result = SiteApiRepository.renameRoyalGuardSubPledge(login, charId, subPledgeId, name)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanRoyalGuardDelete(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), RoyalGuardDeletePayload::class.java)
            val login = req.login; val charId = req.characterId; val subPledgeId = req.subPledgeId; val moveTo = req.moveToSubPledgeId ?: 0
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            if (subPledgeId == null) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Royal inválida"))
            val result = SiteApiRepository.deleteRoyalGuard(login, charId, subPledgeId, moveTo)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanRoyalGuardAssignCaptain(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), RoyalGuardAssignCaptainPayload::class.java)
            val login = req.login; val charId = req.characterId; val subPledgeId = req.subPledgeId; val targetCharId = req.targetCharacterId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            if (subPledgeId == null) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Ordem inválida"))
            if (targetCharId == null || targetCharId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Membro capitão inválido"))
            val result = SiteApiRepository.assignRoyalGuardCaptain(login, charId, subPledgeId, targetCharId)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanRoyalGuardRemoveCaptain(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), RoyalGuardRemoveCaptainPayload::class.java)
            val login = req.login; val charId = req.characterId; val subPledgeId = req.subPledgeId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            if (subPledgeId == null) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Ordem inválida"))
            val result = SiteApiRepository.removeRoyalGuardCaptain(login, charId, subPledgeId)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanInviteCandidates(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanInviteCandidatesPayload::class.java)
            val login = req.login; val charId = req.characterId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            val result = SiteApiRepository.getClanInviteCandidates(login, charId)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanInviteSend(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanInviteSendPayload::class.java)
            val login = req.login; val charId = req.characterId; val targetCharId = req.targetCharacterId; val subPledgeId = req.targetSubPledgeId ?: 0
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            if (targetCharId == null || targetCharId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Jogador convidado inválido"))
            val result = SiteApiRepository.sendClanInvite(login, charId, targetCharId, subPledgeId)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanSkillsList(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanServiceBasePayload::class.java)
            val login = req.login; val charId = req.characterId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            val result = SiteApiRepository.getClanSkillsServiceData(login, charId)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanSkillsBuy(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanSkillBuyPayload::class.java)
            val login = req.login; val charId = req.characterId; val skillId = req.skillId
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            if (skillId == null || skillId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Habilidade inválida"))
            val result = SiteApiRepository.buyClanSkillService(login, charId, skillId)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanSkillsDonateReputation(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanSkillDonatePayload::class.java)
            val login = req.login; val charId = req.characterId; val count = req.count ?: 1
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            val result = SiteApiRepository.donateClanReputation(login, charId, count)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanChat(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanChatPayload::class.java)
            val login = req.login; val charId = req.characterId; val since = req.since ?: 0L
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            val result = SiteApiRepository.clanChat(login, charId, since)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanChatSend(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanChatSendPayload::class.java)
            val login = req.login; val charId = req.characterId; val text = req.text ?: ""
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            val result = SiteApiRepository.sendClanChat(login, charId, text)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }

        private fun handleAccountClanSiegeRegister(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), SiegeRegisterPayload::class.java)
            val login = req.login; val charId = req.characterId; val castleId = req.castleId; val type = req.type
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            if (castleId == null || castleId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Castelo inválido"))
            if (type.isNullOrEmpty()) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Tipo de registro inválido"))
            val result = SiteApiRepository.registerSiege(login, charId, castleId, type)
            respond(ctx, if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT, result)
        }


        private fun handleAccountClanValidate(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanServiceValidationPayload::class.java)
            val login = req.login
            val charId = req.characterId
            val serviceId = req.serviceId?.trim()?.lowercase()
            if (login == null || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            if (charId == null || charId <= 0) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            }
            if (serviceId.isNullOrEmpty()) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Serviço não especificado"))
            }
            val result = SiteApiRepository.validateClanService(login, charId, serviceId)
            respond(ctx, HttpResponseStatus.OK, result)
        }

        private fun handleAccountPkReset(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), PkResetPayload::class.java)
            val login = req.login
            val charId = req.characterId
            if (login == null || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            if (charId == null || charId <= 0) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            }
            val result = SiteApiRepository.resetPkAndKarma(login, charId)
            val status = when {
                result.ok -> HttpResponseStatus.OK
                result.message.contains("inválido", true) -> HttpResponseStatus.BAD_REQUEST
                result.message.contains("offline", true) -> HttpResponseStatus.CONFLICT
                result.message.contains("possui", true) || result.message.contains("item", true) && !result.ok -> HttpResponseStatus.PAYMENT_REQUIRED
                else -> HttpResponseStatus.INTERNAL_SERVER_ERROR
            }
            respond(ctx, status, result)
        }

        private fun handleAccountPlayerReset(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), PlayerResetPayload::class.java)
            val login = req.login
            val charId = req.characterId
            if (login == null || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            if (charId == null || charId <= 0) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            }
            val result = SiteApiRepository.resetPlayerLocation(login, charId)
            val status = when {
                result.ok -> HttpResponseStatus.OK
                result.message.contains("inválido", true) -> HttpResponseStatus.BAD_REQUEST
                result.message.contains("conectado", true) ||
                    result.message.contains("offline", true) ||
                    result.message.contains("zona de paz", true) ||
                    result.message.contains("combate", true) -> HttpResponseStatus.CONFLICT
                result.message.contains("possui", true) || result.message.contains("item", true) && !result.ok -> HttpResponseStatus.PAYMENT_REQUIRED
                else -> HttpResponseStatus.INTERNAL_SERVER_ERROR
            }
            respond(ctx, status, result)
        }

        private fun handleAccountHardwareGet(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), HardwareGetPayload::class.java)
            val login = req.login
            if (login == null || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            val creds = SiteApiRepository.getHardwareCredentials(login)
            respond(ctx, HttpResponseStatus.OK, mapOf("ok" to true, "credentials" to creds))
        }

        private fun handleAccountHardwareSave(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), HardwareSavePayload::class.java)
            val login = req.login
            if (login == null || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            val credId = req.credentialId
            val pubKey = req.publicKeyDer
            if (credId.isNullOrBlank() || pubKey.isNullOrBlank()) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Dados de credencial inválidos"))
            }
            val ok = SiteApiRepository.saveHardwareCredential(
                login = login,
                credentialId = credId,
                publicKeyDer = pubKey,
                algorithm = req.algorithm ?: -7,
                deviceName = req.deviceName ?: "Windows Hello (TPM 2.0)",
                signCount = req.signCount ?: 0L
            )
            respond(ctx, if (ok) HttpResponseStatus.OK else HttpResponseStatus.INTERNAL_SERVER_ERROR, mapOf("ok" to ok))
        }

        private fun handleAccountHardwareRemove(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), HardwareRemovePayload::class.java)
            val login = req.login
            if (login == null || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            val ok = SiteApiRepository.removeHardwareCredentials(login)
            respond(ctx, if (ok) HttpResponseStatus.OK else HttpResponseStatus.INTERNAL_SERVER_ERROR, mapOf("ok" to ok))
        }

        private fun handleAccountHardwareUpdateCount(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), HardwareUpdateCountPayload::class.java)
            val login = req.login
            if (login == null || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            val credId = req.credentialId
            val signCount = req.signCount
            if (credId.isNullOrBlank() || signCount == null) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Dados inválidos"))
            }
            val ok = SiteApiRepository.updateHardwareSignCount(login, credId, signCount)
            respond(ctx, if (ok) HttpResponseStatus.OK else HttpResponseStatus.INTERNAL_SERVER_ERROR, mapOf("ok" to ok))
        }

        private fun verify(req: FullHttpRequest, path: String, bodyBytes: ByteArray): Boolean {
            val timestamp = req.headers().get("X-Site-Timestamp")?.toLongOrNull() ?: return false
            val nonce = req.headers().get("X-Site-Nonce") ?: return false
            val signature = req.headers().get("X-Site-Signature") ?: return false
            return HmacVerifier.verify(req.method().name(), path, timestamp, nonce, bodyBytes, signature)
        }

        private fun validateCredentials(login: String?, password: String?): String? {
            if (login == null || !LOGIN_REGEX.matches(login)) return "Login inválido"
            if (password == null || password.length !in 8..64) return "Senha inválida"
            return null
        }

        private fun respond(ctx: ChannelHandlerContext, status: HttpResponseStatus, payload: Any) {
            val json = GSON.toJson(payload)
            val content = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8)
            val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content)
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8")
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
            val keepAlive = ctx.channel().attr(ATTR_KEEP_ALIVE).get() ?: false
            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                ctx.writeAndFlush(response)
            } else {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                ctx.writeAndFlush(response).addListener { ctx.close() }
            }
        }

        private fun remoteIp(ctx: ChannelHandlerContext): String =
            ((ctx.channel().remoteAddress() as? InetSocketAddress)?.address?.hostAddress) ?: "unknown"

        private data class RegisterPayload(val login: String? = null, val password: String? = null)
        private data class LoginPayload(val login: String? = null, val password: String? = null)
        private data class AccountCharactersPayload(val login: String? = null)
        private data class RenameCharacterPayload(val login: String? = null, val characterId: Int? = null, val newName: String? = null)
        private data class RenameClanPayload(val login: String? = null, val characterId: Int? = null, val newClanName: String? = null)
        private data class ClanServiceValidationPayload(val login: String? = null, val characterId: Int? = null, val serviceId: String? = null)
        private data class ClanServiceBasePayload(val login: String? = null, val characterId: Int? = null)
        private data class ClanServiceWithNamePayload(val login: String? = null, val characterId: Int? = null, val newName: String? = null)
        private data class ClanServiceWithTargetPayload(val login: String? = null, val characterId: Int? = null, val targetCharacterId: Int? = null)
        private data class RoyalGuardMovePayload(val login: String? = null, val characterId: Int? = null, val targetCharacterId: Int? = null, val targetSubPledgeId: Int? = null)
        private data class RoyalGuardCreatePayload(val login: String? = null, val characterId: Int? = null, val name: String? = null)
        private data class RoyalGuardRenamePayload(val login: String? = null, val characterId: Int? = null, val subPledgeId: Int? = null, val name: String? = null)
        private data class RoyalGuardDeletePayload(val login: String? = null, val characterId: Int? = null, val subPledgeId: Int? = null, val moveToSubPledgeId: Int? = null)
        private data class RoyalGuardAssignCaptainPayload(val login: String? = null, val characterId: Int? = null, val subPledgeId: Int? = null, val targetCharacterId: Int? = null)
        private data class RoyalGuardRemoveCaptainPayload(val login: String? = null, val characterId: Int? = null, val subPledgeId: Int? = null)
        private data class ClanInviteCandidatesPayload(val login: String? = null, val characterId: Int? = null)
        private data class ClanInviteSendPayload(val login: String? = null, val characterId: Int? = null, val targetCharacterId: Int? = null, val targetSubPledgeId: Int? = null)
        private data class ClanSkillBuyPayload(val login: String? = null, val characterId: Int? = null, val skillId: Int? = null)
        private data class ClanSkillDonatePayload(val login: String? = null, val characterId: Int? = null, val count: Int? = null)
        private data class ClanChatPayload(val login: String? = null, val characterId: Int? = null, val since: Long? = null)
        private data class ClanChatSendPayload(val login: String? = null, val characterId: Int? = null, val text: String? = null)
        private data class SiegeRegisterPayload(val login: String? = null, val characterId: Int? = null, val castleId: Int? = null, val type: String? = null)
        private data class ClanWarsPayload(val login: String? = null, val characterId: Int? = null)
        private data class ClanWarDeclarePayload(val login: String? = null, val characterId: Int? = null, val targetClanName: String? = null)
        private data class ClanWarStopPayload(val login: String? = null, val characterId: Int? = null, val targetClanId: Int? = null)
        private data class PkResetPayload(val login: String? = null, val characterId: Int? = null)
        private data class PlayerResetPayload(val login: String? = null, val characterId: Int? = null)
        private data class ChangePasswordPayload(val login: String? = null, val currentPassword: String? = null, val newPassword: String? = null)
        private data class ResetPasswordPayload(val login: String? = null, val newPassword: String? = null)
        private data class HardwareGetPayload(val login: String? = null)
        private data class HardwareSavePayload(
            val login: String? = null,
            val credentialId: String? = null,
            val publicKeyDer: String? = null,
            val algorithm: Int? = -7,
            val deviceName: String? = "Windows Hello (TPM 2.0)",
            val signCount: Long? = 0L
        )
        private data class HardwareRemovePayload(val login: String? = null)
        private data class HardwareUpdateCountPayload(
            val login: String? = null,
            val credentialId: String? = null,
            val signCount: Long? = 0L
        )
        private data class VoteStatusPayload(val login: String? = null, val ip: String? = null)
        private data class VoteIntentPayload(val login: String? = null, val characterId: Int? = null, val characterName: String? = null, val ip: String? = null, val eventType: String? = null)
        private data class VoteIntentStatusPayload(val token: String? = null)
        private data class VoteDeliverPayload(
            val deliveryId: String? = null,
            val player: String? = null,
            val ip: String? = null,
            val event: String? = null,
            val rating: Int? = null,
            val quantity: Int? = null,
            val server: String? = null
        )
        private data class DonationCreatePayload(
            val login: String? = null,
            val characterId: Int? = null,
            val email: String? = null,
            val count: Int? = null,
            val clientIp: String? = null
        )
        private data class DonationStatusPayload(
            val login: String? = null,
            val purchaseId: Int? = null
        )
        private data class DonationHistoryPayload(
            val login: String? = null
        )
        private data class DonationShopBuyPayload(
            val login: String? = null,
            val characterId: Int? = null,
            val itemKey: String? = null,
            val itemId: Int? = null,
            val itemCount: Long? = null,
            val coinPrice: Long? = null,
            val quantity: Int? = null,
            val clientIp: String? = null,
            val idempotencyKey: String? = null
        )

        private fun handleAccountClanWars(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanWarsPayload::class.java)
            val login = req.login
            val charId = req.characterId
            if (login == null || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            if (charId == null || charId <= 0) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            }
            val result = SiteApiRepository.getClanWars(login, charId)
            val ok = result["ok"] as? Boolean ?: false
            respond(ctx, if (ok) HttpResponseStatus.OK else HttpResponseStatus.BAD_REQUEST, result)
        }

        private fun handleAccountClanWarDeclare(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanWarDeclarePayload::class.java)
            val login = req.login
            val charId = req.characterId
            val targetClanName = req.targetClanName?.trim()
            if (login == null || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            if (charId == null || charId <= 0) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            }
            if (targetClanName.isNullOrEmpty()) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Informe o nome do clan alvo"))
            }
            val result = SiteApiRepository.declareClanWar(login, charId, targetClanName)
            val ok = result["ok"] as? Boolean ?: false
            respond(ctx, if (ok) HttpResponseStatus.OK else HttpResponseStatus.BAD_REQUEST, result)
        }

        private fun handleAccountClanWarStop(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ClanWarStopPayload::class.java)
            val login = req.login
            val charId = req.characterId
            val targetClanId = req.targetClanId
            if (login == null || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            if (charId == null || charId <= 0) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            }
            if (targetClanId == null || targetClanId <= 0) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Clan alvo inválido"))
            }
            val result = SiteApiRepository.stopClanWar(login, charId, targetClanId)
            val ok = result["ok"] as? Boolean ?: false
            respond(ctx, if (ok) HttpResponseStatus.OK else HttpResponseStatus.BAD_REQUEST, result)
        }

        private fun handleAccountChangePassword(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ChangePasswordPayload::class.java)
            val login = req.login
            val currentPassword = req.currentPassword
            val newPassword = req.newPassword
            val err1 = validateCredentials(login, currentPassword)
            if (err1 != null) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Senha atual inválida"))
            }
            val err2 = validateCredentials(login, newPassword)
            if (err2 != null) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Nova senha inválida"))
            }
            if (currentPassword == newPassword) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "A nova senha deve ser diferente da senha atual"))
            }
            val currentChars = currentPassword!!.toCharArray()
            val newChars = newPassword!!.toCharArray()
            try {
                val result = SiteApiRepository.changePassword(login!!, currentChars, newChars)
                val status = if (result.ok) HttpResponseStatus.OK else HttpResponseStatus.BAD_REQUEST
                respond(ctx, status, result)
            } finally {
                currentChars.fill('\u0000')
                newChars.fill('\u0000')
            }
        }

        private fun handleAccountResetPassword(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), ResetPasswordPayload::class.java)
            val login = req.login
            val newPassword = req.newPassword
            val err = validateCredentials(login, newPassword)
            if (err != null) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Nova senha inválida"))
            }
            val newChars = newPassword!!.toCharArray()
            try {
                val result = SiteApiRepository.resetPassword(login!!, newChars)
                val status = if (result.ok) HttpResponseStatus.OK else HttpResponseStatus.BAD_REQUEST
                respond(ctx, status, result)
            } finally {
                newChars.fill('\u0000')
            }
        }

        private fun handleVoteStatus(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), VoteStatusPayload::class.java)
            val result = SiteApiRepository.getVoteStatus(req.login, req.ip ?: "")
            respond(ctx, HttpResponseStatus.OK, result)
        }

        private fun handleVoteIntent(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), VoteIntentPayload::class.java)
            val result = SiteApiRepository.registerVoteIntent(
                req.login,
                req.characterId ?: 0,
                req.characterName ?: "",
                req.ip ?: "",
                req.eventType ?: "vote"
            )
            val ok = result["ok"] as? Boolean ?: false
            respond(ctx, if (ok) HttpResponseStatus.OK else HttpResponseStatus.BAD_REQUEST, result)
        }

        private fun handleVoteIntentStatus(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), VoteIntentStatusPayload::class.java)
            val token = req.token ?: ""
            val result = SiteApiRepository.checkVoteIntentStatus(token)
            respond(ctx, HttpResponseStatus.OK, result)
        }

        private fun handleVoteDeliver(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), VoteDeliverPayload::class.java)
            val deliveryId = req.deliveryId
            if (deliveryId.isNullOrBlank()) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "deliveryId obrigatório"))
            }
            val result = SiteApiRepository.deliverVoteReward(
                deliveryId,
                req.player,
                req.ip ?: "",
                req.event ?: "vote",
                req.rating,
                req.quantity ?: 1,
                req.server
            )
            val status = if (result.ok) HttpResponseStatus.OK else HttpResponseStatus.BAD_REQUEST
            respond(ctx, status, result)
        }

        private fun handleDonationConfig(ctx: ChannelHandlerContext) {
            try {
                val service = DonationService.Provider.get()
                val config = service.config
                respond(ctx, HttpResponseStatus.OK, config)
            } catch (e: Exception) {
                LOGGER.error("[game-api] handleDonationConfig failed", e)
                respond(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, mapOf("ok" to false, "message" to "Erro interno ao obter configurações de doação"))
            }
        }

        private fun handleDonationCreate(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val bodyStr = String(bodyBytes, Charsets.UTF_8)
            val req = try {
                GSON.fromJson(bodyStr, DonationCreatePayload::class.java)
            } catch (e: Exception) {
                LOGGER.warn("[game-api] handleDonationCreate: JSON inválido: {}", bodyStr)
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Payload JSON inválido"))
            }

            val login = req?.login
            val charId = req?.characterId
            val email = req?.email?.trim()
            val count = req?.count ?: 0
            val clientIp = req?.clientIp ?: remoteIp(ctx)

            if (login.isNullOrBlank() || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            if (charId == null || charId <= 0) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            }
            if (email.isNullOrEmpty() || !email.contains("@")) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Email inválido"))
            }
            if (count <= 0) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Quantidade deve ser maior que zero"))
            }

            if (!SiteApiRepository.ownsCharacter(login, charId)) {
                return respond(ctx, HttpResponseStatus.FORBIDDEN, mapOf("ok" to false, "message" to "O personagem não pertence a esta conta"))
            }

            val service = DonationService.Provider.get()
            if (!service.isEnabled) {
                return respond(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, mapOf("ok" to false, "message" to "O sistema de doação está desativado no momento"))
            }

            try {
                val result = service.createPixPurchase(charId, login, email, count, clientIp)
                val status = if (result.ok()) HttpResponseStatus.OK else HttpResponseStatus.BAD_REQUEST
                respond(ctx, status, result)
            } catch (e: Exception) {
                LOGGER.error("[game-api] handleDonationCreate failed for login={}", e, login)
                respond(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, mapOf("ok" to false, "message" to "Erro ao gerar cobrança PIX"))
            }
        }

        private fun handleDonationStatus(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val bodyStr = String(bodyBytes, Charsets.UTF_8)
            val req = try {
                GSON.fromJson(bodyStr, DonationStatusPayload::class.java)
            } catch (e: Exception) {
                LOGGER.warn("[game-api] handleDonationStatus: JSON inválido: {}", bodyStr)
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Payload JSON inválido"))
            }

            val login = req?.login
            val purchaseId = req?.purchaseId

            if (login.isNullOrBlank() || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            if (purchaseId == null || purchaseId <= 0) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "ID de compra inválido"))
            }

            try {
                val service = DonationService.Provider.get()
                val status = service.getPurchaseStatus(purchaseId, true)
                if (!status.ok()) {
                    return respond(ctx, HttpResponseStatus.NOT_FOUND, status)
                }
                respond(ctx, HttpResponseStatus.OK, status)
            } catch (e: Exception) {
                LOGGER.error("[game-api] handleDonationStatus failed for purchaseId={}", e, purchaseId)
                respond(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, mapOf("ok" to false, "message" to "Erro interno ao consultar status da doação"))
            }
        }

        private fun handleDonationHistory(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val bodyStr = String(bodyBytes, Charsets.UTF_8)
            val req = try {
                GSON.fromJson(bodyStr, DonationHistoryPayload::class.java)
            } catch (e: Exception) {
                LOGGER.warn("[game-api] handleDonationHistory: JSON inválido: {}", bodyStr)
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Payload JSON inválido"))
            }

            val login = req?.login
            if (login.isNullOrBlank() || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }

            try {
                val service = DonationService.Provider.get()
                val purchases = service.getPurchasesForAccount(login) ?: emptyList<Any>()
                val shopPurchases = SiteApiRepository.getShopPurchasesForAccount(login)
                respond(ctx, HttpResponseStatus.OK, mapOf(
                    "ok" to true,
                    "purchases" to purchases,
                    "shopPurchases" to shopPurchases
                ))
            } catch (e: Exception) {
                LOGGER.error("[game-api] handleDonationHistory failed for login={}", e, login)
                respond(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, mapOf("ok" to false, "message" to "Erro interno ao consultar histórico"))
            }
        }

        private fun handleDonationShopBuy(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val bodyStr = String(bodyBytes, Charsets.UTF_8)
            val req = try {
                GSON.fromJson(bodyStr, DonationShopBuyPayload::class.java)
            } catch (e: Exception) {
                LOGGER.warn("[game-api] handleDonationShopBuy: JSON inválido: {}", bodyStr)
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Payload JSON inválido"))
            }

            val login = req?.login
            val charId = req?.characterId ?: 0
            val itemId = req?.itemId ?: 0
            val itemCount = req?.itemCount ?: 0L
            val coinPrice = req?.coinPrice ?: 0L
            val quantity = req?.quantity ?: 1

            if (login.isNullOrBlank() || !LOGIN_REGEX.matches(login)) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            }
            if (charId <= 0 || itemId <= 0 || itemCount <= 0L || coinPrice <= 0L) {
                return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Parâmetros de compra inválidos"))
            }

            val result = SiteApiRepository.buyDonationShopItem(
                login = login,
                characterId = charId,
                itemKey = req.itemKey ?: "item-$itemId",
                itemId = itemId,
                itemCount = itemCount,
                coinPrice = coinPrice,
                quantity = quantity,
                clientIp = req.clientIp ?: "",
                idempotencyKey = req.idempotencyKey ?: "",
            )

            val status = if (result.ok) HttpResponseStatus.OK else HttpResponseStatus.BAD_REQUEST
            respond(ctx, status, result)
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            LOGGER.debug("[game-api] channel exception: {}", cause.message)
            ctx.close()
        }
    }

    companion object {
        private val LOGGER = CLogger(GameApiServer::class.java.name)
        private val GSON = Gson()
        private val LOGIN_REGEX = Regex("^[A-Za-z0-9_]{3,45}${'$'}")
        @Volatile private var instance: GameApiServer? = null

        @JvmStatic
        fun startFromGameServer() {
            GameApiConfig.load()
            if (!GameApiConfig.enabled) return
            val srv = GameApiServer()
            srv.start()
            instance = srv
        }

        @JvmStatic
        fun stopFromGameServer() {
            instance?.close()
            instance = null
        }

        private fun isSafeBindHost(host: String): Boolean =
            host == "127.0.0.1" || host == "localhost" || host == "::1"
    }
}
