/*
 * MIT License
 * * Copyright (c) 2024-2026 L2Brproject
 * * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * * Our main Developers: Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
 * Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY, 
 * SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
 * as a contribution for the forum L2JBrasil.com
 */
package ext.mods.gameserver
import ext.mods.Config
import ext.mods.commons.logging.CLogger
import ext.mods.commons.network.AttributeType
import ext.mods.commons.network.ServerType
import ext.mods.commons.random.Rnd
import ext.mods.gameserver.enums.FailReason
import ext.mods.gameserver.model.World
import ext.mods.gameserver.model.actor.Player
import ext.mods.gameserver.network.GameClient
import ext.mods.gameserver.network.GameClient.GameClientState
import ext.mods.gameserver.network.SessionKey
import ext.mods.gameserver.network.gameserverpackets.AuthRequest
import ext.mods.gameserver.network.gameserverpackets.BlowFishKey
import ext.mods.gameserver.network.gameserverpackets.ChangeAccessLevel
import ext.mods.gameserver.network.gameserverpackets.GameServerBasePacket
import ext.mods.gameserver.network.gameserverpackets.PlayerAuthRequest
import ext.mods.gameserver.network.gameserverpackets.PlayerInGame
import ext.mods.gameserver.network.gameserverpackets.PlayerLogout
import ext.mods.gameserver.network.gameserverpackets.ServerStatus
import ext.mods.gameserver.network.loginserverpackets.AuthResponse
import ext.mods.gameserver.network.loginserverpackets.InitLS
import ext.mods.gameserver.network.loginserverpackets.KickPlayer
import ext.mods.gameserver.network.loginserverpackets.LoginServerFail
import ext.mods.gameserver.network.loginserverpackets.PlayerAuthResponse
import ext.mods.gameserver.network.serverpackets.AuthLoginFail
import ext.mods.gameserver.network.serverpackets.CharSelectInfo
import ext.mods.loginserver.crypt.NewCrypt
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.Socket
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap
import ext.mods.config.ConfigHexId
import ext.mods.config.ConfigServer
class LoginServerThread private constructor() : Thread("LoginServerThread") {
    private val clients = ConcurrentHashMap<String, GameClient>()
    private var loginSocket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var blowfish: NewCrypt? = null
    private var blowfishKey: ByteArray? = null
    private var publicKey: RSAPublicKey? = null
    private val hexId: ByteArray
    private val requestId: Int
    private var serverId = 0
    private var _serverName: String? = null
    var maxPlayers: Int
        get() = _maxPlayers
        set(value) {
            _maxPlayers = value
            sendServerStatus(AttributeType.MAX_PLAYERS, value)
        }
    var serverType: ServerType
        get() = _serverType
        set(value) {
            _serverType = value
            sendServerStatus(AttributeType.STATUS, value.id)
        }
    private var _maxPlayers: Int = ConfigServer.MAXIMUM_ONLINE_USERS
    private var _serverType: ServerType = ServerType.AUTO
    init {
        val tempHex = ConfigHexId.HEX_ID
        if (tempHex == null) {
            requestId = ConfigServer.REQUEST_ID
            hexId = generateHex(16)
        } else {
            requestId = ConfigHexId.SERVER_ID
            hexId = tempHex
        }
        priority = MAX_PRIORITY
    }
    fun getServerName(): String = _serverName ?: "BrProject"
    @JvmName("setMaxPlayer")
    fun setMaxPlayer(num: Int) {
        maxPlayers = num
    }
    override fun run() {
        while (!isInterrupted) {
            try {
                LOGGER.info("Connecting to login on {}:{}.", ConfigServer.GAMESERVER_LOGIN_HOSTNAME, ConfigServer.GAMESERVER_LOGIN_PORT)
                loginSocket = Socket(ConfigServer.GAMESERVER_LOGIN_HOSTNAME, ConfigServer.GAMESERVER_LOGIN_PORT)
                // CRITICAL: without a SO timeout, a hung/crashed LoginServer blocks read() forever
                // (until OS TCP keepalive ~2h). Set 30s so the retry loop can recover quickly.
                loginSocket!!.soTimeout = SOCKET_TIMEOUT_MS
                inputStream = loginSocket!!.getInputStream()
                outputStream = BufferedOutputStream(loginSocket!!.getOutputStream())
                blowfishKey = generateHex(40)
                blowfish = NewCrypt("_;v.]05-31!|+-%xT!^[$\u0000")
                val inp = inputStream!!
                while (!isInterrupted) {
                    try {
                        val decrypt = readFrame(inp, blowfish!!) ?: break
                        val packetType = decrypt[0].toInt() and 0xff
                        handlePacket(packetType, decrypt)
                    } catch (_: java.net.SocketTimeoutException) {
                        // Normal: LoginServer has no data to send right now.
                        // Stay connected, just loop back and wait for next packet.
                        continue
                    }
                }
            } catch (_: Exception) {
                LOGGER.error("No connection found with loginserver, next try in 10 seconds.")
            } finally {
                try {
                    blowfish = null
                    loginSocket?.close()
                    outputStream?.close()
                    inputStream?.close()
                } catch (_: Exception) { }
                if (isInterrupted) return
            }
            try {
                sleep(10000)
            } catch (_: InterruptedException) {
                return
            }
        }
    }
    private fun handlePacket(packetType: Int, data: ByteArray) {
        when (packetType) {
            0x00 -> handleInitLs(data)
            0x01 -> handleLoginServerFail(data)
            0x02 -> handleAuthResponse(data)
            0x03 -> handlePlayerAuthResponse(data)
            0x04 -> handleKickPlayer(data)
        }
    }
    private fun handleInitLs(data: ByteArray) {
        val init = InitLS(data)
        if (init.revision != REVISION) {
            LOGGER.warn("Revision mismatch between LS and GS.")
            return
        }
        try {
            val kfac = KeyFactory.getInstance("RSA")
            val modulus = BigInteger(init.rsaKey)
            val kspec = RSAPublicKeySpec(modulus, RSAKeyGenParameterSpec.F4)
            publicKey = kfac.generatePublic(kspec) as RSAPublicKey
            sendPacket(BlowFishKey(blowfishKey!!, publicKey!!))
            blowfish = NewCrypt(blowfishKey!!)
            sendPacket(AuthRequest(requestId, ConfigServer.ACCEPT_ALTERNATE_ID, hexId, ConfigServer.HOSTNAME, ConfigServer.GAMESERVER_PORT, Config.RESERVE_HOST_ON_LOGIN, _maxPlayers))
        } catch (e: Exception) {
            LOGGER.error("Troubles while init the public key sent by login.")
        }
    }
    private fun handleLoginServerFail(data: ByteArray) {
        val lsf = LoginServerFail(data)
        LOGGER.info("LoginServer registration failed: {}.", lsf.reasonString)
    }
    private fun handleAuthResponse(data: ByteArray) {
        val aresp = AuthResponse(data)
        serverId = aresp.serverId
        _serverName = aresp.serverName
        Config.saveHexid(serverId, BigInteger(hexId).toString(16))
        LOGGER.info("Registered as server: [{}] {}.", serverId, _serverName)
        val ss = ServerStatus()
        ss.addAttribute(AttributeType.STATUS, if (ConfigServer.SERVER_GMONLY) ServerType.GM_ONLY.id else ServerType.AUTO.id)
        ss.addAttribute(AttributeType.CLOCK, ConfigServer.SERVER_LIST_CLOCK)
        ss.addAttribute(AttributeType.BRACKETS, ConfigServer.SERVER_LIST_BRACKET)
        ss.addAttribute(AttributeType.AGE_LIMIT, ConfigServer.SERVER_LIST_AGE)
        ss.addAttribute(AttributeType.TEST_SERVER, ConfigServer.SERVER_LIST_TESTSERVER)
        ss.addAttribute(AttributeType.PVP_SERVER, ConfigServer.SERVER_LIST_PVPSERVER)
        sendPacket(ss)
        val players: Collection<Player> = World.getInstance().players

        // Fail2Ban: auto-start after server registration
        initFail2Ban()
        if (players.isNotEmpty()) {
            val playerList = ArrayList(players.map { it.accountName })
            sendPacket(PlayerInGame(playerList))
        }
    }
    private fun handlePlayerAuthResponse(data: ByteArray) {
        val par = PlayerAuthResponse(data)
        val client = clients[par.account] ?: return
        client.realIpAddress = par.realIpAddress
        if (par.isAuthed) {
            sendPacket(PlayerInGame(par.account))
            client.state = GameClientState.AUTHED
            val playOk = client.sessionId?.playOkID1 ?: 0
            client.sendPacket(CharSelectInfo(par.account, playOk))
        } else {
            client.sendPacket(AuthLoginFail(FailReason.SYSTEM_ERROR_LOGIN_LATER))
            client.closeNow()
        }
    }
    private fun handleKickPlayer(data: ByteArray) {
        val kp = KickPlayer(data)
        kickPlayer(kp.account)
    }
    fun sendLogout(account: String?) {
        if (account == null) return
        try {
            sendPacket(PlayerLogout(account))
        } catch (e: IOException) {
            LOGGER.error("Error while sending logout packet to login.")
        } finally {
            clients.remove(account)
        }
    }
    fun addClient(account: String, client: GameClient) {
        val existing = clients.putIfAbsent(account, client)
        if (client.isDetached) return
        if (existing == null) {
            try {
                val session = client.sessionId
                if (session != null) {
                    sendPacket(PlayerAuthRequest(client.accountName, session))
                } else {
                    LOGGER.error("Error while sending player auth request.")
                }
            } catch (e: IOException) {
                LOGGER.error("Error while sending player auth request.")
            }
        } else {
            client.closeNow()
            existing.closeNow()
        }
    }
    fun addClient(loginName: String, loginKey1: Int, loginKey2: Int, playKey1: Int, playKey2: Int, client: GameClient) {
        val existing = clients.putIfAbsent(loginName, client)
        if (existing != null) {
            existing.closeNow()
            return
        }
        if (client.isDetached) return
        try {
            client.accountName = loginName
            client.sessionId = SessionKey(loginKey1, loginKey2, playKey1, playKey2)
            sendPacket(PlayerAuthRequest(client.accountName, client.sessionId!!))
        } catch (e: IOException) {
            LOGGER.error("Error while sending player auth request.")
        }
    }
    fun sendAccessLevel(account: String, level: Int) {
        try {
            sendPacket(ChangeAccessLevel(account, level))
        } catch (_: IOException) { }
    }
    fun kickPlayer(account: String) {
        clients[account]?.closeNow()
    }
    private fun sendServerStatus(type: AttributeType, value: Int) {
        try {
            val ss = ServerStatus()
            ss.addAttribute(type, value)
            sendPacket(ss)
        } catch (_: IOException) { }
    }
    @Synchronized
    private fun sendPacket(sl: GameServerBasePacket) {
        val bf = blowfish
        val out = outputStream
        if (bf == null || out == null) return
        try {
            var data = sl.content
            NewCrypt.appendChecksum(data)
            data = bf.crypt(data)
            val len = data.size + 2
            synchronized(out) {
                out.write(len and 0xff)
                out.write(len shr 8 and 0xff)
                out.write(data)
                out.flush()
            }
        } catch (e: IOException) {
            LOGGER.error("Error sending packet to LoginServer: ${e.message}")
        }
    }
    
