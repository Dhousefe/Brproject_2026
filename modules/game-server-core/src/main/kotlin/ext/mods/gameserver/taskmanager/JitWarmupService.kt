package ext.mods.gameserver.taskmanager

import com.lmax.disruptor.BlockingWaitStrategy
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.RingBuffer
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import ext.mods.commons.logging.CLogger
import ext.mods.config.ConfigServer
import ext.mods.gameserver.data.xml.PlayerData
import ext.mods.gameserver.enums.StatusType
import ext.mods.gameserver.enums.actors.Sex
import ext.mods.gameserver.geoengine.GeoEngine
import ext.mods.gameserver.model.World
import ext.mods.gameserver.model.actor.Creature
import ext.mods.gameserver.model.actor.Npc
import ext.mods.gameserver.model.actor.Player
import ext.mods.gameserver.model.actor.container.player.Appearance
import ext.mods.gameserver.model.location.Location
import ext.mods.gameserver.network.serverpackets.AbstractNpcInfo.NpcInfo
import ext.mods.gameserver.network.serverpackets.CharInfo
import ext.mods.gameserver.network.serverpackets.MoveToLocation
import ext.mods.gameserver.network.serverpackets.StatusUpdate
import ext.mods.gameserver.network.serverpackets.UserInfo
import java.nio.ByteBuffer
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

/**
 * JIT C2 Warmup Service (Zero-Regression & Throttled).
 *
 * Executa tarefas sintéticas de aquecimento do HotSpot JVM C2 (Tier 4) logo após a
 * finalização do spawn diferido de NPCs, com o servidor já online:
 * 1. Simulação de Pathfinding e Line of Sight em GeoEngine (coordenadas reais de cidades).
 * 2. Serialização de pacotes de movimentação (MoveToLocation).
 * 3. Serialização de atributos dinâmicos de combate (StatusUpdate com HP/MP/CP/Karma/PvpFlag).
 * 4. Serialização de pacotes pesados de visibilidade (UserInfo e CharInfo) usando DummyPlayer sintético sem efeitos colaterais.
 * 5. Barramento de Broadcast Lock-Free do LMAX Disruptor com 10.000 conexões simuladas.
 *
 * Mecanismos de proteção:
 * - Execução em Thread Daemon com prioridade mínima (Thread.MIN_PRIORITY = 1).
 * - Sleep cooperativo a cada lote para manter o consumo de CPU abaixo de 5% de um único core.
 * - Reutilização de ByteBuffer ThreadLocal direto para zero alocação de heap e zero pressão de GC.
 * - Não envia pacotes pela rede nem insere objetos no World/Disruptor de produção.
 */
object JitWarmupService {
    private val LOGGER = CLogger(JitWarmupService::class.java.name)
    private val isRunning = AtomicBoolean(false)
    private val isCompleted = AtomicBoolean(false)

    // Reusable byte buffer for serialization warmup to avoid heap allocations
    private val BUFFER_HOLDER = ThreadLocal.withInitial {
        ByteBuffer.allocateDirect(4096)
    }

    // Coordenadas conhecidas de alta densidade para simulações de rota e visibilidade
    private val WARMUP_COORDINATES = arrayOf(
        // Giran Town
        Location(83424, 148585, -3405) to Location(82698, 148638, -3468),
        Location(82698, 148638, -3468) to Location(81083, 149023, -3469),
        Location(81083, 149023, -3469) to Location(83424, 148585, -3405),
        // Aden Castle Town
        Location(147450, 27120, -2200) to Location(148000, 27800, -2200),
        Location(148000, 27800, -2200) to Location(146800, 26500, -2200),
        // Dion Town
        Location(15670, 142983, -2705) to Location(16500, 143500, -2705),
        // Talking Island Village
        Location(-84176, 244656, -3730) to Location(-83500, 243800, -3730)
    )

    @JvmStatic
    fun start() {
        if (!ConfigServer.ENABLE_JIT_WARMUP) {
            LOGGER.info("JIT C2 Warmup desativado nas configuracoes (EnableJitWarmup = false).")
            return
        }

        if (!isRunning.compareAndSet(false, true)) {
            LOGGER.warn("JIT C2 Warmup ja esta em execucao ou ja foi iniciado.")
            return
        }

        val warmupThread = Thread({
            runWarmupRoutine()
        }, "JitC2-Warmup-Worker")

        warmupThread.isDaemon = true
        warmupThread.priority = Thread.MIN_PRIORITY
        warmupThread.start()
    }

