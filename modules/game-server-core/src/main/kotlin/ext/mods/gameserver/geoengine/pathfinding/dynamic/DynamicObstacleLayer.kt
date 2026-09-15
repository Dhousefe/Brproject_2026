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
package ext.mods.gameserver.geoengine.pathfinding.dynamic
import ext.mods.gameserver.geoengine.pathfinding.model.GeoLocation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.locks.StampedLock
class DynamicObstacleLayer(
    private val totalNodes: Int,
    private val chunkSize: Int = 64
) {
    private val blockedBits = AtomicLongArray((totalNodes + chunkSize - 1) / chunkSize)
    
    private val activeObjects = ConcurrentHashMap<String, DynamicObject>()
    
    private val coordLock = StampedLock()
    
    fun isBlocked(nodeId: Int): Boolean {
        if (nodeId < 0 || nodeId >= totalNodes) return true 
        val chunkIdx = nodeId ushr 6 
        val bitIdx = nodeId and 0x3F 
        return (blockedBits[chunkIdx] ushr bitIdx) and 1L != 0L
    }
    
    fun isBlocked(geo: GeoLocation, nodeIdInRegion: Int): Boolean {
        return isBlocked(nodeIdInRegion)
    }
    
    fun blockNode(nodeId: Int): Boolean {
        require(nodeId in 0 until totalNodes) { "NodeId fora de bounds: $nodeId" }
        val chunkIdx = nodeId ushr 6
        val bitIdx = nodeId and 0x3F
        val mask = 1L shl bitIdx
        var current: Long
        do {
            current = blockedBits[chunkIdx]
            if (current and mask != 0L) return false 
        } while (!blockedBits.compareAndSet(chunkIdx, current, current or mask))
        return true
    }
    
    fun unblockNode(nodeId: Int): Boolean {
        require(nodeId in 0 until totalNodes)
        val chunkIdx = nodeId ushr 6
        val bitIdx = nodeId and 0x3F
        val mask = 1L shl bitIdx
        var current: Long
        do {
            current = blockedBits[chunkIdx]
            if (current and mask == 0L) return false 
        } while (!blockedBits.compareAndSet(chunkIdx, current, current and mask.inv()))
        return true
    }
    
    fun isPathClear(nodeIds: IntArray): Boolean {
        for (nodeId in nodeIds) {
            if (isBlocked(nodeId)) return false
        }
        return true
    }
    
    fun registerObject(objectId: String, nodeIds: IntArray, objectType: ObjectType = ObjectType.DOOR) {
        val stamp = coordLock.writeLock()
        try {
            nodeIds.forEach { blockNode(it) }
            
            activeObjects[objectId] = DynamicObject(
                id = objectId,
                nodeIds = nodeIds,
                type = objectType,
                blocked = true
            )
        } finally {
            coordLock.unlockWrite(stamp)
        }
    }
    
    fun unregisterObject(objectId: String) {
        val stamp = coordLock.writeLock()
        try {
            val obj = activeObjects.remove(objectId) ?: return
            obj.nodeIds.forEach { unblockNode(it) }
        } finally {
            coordLock.unlockWrite(stamp)
        }
    }
    
    fun toggleObject(objectId: String): Boolean {
        val stamp = coordLock.writeLock()
        try {
            val obj = activeObjects[objectId] ?: return false
            
            if (obj.blocked) {
                obj.nodeIds.forEach { unblockNode(it) }
                activeObjects[objectId] = obj.copy(blocked = false)
            } else {
                obj.nodeIds.forEach { blockNode(it) }
                activeObjects[objectId] = obj.copy(blocked = true)
            }
            
            return true
        } finally {
            coordLock.unlockWrite(stamp)
        }
    }
    
    fun getObjectStates(): Map<String, DynamicObject> {
        val stamp = coordLock.tryOptimisticRead()
        val copy = activeObjects.toMap()
        if (!coordLock.validate(stamp)) {
            val newStamp = coordLock.readLock()
            try {
                return activeObjects.toMap()
            } finally {
                coordLock.unlockRead(newStamp)
            }
        }
        return copy
    }
    
    fun clearAll() {
        val stamp = coordLock.writeLock()
        try {
            for (i in 0 until blockedBits.length()) {
                blockedBits[i] = 0L
            }
            activeObjects.clear()
        } finally {
            coordLock.unlockWrite(stamp)
        }
    }
    
    fun getStats(): LayerStats {
        var blockedCount = 0
        for (i in 0 until blockedBits.length()) {
            blockedCount += blockedBits[i].countOneBits()
        }
        
        return LayerStats(
            totalNodes = totalNodes,
            blockedNodes = blockedCount,
            activeObjects = activeObjects.size,
            memoryBytes = blockedBits.length() * 8L + activeObjects.size * 64L 
        )
    }
    data class DynamicObject(
        val id: String,
        val nodeIds: IntArray,
        val type: ObjectType,
        val blocked: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DynamicObject) return false
            return id == other.id
        }
        override fun hashCode(): Int = id.hashCode()
    }
    enum class ObjectType {
        DOOR,
        WALL,
        SIEGE_WEAPON,
        NPC_BARRIER,
        TEMPORARY
    }
    data class LayerStats(
        val totalNodes: Int,
        val blockedNodes: Int,
        val activeObjects: Int,
        val memoryBytes: Long
    ) {
        val blockedPercentage: Double get() = (blockedNodes.toDouble() / totalNodes) * 100
    }
}
interface GeoObjectAdapter {
    fun getObjectId(): String
    fun getGeoX(): Int
    fun getGeoY(): Int
    fun getGeoZ(): Short
    fun getWidth(): Int
    fun getHeight(): Int
    fun isSolid(): Boolean
}
class DynamicObstacleAdapter(
    private val layer: DynamicObstacleLayer,
    private val regionOffsetX: Int,
    private val regionOffsetY: Int
) {
    
    fun geoToNodeId(geoX: Int, geoY: Int): Int {
        val localX = geoX - regionOffsetX
        val localY = geoY - regionOffsetY
        return localY * 256 + localX 
    }
    
    fun registerGeoObject(obj: GeoObjectAdapter) {
        val nodeIds = calculateCoveredNodes(obj)
        layer.registerObject(obj.getObjectId(), nodeIds, DynamicObstacleLayer.ObjectType.DOOR)
    }
    
    fun removeGeoObject(obj: GeoObjectAdapter) {
        layer.unregisterObject(obj.getObjectId())
    }
    private fun calculateCoveredNodes(obj: GeoObjectAdapter): IntArray {
        val nodes = mutableListOf<Int>()
        val startX = obj.getGeoX()
        val startY = obj.getGeoY()
        val width = obj.getWidth()
        val height = obj.getHeight()
        for (dx in 0 until width) {
            for (dy in 0 until height) {
                nodes.add(geoToNodeId(startX + dx, startY + dy))
            }
        }
        return nodes.toIntArray()
    }
}