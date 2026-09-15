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
data class Location(
    val x: Int,
    val y: Int,
    val z: Int
) {
    
    fun toGeo(): GeoLocation {
        return GeoLocation(
            x = (x - World.TILE_X_MIN * GeoStructure.REGION_SIZE) / GeoStructure.CELL_SIZE,
            y = (y - World.TILE_Y_MIN * GeoStructure.REGION_SIZE) / GeoStructure.CELL_SIZE,
            z = (z / 16).toShort()
        )
    }
    
    fun distance2D(other: Location): Double {
        val dx = (x - other.x).toDouble()
        val dy = (y - other.y).toDouble()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    
    fun distanceManhattan(other: Location): Int {
        return kotlin.math.abs(x - other.x) + kotlin.math.abs(y - other.y) + kotlin.math.abs(z - other.z)
    }
    companion object {
        val ZERO = Location(0, 0, 0)
    }
}
data class GeoLocation(
    val x: Int,
    val y: Int,
    val z: Short
) {
    
    fun toWorld(): Location {
        return Location(
            x = x * GeoStructure.CELL_SIZE + World.TILE_X_MIN * GeoStructure.REGION_SIZE,
            y = y * GeoStructure.CELL_SIZE + World.TILE_Y_MIN * GeoStructure.REGION_SIZE,
            z = z * 16
        )
    }
    
    fun getRegion(): Pair<Int, Int> {
        val regionX = x / GeoStructure.REGION_CELLS_X + World.TILE_X_MIN
        val regionY = y / GeoStructure.REGION_CELLS_Y + World.TILE_Y_MIN
        return regionX to regionY
    }
    
    fun getNodeIdInRegion(): Int {
        val localX = x % GeoStructure.REGION_CELLS_X
        val localY = y % GeoStructure.REGION_CELLS_Y
        return localY * GeoStructure.REGION_CELLS_X + localX
    }
}
object World {
    const val TILE_X_MIN = 16
    const val TILE_X_MAX = 26
    const val TILE_Y_MIN = 10
    const val TILE_Y_MAX = 26
    const val TILE_SIZE = 32768
}
object GeoStructure {
    const val CELL_SIZE = 16
    const val REGION_CELLS_X = 256
    const val REGION_CELLS_Y = 256
    const val REGION_SIZE = REGION_CELLS_X * CELL_SIZE
    const val GEO_CELLS_X = 32768
    const val GEO_CELLS_Y = 32768
    
    const val CELL_FLAG_NONE: Byte = 0
    const val CELL_FLAG_N: Byte = 1
    const val CELL_FLAG_S: Byte = 2
    const val CELL_FLAG_W: Byte = 4
    const val CELL_FLAG_E: Byte = 8
    const val CELL_FLAG_ALL: Byte = 15
    
    const val BLOCKTYPE_FLAT: Byte = 0
    const val BLOCKTYPE_COMPLEX: Byte = 1
    const val BLOCKTYPE_MULTILAYER: Byte = 2
}
data class IndexedNode(
    val id: Int,
    val geoX: Int,
    val geoY: Int,
    val worldZ: Short,
    val nswe: Byte,
    val layer: Int = 0,
    val blockType: BlockType
) {
    val isWalkable: Boolean
        get() = nswe != GeoStructure.CELL_FLAG_NONE
    
    val worldX: Int
        get() = geoX * GeoStructure.CELL_SIZE + World.TILE_X_MIN * GeoStructure.REGION_SIZE
    
    val worldY: Int
        get() = geoY * GeoStructure.CELL_SIZE + World.TILE_Y_MIN * GeoStructure.REGION_SIZE
    
    fun toLocation(): Location = Location(worldX, worldY, worldZ.toInt())
    fun toGeoLocation(): GeoLocation = GeoLocation(geoX, geoY, worldZ)
}
enum class BlockType {
    FLAT,
    COMPLEX,
    MULTILAYER
}
data class JumpPoint(
    val targetNodeId: Int,
    val direction: Direction,
    val cost: Int,
    val flags: Byte = 0
)
enum class Direction(val dx: Int, val dy: Int, val cost: Int) {
    N(0, -1, 10),
    NE(1, -1, 14),
    E(1, 0, 10),
    SE(1, 1, 14),
    S(0, 1, 10),
    SW(-1, 1, 14),
    W(-1, 0, 10),
    NW(-1, -1, 14);
    
    companion object {
        fun fromIndex(index: Int): Direction = entries[index]
        fun fromDelta(dx: Int, dy: Int): Direction? = 
            entries.find { it.dx == dx && it.dy == dy }
    }
}
data class PathResult(
    val path: List<Location>,
    val nodesExplored: Int,
    val timeMicros: Long,
    val success: Boolean
) {
    companion object {
        fun failure() = PathResult(emptyList(), 0, 0, false)
    }
}
class PerformanceMetrics {
    private val times = mutableListOf<Long>()
    
    fun record(nanos: Long) {
        times.add(nanos)
    }
    
    fun getPercentile(p: Double): Double {
        if (times.isEmpty()) return 0.0
        val sorted = times.sorted()
        val index = (p / 100.0 * sorted.size).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index].toDouble()
    }
    
    fun getAverage(): Double {
        if (times.isEmpty()) return 0.0
        return times.average()
    }
    
    fun getMin(): Long = times.minOrNull() ?: 0
    fun getMax(): Long = times.maxOrNull() ?: 0
}