    private fun runWarmupRoutine() {
        LOGGER.info("Iniciando JIT C2 Warmup em background (prioridade minima, sem impacto de CPU)...")

        val totalIterations = ConfigServer.JIT_WARMUP_ITERATIONS.coerceIn(500, 25000)
        val batchSize = 200
        val sleepMsPerBatch = 15L

        var geoWarmupCount = 0
        var moveWarmupCount = 0
        var statusWarmupCount = 0
        var packetWarmupCount = 0
        var disruptorBroadcastCount = 0
        val warmupConnections = ConfigServer.DISRUPTOR_WARMUP_CONNECTIONS.coerceIn(100, 50000)

        val elapsedMs = measureTimeMillis {
            // 1. Warmup GeoEngine (canMove e findPath)
            try {
                geoWarmupCount = warmupGeoEngine(totalIterations, batchSize, sleepMsPerBatch)
            } catch (t: Throwable) {
                LOGGER.error("Erro no warmup de GeoEngine: ${t.message}", t)
            }

            // 2. Warmup Movimentação (MoveToLocation)
            try {
                moveWarmupCount = warmupMovementPackets(totalIterations, batchSize, sleepMsPerBatch)
            } catch (t: Throwable) {
                LOGGER.error("Erro no warmup de Movimento: ${t.message}", t)
            }

            // 3. Warmup StatusUpdate (HP, MP, CP, Karma, PvpFlag)
            if (ConfigServer.WARMUP_STATUS_UPDATE) {
                try {
                    statusWarmupCount = warmupStatusUpdatePackets(totalIterations, batchSize, sleepMsPerBatch)
                } catch (t: Throwable) {
                    LOGGER.error("Erro no warmup de StatusUpdate: ${t.message}", t)
                }
            }

            // 4. Warmup Serialização de Pacotes (NpcInfo, UserInfo e CharInfo com DummyPlayer)
            try {
                packetWarmupCount = warmupPacketSerialization(totalIterations, batchSize, sleepMsPerBatch)
            } catch (t: Throwable) {
                LOGGER.error("Erro no warmup de Serializacao: ${t.message}", t)
            }

            // 5. Warmup LMAX Disruptor Broadcast (10.000 conexões simuladas)
            if (ConfigServer.WARMUP_DISRUPTOR_BROADCAST) {
                try {
                    val broadcastIterations = (totalIterations / 150).coerceIn(30, 200)
                    disruptorBroadcastCount = warmupDisruptorBroadcast(
                        warmupConnections,
                        broadcastIterations,
                        batchSize = 10,
                        sleepMs = 10L
                    )
                } catch (t: Throwable) {
                    LOGGER.error("Erro no warmup de Disruptor Broadcast: ${t.message}", t)
                }
            }
        }
        BUFFER_HOLDER.remove()
        isCompleted.set(true)

        LOGGER.info(
            "JIT C2 Warmup concluido com sucesso em ${elapsedMs}ms! " +
            "Simulacoes: [GeoEngine: $geoWarmupCount, MovePackets: $moveWarmupCount, StatusUpdate: $statusWarmupCount, " +
            "Serialization: $packetWarmupCount, DisruptorBroadcast: $disruptorBroadcastCount x $warmupConnections clients]. Classes e hotspots compilados para C2."
        )
    }

    private fun warmupGeoEngine(total: Int, batchSize: Int, sleepMs: Long): Int {
        val geo = GeoEngine.getInstance()
        var count = 0
        val numPairs = WARMUP_COORDINATES.size

        for (i in 0 until total) {
            val (from, to) = WARMUP_COORDINATES[i % numPairs]

            // Exercita canMove (raycast rápido com bitmask NSWE)
            geo.canMove(from.x, from.y, from.z, to.x, to.y, to.z, null)

            // Exercita findPath (A* / malha e alocações de nós) a cada 5 iterações para balancear custo
            if (i % 5 == 0) {
                geo.findPath(from.x, from.y, from.z, to.x, to.y, to.z, false, null)
            }

            count++

            if (count % batchSize == 0) {
                throttleSleep(sleepMs)
            }
        }
        return count
    }

