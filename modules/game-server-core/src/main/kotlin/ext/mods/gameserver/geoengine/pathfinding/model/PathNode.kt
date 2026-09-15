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
package ext.mods.gameserver.geoengine.pathfinding.model
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
class PathNode private constructor(
    private val segment: MemorySegment,
    val nodeId: Int
) {
    companion object {
        const val NODE_SIZE = 32L
        
        const val OFFSET_X = 0L
        const val OFFSET_Y = 2L
        const val OFFSET_Z = 4L
        const val OFFSET_FLAGS = 5L
        
        const val OFFSET_JPS_DISTANCES = 8L
        const val OFFSET_SUCCESSOR_OFFSET = 12L
        
        const val OFFSET_SUCCESSOR_COUNT = 16L
        const val OFFSET_CLUSTER_ID = 20L
        
        const val FLAG_WALKABLE = 0x01
        const val FLAG_DYNAMIC = 0x02
        const val FLAG_MULTILAYER = 0x04
        const val FLAG_RESERVED1 = 0x08
        const val FLAG_RESERVED2 = 0x10
        const val FLAG_RESERVED3 = 0x20
        const val FLAG_RESERVED4 = 0x40
        const val FLAG_RESERVED5 = 0x80
    
        fun fromMemory(segment: MemorySegment, nodeId: Int): PathNode = PathNode(segment, nodeId)
    }
    val x: Int get() = segment.get(ValueLayout.JAVA_SHORT, OFFSET_X).toInt() and 0xFFFF
    val y: Int get() = segment.get(ValueLayout.JAVA_SHORT, OFFSET_Y).toInt() and 0xFFFF
    val z: Int get() = segment.get(ValueLayout.JAVA_BYTE, OFFSET_Z).toInt() and 0xFF
    val flags: Byte get() = segment.get(ValueLayout.JAVA_BYTE, OFFSET_FLAGS)
    val jpsDistances: Int get() = segment.get(ValueLayout.JAVA_INT, OFFSET_JPS_DISTANCES)
    val successorOffset: Int get() = segment.get(ValueLayout.JAVA_INT, OFFSET_SUCCESSOR_OFFSET)
    val successorCount: Int get() = segment.get(ValueLayout.JAVA_SHORT, OFFSET_SUCCESSOR_COUNT).toInt() and 0xFFFF
    val clusterId: Int get() = segment.get(ValueLayout.JAVA_INT, OFFSET_CLUSTER_ID)
    
    val isWalkable: Boolean get() = (flags.toInt() and FLAG_WALKABLE) != 0
    
    val hasDynamicCheck: Boolean get() = (flags.toInt() and FLAG_DYNAMIC) != 0
    
    fun getJpsDistance(direction: Int): Int {
        return (jpsDistances ushr (direction * 4)) and 0xF
    }
    
    fun getJpsDistance(direction: Direction): Int = getJpsDistance(direction.ordinal)
    
    fun toLocation(): Location {
        return Location(
            x = x * GeoStructure.CELL_SIZE + World.TILE_X_MIN * GeoStructure.REGION_SIZE,
            y = y * GeoStructure.CELL_SIZE + World.TILE_Y_MIN * GeoStructure.REGION_SIZE,
            z = z * 16
        )
    }
    
    fun toGeoLocation(): GeoLocation = GeoLocation(x, y, z.toShort())
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PathNode) return false
        return nodeId == other.nodeId
    }
    override fun hashCode(): Int = nodeId
    override fun toString(): String = "PathNode(id=$nodeId, pos=($x,$y,$z), walkable=$isWalkable)"
}
class PathNodeBuilder {
    var x: Int = 0
    var y: Int = 0
    var z: Int = 0
    var flags: Byte = 0
    var jpsDistances: Int = 0
    var successorOffset: Int = 0
    var successorCount: Int = 0
    var clusterId: Int = 0
    fun setPosition(geoX: Int, geoY: Int, worldZ: Short) {
        x = geoX
        y = geoY
        z = (worldZ.toInt() shr 4).coerceIn(0, 255)
    }
    fun setWalkable(walkable: Boolean) {
        flags = if (walkable) (flags.toInt() or PathNode.FLAG_WALKABLE).toByte() else (flags.toInt() and PathNode.FLAG_WALKABLE.inv()).toByte()
    }
    fun setDynamic(dynamic: Boolean) {
        flags = if (dynamic) (flags.toInt() or PathNode.FLAG_DYNAMIC).toByte() else (flags.toInt() and PathNode.FLAG_DYNAMIC.inv()).toByte()
    }
    
    fun setJpsDistance(direction: Int, distance: Int) {
        val shift = direction * 4
        val mask = 0xF shl shift
        jpsDistances = (jpsDistances and mask.inv()) or ((distance and 0xF) shl shift)
    }
    fun setJpsDistance(direction: Direction, distance: Int) = setJpsDistance(direction.ordinal, distance)
    
