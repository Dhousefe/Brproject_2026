package ext.mods.gameserver.taskmanager

import com.lmax.disruptor.BlockingWaitStrategy
import com.lmax.disruptor.EventHandler
import com.lmax.disruptor.RingBuffer
import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.dsl.ProducerType
import ext.mods.commons.data.StatSet
import ext.mods.config.ConfigServer
import ext.mods.gameserver.enums.StatusType
import ext.mods.gameserver.model.actor.Creature
import ext.mods.gameserver.model.actor.template.CreatureTemplate
import ext.mods.gameserver.model.location.Location
import ext.mods.gameserver.network.serverpackets.MoveToLocation
import ext.mods.gameserver.network.serverpackets.StatusUpdate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicLong

class JitWarmupServiceTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            ConfigServer.ENABLE_JIT_WARMUP = true
            ConfigServer.JIT_WARMUP_ITERATIONS = 500
            ConfigServer.ENABLE_NETWORK_JIT_WARMUP = true
            ConfigServer.DISRUPTOR_WARMUP_CONNECTIONS = 10000
            ConfigServer.WARMUP_STATUS_UPDATE = true
            ConfigServer.WARMUP_USER_INFO = true
            ConfigServer.WARMUP_DISRUPTOR_BROADCAST = true
        }
    }

    private fun createDummyCreature(): Creature {
        val set = StatSet()
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

        val template = CreatureTemplate(set)
        return object : Creature(999999999, template) {
            override fun updateAbnormalEffect() {}
            override fun getActiveWeaponInstance() = null
            override fun getActiveWeaponItem() = null
            override fun getSecondaryWeaponInstance() = null
            override fun getSecondaryWeaponItem() = null
        }
    }

    @Test
    fun testMoveToLocationPacketSerialization() {
        val creature = createDummyCreature()
        creature.setXYZ(83424, 148585, -3405)
        val target = Location(82698, 148638, -3468)

        val movePacket = MoveToLocation(creature, target)
        val buf = ByteBuffer.allocateDirect(1024)

        movePacket.writePacket(null, buf)
        buf.flip()

        assertTrue(buf.remaining() > 0, "Buffer de MoveToLocation nao deve estar vazio")
        val opcode = buf.get().toInt() and 0xFF
        assertEquals(0x01, opcode, "Opcode de MoveToLocation deve ser 0x01")
    }

    @Test
    fun testStatusUpdatePacketSerialization() {
        val creature = createDummyCreature()
        val statusPacket = StatusUpdate(creature)
        statusPacket.addAttribute(StatusType.CUR_HP, 950)
        statusPacket.addAttribute(StatusType.MAX_HP, 1000)
        statusPacket.addAttribute(StatusType.CUR_MP, 400)
        statusPacket.addAttribute(StatusType.MAX_MP, 500)
        statusPacket.addAttribute(StatusType.CUR_CP, 800)
        statusPacket.addAttribute(StatusType.MAX_CP, 800)
        statusPacket.addAttribute(StatusType.PVP_FLAG, 1)
        statusPacket.addAttribute(StatusType.KARMA, 0)

        val buf = ByteBuffer.allocateDirect(1024)
        statusPacket.writePacket(null, buf)
        buf.flip()

        assertTrue(buf.remaining() > 0, "Buffer de StatusUpdate nao deve estar vazio")
        val opcode = buf.get().toInt() and 0xFF
        assertEquals(0x0e, opcode, "Opcode de StatusUpdate deve ser 0x0e")
        val objectId = buf.getInt()
        assertEquals(999999999, objectId, "ObjectId deve bater com o dummy creature")
        val attrCount = buf.getInt()
        assertEquals(8, attrCount, "StatusUpdate deve conter 8 atributos")
    }

    @Test
    fun testDisruptorWarmupWith10000Connections() {
        val connectionsCount = 10000
        val totalBroadcasts = 20
        val counter = AtomicLong(0)

        class TestVirtualSink(val id: Int) {
            fun accept(pId: Int, size: Int) {
                counter.incrementAndGet()
            }
        }

        class TestWarmupEvent {
            var packetId = 0
            var payloadSize = 0
            fun set(pId: Int, s: Int) { packetId = pId; payloadSize = s }
            fun clear() { packetId = 0; payloadSize = 0 }
        }

        val sinks = Array(connectionsCount) { TestVirtualSink(it) }
        val threadFactory = ThreadFactory { r ->
            val t = Thread(r, "Test-DisruptorWorker")
            t.isDaemon = true
            t
        }

        val disruptor = Disruptor(
            { TestWarmupEvent() },
            1024,
            threadFactory,
            ProducerType.SINGLE,
            BlockingWaitStrategy()
        )

        val handler = EventHandler<TestWarmupEvent> { event, _, _ ->
            for (sink in sinks) {
                sink.accept(event.packetId, event.payloadSize)
            }
            event.clear()
        }
        disruptor.handleEventsWith(handler)

        val ringBuffer: RingBuffer<TestWarmupEvent> = disruptor.start()

        for (i in 0 until totalBroadcasts) {
            val seq = ringBuffer.next()
            try {
                ringBuffer.get(seq).set(0x0e, 28)
            } finally {
                ringBuffer.publish(seq)
            }
        }

        // Aguarda termino e encerra
        disruptor.shutdown()

        val expected = (connectionsCount * totalBroadcasts).toLong()
        assertEquals(expected, counter.get(), "Total de entregas simuladas deve ser exatamente 200.000")
    }
}