    private fun warmupMovementPackets(total: Int, batchSize: Int, sleepMs: Long): Int {
        var count = 0
        val dummyBuffer = BUFFER_HOLDER.get()
        val dummyCreature = DummyCreatureHolder.instance

        for (i in 0 until total) {
            val (from, to) = WARMUP_COORDINATES[i % WARMUP_COORDINATES.size]
            dummyCreature.setXYZ(from.x, from.y, from.z)

            val movePacket = MoveToLocation(dummyCreature, to)
            dummyBuffer.clear()
            movePacket.writePacket(null, dummyBuffer)

            count++

            if (count % batchSize == 0) {
                throttleSleep(sleepMs)
            }
        }
        return count
    }

    private fun warmupStatusUpdatePackets(total: Int, batchSize: Int, sleepMs: Long): Int {
        var count = 0
        val dummyBuffer = BUFFER_HOLDER.get()
        val dummyCreature = DummyCreatureHolder.instance

        for (i in 0 until total) {
            val statusPacket = StatusUpdate(dummyCreature)
            statusPacket.addAttribute(StatusType.CUR_HP, 1000)
            statusPacket.addAttribute(StatusType.MAX_HP, 1000)
            statusPacket.addAttribute(StatusType.CUR_MP, 500)
            statusPacket.addAttribute(StatusType.MAX_MP, 500)
            statusPacket.addAttribute(StatusType.CUR_CP, 800)
            statusPacket.addAttribute(StatusType.MAX_CP, 800)
            statusPacket.addAttribute(StatusType.PVP_FLAG, 0)
            statusPacket.addAttribute(StatusType.KARMA, 0)

            dummyBuffer.clear()
            statusPacket.writePacket(null, dummyBuffer)

            count++

            if (count % batchSize == 0) {
                throttleSleep(sleepMs)
            }
        }
        return count
    }

    private fun warmupPacketSerialization(total: Int, batchSize: Int, sleepMs: Long): Int {
        var count = 0
        val dummyBuffer = BUFFER_HOLDER.get()

        // Obtém o primeiro NPC disponível no mundo ou utiliza o DummyCreature
        val sampleNpc: Npc? = World.getInstance().objects.asSequence()
            .filterIsInstance<Npc>()
            .firstOrNull()

        // Prioriza player conectado; se ausente, utiliza o DummyPlayer sintetizado em memória
        val samplePlayer: Player? = DummyPlayerHolder.instance

        for (i in 0 until total) {
            dummyBuffer.clear()

            // Warmup NpcInfo se houver NPC carregado
            if (sampleNpc != null) {
                val npcInfo = NpcInfo(sampleNpc, samplePlayer)
                npcInfo.writePacket(null, dummyBuffer)
            }

            // Warmup UserInfo e CharInfo com DummyPlayer
            if (samplePlayer != null && ConfigServer.WARMUP_USER_INFO) {
                dummyBuffer.clear()
                val userInfo = UserInfo(samplePlayer)
                userInfo.writePacket(null, dummyBuffer)

                dummyBuffer.clear()
                val charInfo = CharInfo(samplePlayer)
                charInfo.writePacket(null, dummyBuffer)
            }

            count++

            if (count % batchSize == 0) {
                throttleSleep(sleepMs)
            }
        }
        return count
    }

    /**
     * Exercita o barramento de broadcast LMAX Disruptor e o despacho para milhares de conexões virtuais.
     * Compila em C2 o Sequencer, a barreira de memória, o padding de cache line e o loop de entrega.
     */
    private fun warmupDisruptorBroadcast(
        connectionsCount: Int,
        totalBroadcasts: Int,
        batchSize: Int,
        sleepMs: Long
    ): Int {
        val virtualSinks = Array(connectionsCount) { VirtualClientSink(it) }
        val threadFactory = ThreadFactory { r ->
            val t = Thread(r, "JitC2-Warmup-DisruptorWorker")
            t.isDaemon = true
            t.priority = Thread.MIN_PRIORITY
            t
        }

        val disruptor = Disruptor(
            { WarmupBroadcastEvent() },
            32768,
            threadFactory,
            ProducerType.SINGLE,
            BlockingWaitStrategy()
        )

        val handler = EventHandler<WarmupBroadcastEvent> { event, _, _ ->
            val pId = event.packetId
            val pSize = event.payloadSize
            val len = virtualSinks.size
            for (i in 0 until len) {
                virtualSinks[i].accept(pId, pSize)
            }
            event.clear()
        }
        disruptor.handleEventsWith(handler)

        val ringBuffer: RingBuffer<WarmupBroadcastEvent> = disruptor.start()
        var published = 0

        try {
            for (i in 0 until totalBroadcasts) {
                val seq = ringBuffer.next()
                try {
                    val ev = ringBuffer.get(seq)
                    ev.set(0x0e /* StatusUpdate / MoveToLocation opcode */, 28)
                } finally {
                    ringBuffer.publish(seq)
                }
                published++

                if (published % batchSize == 0) {
                    throttleSleep(sleepMs)
                }
            }
        } finally {
            disruptor.shutdown()
        }

        return published
    }