    fun writeTo(segment: MemorySegment, offset: Long = 0L) {
        segment.set(ValueLayout.JAVA_SHORT, offset + PathNode.OFFSET_X, x.toShort())
        segment.set(ValueLayout.JAVA_SHORT, offset + PathNode.OFFSET_Y, y.toShort())
        segment.set(ValueLayout.JAVA_BYTE, offset + PathNode.OFFSET_Z, z.toByte())
        segment.set(ValueLayout.JAVA_BYTE, offset + PathNode.OFFSET_FLAGS, flags)
        segment.set(ValueLayout.JAVA_INT, offset + PathNode.OFFSET_JPS_DISTANCES, jpsDistances)
        segment.set(ValueLayout.JAVA_INT, offset + PathNode.OFFSET_SUCCESSOR_OFFSET, successorOffset)
        segment.set(ValueLayout.JAVA_SHORT, offset + PathNode.OFFSET_SUCCESSOR_COUNT, successorCount.toShort())
        segment.set(ValueLayout.JAVA_INT, offset + PathNode.OFFSET_CLUSTER_ID, clusterId)
        for (i in 24 until 32) {
            segment.set(ValueLayout.JAVA_BYTE, offset + i, 0)
        }
    }
    fun build(): PathNodeData = PathNodeData(
        x = x, y = y, z = z,
        flags = flags,
        jpsDistances = jpsDistances,
        successorOffset = successorOffset,
        successorCount = successorCount,
        clusterId = clusterId
    )
}
data class PathNodeData(
    val x: Int,
    val y: Int,
    val z: Int,
    val flags: Byte,
    val jpsDistances: Int,
    val successorOffset: Int,
    val successorCount: Int,
    val clusterId: Int
) {
    val isWalkable: Boolean get() = (flags.toInt() and PathNode.FLAG_WALKABLE) != 0
    
    fun getJpsDistance(direction: Int): Int = (jpsDistances ushr (direction * 4)) and 0xF
    fun getJpsDistance(direction: Direction): Int = getJpsDistance(direction.ordinal)
}
data class L2BRHeader(
    val magic: Long = MAGIC,
    val version: Short = VERSION,
    val regionX: Short,
    val regionY: Short,
    val nodeCount: Int,
    val indexOffset: Long,
    val dataOffset: Long,
    val checksum: Long
) {
    companion object {
    const val MAGIC = 0x4C32425276350000L
    const val VERSION: Short = 5
    const val HEADER_SIZE = 64L
    const val OFFSET_MAGIC = 0L
    const val OFFSET_VERSION = 8L
    const val OFFSET_REGION_X = 10L
    const val OFFSET_REGION_Y = 12L
    const val OFFSET_NODE_COUNT = 16L
    const val OFFSET_INDEX_OFFSET = 24L
    const val OFFSET_DATA_OFFSET = 32L
    const val OFFSET_CHECKSUM = 40L
    fun readFrom(segment: MemorySegment): L2BRHeader {
        return L2BRHeader(
            magic = segment.get(ValueLayout.JAVA_LONG, OFFSET_MAGIC),
            version = segment.get(ValueLayout.JAVA_SHORT, OFFSET_VERSION),
            regionX = segment.get(ValueLayout.JAVA_SHORT, OFFSET_REGION_X),
            regionY = segment.get(ValueLayout.JAVA_SHORT, OFFSET_REGION_Y),
            nodeCount = segment.get(ValueLayout.JAVA_INT, OFFSET_NODE_COUNT),
            indexOffset = segment.get(ValueLayout.JAVA_LONG, OFFSET_INDEX_OFFSET),
            dataOffset = segment.get(ValueLayout.JAVA_LONG, OFFSET_DATA_OFFSET),
            checksum = segment.get(ValueLayout.JAVA_LONG, OFFSET_CHECKSUM)
        )
    }
    fun writeTo(segment: MemorySegment, header: L2BRHeader) {
        segment.set(ValueLayout.JAVA_LONG, OFFSET_MAGIC, header.magic)
        segment.set(ValueLayout.JAVA_SHORT, OFFSET_VERSION, header.version)
        segment.set(ValueLayout.JAVA_SHORT, OFFSET_REGION_X, header.regionX)
        segment.set(ValueLayout.JAVA_SHORT, OFFSET_REGION_Y, header.regionY)
        segment.set(ValueLayout.JAVA_INT, OFFSET_NODE_COUNT, header.nodeCount)
        segment.set(ValueLayout.JAVA_LONG, OFFSET_INDEX_OFFSET, header.indexOffset)
        segment.set(ValueLayout.JAVA_LONG, OFFSET_DATA_OFFSET, header.dataOffset)
        segment.set(ValueLayout.JAVA_LONG, OFFSET_CHECKSUM, header.checksum)
        for (i in 48 until 64) {
            segment.set(ValueLayout.JAVA_BYTE, i.toLong(), 0)
        }
    }
}
    fun isValid(): Boolean = magic == MAGIC && version == VERSION
}