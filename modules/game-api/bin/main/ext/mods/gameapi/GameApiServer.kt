package ext.mods.gameapi

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
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.util.CharsetUtil
import java.net.InetSocketAddress
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

        val bootstrap = ServerBootstrap()
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(ReadTimeoutHandler(10, TimeUnit.SECONDS))
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
        ext.mods.gameapi.clan.SitePushNotifier.shutdown()
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
        NonceCache.clear()
        LOGGER.info("[game-api] stopped")
    }

    private class GameApiHandler(private val limiter: RateLimiter) : SimpleChannelInboundHandler<FullHttpRequest>() {
        override fun channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest) {
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
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/level-up" -> handleAccountClanLevelUp(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/level-down" -> handleAccountClanLevelDown(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/transfer-leadership" -> handleAccountClanTransferLeadership(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/ban-member" -> handleAccountClanBanMember(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/royal-guard" -> handleAccountClanRoyalGuard(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/castle-siege" -> handleAccountClanCastleSiege(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/royal-guard/move-member" -> handleAccountClanRoyalGuardMove(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/royal-guard/create" -> handleAccountClanRoyalGuardCreate(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/royal-guard/delete" -> handleAccountClanRoyalGuardDelete(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/chat" -> handleAccountClanChat(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/chat/send" -> handleAccountClanChatSend(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/clan/castle-siege/register" -> handleAccountClanSiegeRegister(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/player-reset" -> handleAccountPlayerReset(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path == "/internal/site/account/pk-reset" -> handleAccountPkReset(ctx, bodyBytes)
                    req.method() == HttpMethod.POST && path in setOf("/internal/site/donation", "/internal/site/shop") -> {
                        respond(ctx, HttpResponseStatus.ACCEPTED, mapOf("ok" to false, "message" to "Endpoint reservado; entrega automática será implementada no próximo passo"))
                    }
                    else -> respond(ctx, HttpResponseStatus.NOT_FOUND, mapOf("ok" to false, "message" to "not found"))
                }
            } catch (e: Exception) {
                LOGGER.error("[game-api] request failed {}", e, path)
                respond(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, mapOf("ok" to false, "message" to "Erro interno"))
            } finally {
                limiter.gc()
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

        private fun handleAccountClanRoyalGuardDelete(ctx: ChannelHandlerContext, bodyBytes: ByteArray) {
            val req = GSON.fromJson(String(bodyBytes, Charsets.UTF_8), RoyalGuardDeletePayload::class.java)
            val login = req.login; val charId = req.characterId; val subPledgeId = req.subPledgeId; val moveTo = req.moveToSubPledgeId ?: 0
            if (login == null || !LOGIN_REGEX.matches(login)) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Login inválido"))
            if (charId == null || charId <= 0) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Personagem inválido"))
            if (subPledgeId == null) return respond(ctx, HttpResponseStatus.BAD_REQUEST, mapOf("ok" to false, "message" to "Royal inválida"))
            val result = SiteApiRepository.deleteRoyalGuard(login, charId, subPledgeId, moveTo)
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
            val status = if (result["ok"] == true) HttpResponseStatus.OK else HttpResponseStatus.CONFLICT
            respond(ctx, status, result)
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
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
            ctx.writeAndFlush(response).addListener { ctx.close() }
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
        private data class RoyalGuardDeletePayload(val login: String? = null, val characterId: Int? = null, val subPledgeId: Int? = null, val moveToSubPledgeId: Int? = null)
        private data class ClanChatPayload(val login: String? = null, val characterId: Int? = null, val since: Long? = null)
        private data class ClanChatSendPayload(val login: String? = null, val characterId: Int? = null, val text: String? = null)
        private data class SiegeRegisterPayload(val login: String? = null, val characterId: Int? = null, val castleId: Int? = null, val type: String? = null)
        private data class PkResetPayload(val login: String? = null, val characterId: Int? = null)
        private data class PlayerResetPayload(val login: String? = null, val characterId: Int? = null)
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
