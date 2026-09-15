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
package ext.mods.gameserver.geoengine.pathfinding.core
import ext.mods.gameserver.geoengine.pathfinding.core.jps.JPSPlusCalculator
import ext.mods.gameserver.geoengine.pathfinding.core.spatial.SpatialIndexAdapter
import ext.mods.gameserver.geoengine.pathfinding.dynamic.DynamicObstacleLayer
import ext.mods.gameserver.geoengine.pathfinding.model.*
import ext.mods.gameserver.geoengine.pathfinding.native.NativeStorage
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger
class L2BREngine(
    val storage: NativeStorage,
    val dynamicLayer: DynamicObstacleLayer
) : AutoCloseable {
    
    private val logger = Logger.getLogger(L2BREngine::class.java.name)
    
    private val calculator = JPSPlusCalculator(storage, dynamicLayer)
    private val spatialIndex = SpatialIndexAdapter()
    
    private val metrics = EngineMetrics()
    
    @Volatile
    private var initialized = false
    
    private val validNodeCount: Int
    
    init {
        try {
            val stats = storage.getMemoryStats()
            validNodeCount = stats.nodeCount
            
            if (validNodeCount <= 0) {
                throw IllegalStateException("NodeCount inválido no header: $validNodeCount")
            }
            
            val expectedIndexSize = validNodeCount * 32L  
            val expectedMinFileSize = 64L + expectedIndexSize  
            
            if (stats.mappedSize < expectedMinFileSize) {
                throw IllegalStateException(
                    "Arquivo mapeado é menor que o esperado. " +
                    "NodeCount: $validNodeCount, " +
                    "Esperado mínimo: $expectedMinFileSize bytes, " +
                    "Mapeado: ${stats.mappedSize} bytes. " +
                    "Verifique se o NativeStorage está usando DataOffset correto."
                )
            }
            
            buildSpatialIndex()
            initialized = true
            
        } catch (e: Exception) {
            logger.severe("[L2BR] Falha crítica na inicialização: ${e.message}")
            close() 
            throw e
        }
    }
    
    fun findPath(start: Location, goal: Location): List<Location> {
        if (!initialized) throw IllegalStateException("Engine não inicializado")
        
        val startTime = System.nanoTime()
        
        if (start == goal) return listOf(start)
        
        val startNodeId = spatialIndex.findNearest(start)
        val goalNodeId = spatialIndex.findNearest(goal)
        
        if (startNodeId == null || goalNodeId == null) {
            metrics.recordFailure()
            return emptyList()
        }
        
        if (startNodeId >= validNodeCount || goalNodeId >= validNodeCount) {
            logger.warning("[L2BR] NodeId fora dos limites: start=$startNodeId, goal=$goalNodeId, max=$validNodeCount")
            metrics.recordFailure()
            return emptyList()
        }
        
        if (dynamicLayer.isBlocked(startNodeId) || dynamicLayer.isBlocked(goalNodeId)) {
            metrics.recordFailure()
            return emptyList()
        }
        
        val path = calculator.findPath(start, goal)
        metrics.recordSuccess(System.nanoTime() - startTime)
        
        return path
    }
    
    fun canMove(start: Location, goal: Location): Boolean {
        if (!initialized) return false
        
        val startNodeId = spatialIndex.findNearest(start) ?: return false
        val goalNodeId = spatialIndex.findNearest(goal) ?: return false
        
        if (startNodeId == goalNodeId) return true
        
        if (startNodeId >= validNodeCount || goalNodeId >= validNodeCount) return false
        
        val startNode = storage.getNode(startNodeId)
        val goalNode = storage.getNode(goalNodeId)
        
        if (!startNode.isWalkable || !goalNode.isWalkable) return false
        if (dynamicLayer.isBlocked(startNodeId) || dynamicLayer.isBlocked(goalNodeId)) return false
        
        return true
    }
    
    fun getHeight(location: Location): Short {
        val nodeId = spatialIndex.findNearest(location) ?: return 0
        if (nodeId >= validNodeCount) return 0
        
        return storage.getNode(nodeId).toLocation().z.toShort()
    }
    
    fun canSee(from: Location, to: Location): Boolean {
        val fromNodeId = spatialIndex.findNearest(from) ?: return false
        val toNodeId = spatialIndex.findNearest(to) ?: return false
        
        if (fromNodeId >= validNodeCount || toNodeId >= validNodeCount) return false
        
        val fromGeo = from.toGeo()
        val toGeo = to.toGeo()
        
        val dx = kotlin.math.abs(toGeo.x - fromGeo.x)
        val dy = kotlin.math.abs(toGeo.y - fromGeo.y)
        val sx = if (fromGeo.x < toGeo.x) 1 else -1
        val sy = if (fromGeo.y < toGeo.y) 1 else -1
        var err = dx - dy
        
        var x = fromGeo.x
        var y = fromGeo.y
        
        while (x != toGeo.x || y != toGeo.y) {
            val nodeId = spatialIndex.findAt(GeoLocation(x, y, 0))
            if (nodeId != null) {
                if (nodeId < validNodeCount) {
                    val node = storage.getNode(nodeId)
                    if (!node.isWalkable || dynamicLayer.isBlocked(nodeId)) {
                        return false
                    }
                }
            }
            
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
        
        return true
    }
    fun registerDynamicObject(objectId: String, locations: List<Location>, type: DynamicObstacleLayer.ObjectType) {
        val nodeIds = locations.mapNotNull { spatialIndex.findNearest(it) }
            .filter { it < validNodeCount }  
            .toIntArray()
            
        if (nodeIds.isNotEmpty()) {
            dynamicLayer.registerObject(objectId, nodeIds, type)
        }
    }
    fun unregisterDynamicObject(objectId: String) {
        dynamicLayer.unregisterObject(objectId)
    }
    fun toggleDynamicObject(objectId: String): Boolean {
        return dynamicLayer.toggleObject(objectId)
    }
    fun getMetrics(): EngineMetricsSnapshot = metrics.snapshot()
    fun getStorageStats(): NativeStorage.MemoryStats = storage.getMemoryStats()
    fun getDynamicLayerStats(): DynamicObstacleLayer.LayerStats = dynamicLayer.getStats()
    fun contains(location: Location): Boolean {
        val nodeId = spatialIndex.findNearest(location)
        return nodeId != null && nodeId < validNodeCount
    }
    fun getNodeDebugInfo(location: Location): String {
        val nodeId = spatialIndex.findNearest(location) ?: return "Nó não encontrado"
        if (nodeId >= validNodeCount) return "NodeId ($nodeId) fora dos limites (max: $validNodeCount)"
        
        val node = storage.getNode(nodeId)
        
        return buildString {
            appendLine("Node ID: $nodeId (max válido: ${validNodeCount - 1})")
            appendLine("Position: (${node.x}, ${node.y}, ${node.z})")
            appendLine("Walkable: ${node.isWalkable}")
            appendLine("Dynamic: ${node.hasDynamicCheck}")
            appendLine("Blocked: ${dynamicLayer.isBlocked(nodeId)}")
            appendLine("JPS Distances: ${(0..7).map { node.getJpsDistance(it) }}")
        }
    }
    
    private fun buildSpatialIndex() {
        val entries = mutableListOf<Pair<Int, GeoLocation>>()
        var invalidCount = 0
        
        try {
            storage.forEachNode { id, node ->
                if (id < 0 || id >= validNodeCount) {
                    invalidCount++
                    if (invalidCount <= 5) {  
                        logger.warning("[L2BR] Ignorando nó com ID inválido: $id (fora de 0..${validNodeCount - 1})")
                    }
                    return@forEachNode  
                }
                
                try {
                    entries.add(id to node.toGeoLocation())
                } catch (e: Exception) {
                    invalidCount++
                    logger.warning("[L2BR] Erro ao processar nó $id: ${e.message}")
                }
            }
            
            if (invalidCount > 0) {
                logger.warning("[L2BR] Total de nós inválidos ignorados: $invalidCount")
            }
            
            entries.forEach { (id, geo) ->
                spatialIndex.insertNode(geo.x, geo.y, id)
            }
            
        } catch (e: Exception) {
            throw IllegalStateException("Falha ao construir índice espacial: ${e.message}", e)
        }
    }
    override fun close() {
        initialized = false
        try {
            storage.close()
        } catch (e: Exception) {
            logger.warning("[L2BR] Erro ao fechar storage: ${e.message}")
        }
    }
    private class EngineMetrics {
        private val totalCalls = AtomicLong(0)
        private val successfulCalls = AtomicLong(0)
        private val failedCalls = AtomicLong(0)
        private val totalTimeNanos = AtomicLong(0)
        private val timeHistogram = LongArray(10) { 0 }
        
        fun recordSuccess(durationNanos: Long) {
            totalCalls.incrementAndGet()
            successfulCalls.incrementAndGet()
            totalTimeNanos.addAndGet(durationNanos)
            
            val micros = durationNanos / 1000
            val bin = when {
                micros < 1 -> 0
                micros < 5 -> 1
                micros < 10 -> 2
                micros < 50 -> 3
                micros < 100 -> 4
                micros < 500 -> 5
                micros < 1000 -> 6
                micros < 5000 -> 7
                micros < 10000 -> 8
                else -> 9
            }
            timeHistogram[bin]++
        }
        
        fun recordFailure() {
            totalCalls.incrementAndGet()
            failedCalls.incrementAndGet()
        }
        
        fun snapshot(): EngineMetricsSnapshot {
            val total = totalCalls.get()
            val success = successfulCalls.get()
            val avgTime = if (success > 0) totalTimeNanos.get() / success else 0
            
            return EngineMetricsSnapshot(
                totalCalls = total,
                successfulCalls = success,
                failedCalls = failedCalls.get(),
                averageTimeMicros = avgTime / 1000.0,
                successRate = if (total > 0) (success * 100.0 / total) else 0.0,
                timeDistribution = timeHistogram.copyOf()
            )
        }
    }
    
    data class EngineMetricsSnapshot(
        val totalCalls: Long,
        val successfulCalls: Long,
        val failedCalls: Long,
        val averageTimeMicros: Double,
        val successRate: Double,
        val timeDistribution: LongArray
    ) {
        val p50Estimate: Long get() = calculatePercentile(50)
        val p99Estimate: Long get() = calculatePercentile(99)
        
        private fun calculatePercentile(percentile: Int): Long {
            val total = timeDistribution.sum()
            if (total == 0L) return 0
            
            val target = total * percentile / 100
            var cumulative = 0L
            
            val thresholds = longArrayOf(1, 5, 10, 50, 100, 500, 1000, 5000, 10000, Long.MAX_VALUE)
            
            timeDistribution.forEachIndexed { index, count ->
                cumulative += count
                if (cumulative >= target) {
                    return thresholds[index]
                }
            }
            return thresholds.last()
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EngineMetricsSnapshot) return false
            return totalCalls == other.totalCalls && 
                   successfulCalls == other.successfulCalls &&
                   failedCalls == other.failedCalls
        }
        
        override fun hashCode(): Int {
            var result = totalCalls.hashCode()
            result = 31 * result + successfulCalls.hashCode()
            result = 31 * result + failedCalls.hashCode()
            return result
        }
    }
}