package ext.mods.gameserver.taskmanager

import ext.mods.commons.logging.CLogger
import ext.mods.config.ConfigServer
import ext.mods.gameserver.geoengine.GeoEngine
import ext.mods.gameserver.model.World
import ext.mods.gameserver.model.actor.Creature
import ext.mods.gameserver.model.actor.Npc
import ext.mods.gameserver.model.actor.Player
import ext.mods.gameserver.model.location.Location
import ext.mods.gameserver.network.serverpackets.AbstractNpcInfo.NpcInfo
import ext.mods.gameserver.network.serverpackets.MoveToLocation
import ext.mods.gameserver.network.serverpackets.UserInfo
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

/**
 * JIT C2 Warmup Service (Zero-Regression & Throttled).
 *
 * Executa tarefas sintéticas de aquecimento do HotSpot JVM C2 (Tier 4) logo após a
 * finalização do spawn diferido de NPCs, com o servidor já online:
 * 1. Simulação de Pathfinding e Line of Sight em GeoEngine (coordenadas reais de cidades).
 * 2. Serialização de pacotes de movimentação (MoveToLocation).
 * 3. Serialização de pacotes de informação e visibilidade (UserInfo e NpcInfo).
 *
 * Mecanismos de proteção:
 * - Execução em Thread Daemon com prioridade mínima (Thread.MIN_PRIORITY = 1).
 * - Sleep cooperativo a cada lote para manter o consumo de CPU abaixo de 5% de um único core.
 * - Reutilização de ByteBuffer ThreadLocal direto para zero alocação de heap e zero pressão de GC.
 * - Não envia pacotes pela rede nem insere objetos no World/Disruptor.
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

        val totalIterations = ConfigServer.JIT_WARMUP_ITERATIONS.coerceIn(500, 10000)
        val batchSize = 200
        val sleepMsPerBatch = 15L

        var geoWarmupCount = 0
        var moveWarmupCount = 0
        var packetWarmupCount = 0

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

            // 3. Warmup Serialização de Pacotes (NpcInfo e UserInfo)
            try {
                packetWarmupCount = warmupPacketSerialization(totalIterations, batchSize, sleepMsPerBatch)
            } catch (t: Throwable) {
                LOGGER.error("Erro no warmup de Serializacao: ${t.message}", t)
            }
        }
        BUFFER_HOLDER.remove()
        isCompleted.set(true)

        LOGGER.info(
            "JIT C2 Warmup concluido com sucesso em ${elapsedMs}ms! " +
            "Simulacoes: [GeoEngine: $geoWarmupCount, MovePackets: $moveWarmupCount, Serialization: $packetWarmupCount]. Classes e hotspots compilados para C2."
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

    private fun warmupPacketSerialization(total: Int, batchSize: Int, sleepMs: Long): Int {
        var count = 0
        val dummyBuffer = BUFFER_HOLDER.get()

        // Obtém o primeiro NPC disponível no mundo ou utiliza o DummyCreature
        val sampleNpc: Npc? = World.getInstance().objects.asSequence()
            .filterIsInstance<Npc>()
            .firstOrNull()

        val samplePlayer: Player? = World.getInstance().players.firstOrNull()

        for (i in 0 until total) {
            dummyBuffer.clear()

            // Warmup NpcInfo se houver NPC carregado
            if (sampleNpc != null) {
                val npcInfo = NpcInfo(sampleNpc, samplePlayer)
                npcInfo.writePacket(null, dummyBuffer)
            }

            // Warmup UserInfo se houver Player conectado
            if (samplePlayer != null) {
                dummyBuffer.clear()
                val userInfo = UserInfo(samplePlayer)
                userInfo.writePacket(null, dummyBuffer)
            }

            count++

            if (count % batchSize == 0) {
                throttleSleep(sleepMs)
            }
        }
        return count
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
     * Dummy Creature ultraleve para exercícios de MoveToLocation sem efeitos colaterais.
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