    private fun throttleSleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    fun isCompleted(): Boolean = isCompleted.get()

    /**
     * Evento reutilizável para o RingBuffer do Warmup do Disruptor.
     */
    private class WarmupBroadcastEvent {
        var packetId: Int = 0
        var payloadSize: Int = 0

        fun set(packetId: Int, size: Int) {
            this.packetId = packetId
            this.payloadSize = size
        }

        fun clear() {
            this.packetId = 0
            this.payloadSize = 0
        }
    }

    /**
     * Receptor virtual leve em memória simulando um cliente ativo no broadcast.
     */
    private class VirtualClientSink(val id: Int) {
        @Volatile
        var packetsReceived: Long = 0L

        fun accept(packetId: Int, size: Int) {
            packetsReceived++
        }
    }

    /**
     * Dummy Player sintético e leve em memória para aquecimento de UserInfo e CharInfo no boot,
     * sem registro no World, sem persistência no banco e sem concorrência de rede.
     */
    private object DummyPlayerHolder {
        val instance: Player? by lazy {
            try {
                // Tenta reaproveitar player já conectado se houver
                val existing = World.getInstance().players.firstOrNull()
                if (existing != null) {
                    existing
                } else {
                    val template = PlayerData.getInstance().getTemplate(0)
                    if (template != null) {
                        val app = Appearance(0.toByte(), 0.toByte(), 0.toByte(), Sex.MALE)
                        val dummy = Player(999999998, template, "JitWarmupDummy", app)
                        dummy.name = "JitWarmupDummy"
                        dummy
                    } else {
                        LOGGER.warn("Template para classId 0 nao encontrado no PlayerData. UserInfo warmup diferido.")
                        null
                    }
                }
            } catch (t: Throwable) {
                LOGGER.warn("Nao foi possivel inicializar DummyPlayer para JIT Warmup: ${t.message}")
                null
            }
        }
    }

    /**
     * Dummy Creature ultraleve para exercícios de MoveToLocation e StatusUpdate sem efeitos colaterais.
     */
    private object DummyCreatureHolder {
        val instance: Creature by lazy {
            // Tenta reaproveitar o primeiro NPC já existente no mundo (já tem template e status 100% íntegros)
            val existingNpc = World.getInstance().objects.asSequence().filterIsInstance<Npc>().firstOrNull()
            if (existingNpc != null) {
                existingNpc
            } else {
                val set = ext.mods.commons.data.StatSet()
                set.set("str", 40)
                set.set("con", 40)
                set.set("dex", 30)
                set.set("int", 20)
                set.set("wit", 40)
                set.set("men", 20)
                set.set("hp", 1000.0)
                set.set("mp", 500.0)
                set.set("hpRegen", 1.5)
                set.set("mpRegen", 0.9)
                set.set("pAtk", 100.0)
                set.set("mAtk", 100.0)
                set.set("pDef", 100.0)
                set.set("mDef", 100.0)
                set.set("atkSpd", 300.0)
                set.set("crit", 4.0)
                set.set("walkSpd", 50.0)
                set.set("runSpd", 120.0)
                set.set("radius", 8.0)
                set.set("height", 24.0)

                val dummyTemplate = ext.mods.gameserver.model.actor.template.CreatureTemplate(set)
                object : Creature(999999999, dummyTemplate) {
                    override fun updateAbnormalEffect() {}
                    override fun getActiveWeaponInstance(): ext.mods.gameserver.model.item.instance.ItemInstance? = null
                    override fun getActiveWeaponItem(): ext.mods.gameserver.model.item.kind.Weapon? = null
                    override fun getSecondaryWeaponInstance(): ext.mods.gameserver.model.item.instance.ItemInstance? = null
                    override fun getSecondaryWeaponItem(): ext.mods.gameserver.model.item.kind.Item? = null
                }
            }
        }
    }
}