    /**
     * Initialize Fail2Ban system in background thread (non-blocking).
     */
    private fun initFail2Ban() {
        Thread({
            try {
                val mgr = ext.mods.security.fail2ban.Fail2BanInitializer.initialize()
                if (mgr != null) {
                    LOGGER.info("Fail2Ban initialized with {} active bans.", mgr.getActiveBans().size)
                }
            } catch (e: Exception) {
                LOGGER.error("Fail2Ban init failed: {}", e.message)
            }
        }, "fail2ban-init").apply { isDaemon = true }.start()
    }
    
    companion object {
        private const val REVISION = 0x0102
        private const val SOCKET_TIMEOUT_MS = 30000
        private val LOGGER = CLogger(LoginServerThread::class.java.name)
        private val instanceHolder = lazy { LoginServerThread() }
        @JvmStatic
        fun getInstance(): LoginServerThread = instanceHolder.value
        @JvmStatic
        fun generateHex(size: Int): ByteArray {
            val array = ByteArray(size)
            Rnd.nextBytes(array)
            return array
        }

        /**
         * Read a single decrypted packet frame from the input stream.
         * Returns null on EOF (stream closed gracefully).
         * Throws SocketTimeoutException if no data arrives within [SOCKET_TIMEOUT_MS].
         *
         * Packet format (wire):
         * - byte 0: length low
         * - byte 1: length high
         * - bytes 2..(length-1): encrypted payload
         */
        internal fun readFrame(inp: InputStream, blowfish: NewCrypt): ByteArray? {
            val lengthLo = inp.read()
            if (lengthLo < 0) return null
            val lengthHi = inp.read()
            if (lengthHi < 0) return null
            val length = lengthHi * 256 + lengthLo
            if (length < 2) return null
            val incoming = ByteArray(length - 2)
            var receivedBytes = 0
            var left = length - 2
            while (receivedBytes < length - 2) {
                val n = inp.read(incoming, receivedBytes, left)
                if (n == -1) return null
                receivedBytes += n
                left -= n
            }
            if (receivedBytes != length - 2) return null
            val decrypt = blowfish.decrypt(incoming)
            if (!NewCrypt.verifyChecksum(decrypt)) return null
            return decrypt
        }
    }
}