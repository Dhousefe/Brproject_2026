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
package ext.mods.gameserver.geoengine.pathfinding.warmup
import ext.mods.gameserver.geoengine.pathfinding.core.L2BREngine
import ext.mods.gameserver.geoengine.pathfinding.model.Location
import ext.mods.gameserver.geoengine.pathfinding.model.PerformanceMetrics
import ext.mods.gameserver.geoengine.pathfinding.native.NativeStorage
import kotlinx.coroutines.*
import java.lang.foreign.ValueLayout
import java.util.concurrent.ThreadLocalRandom
import java.util.logging.Logger
class L2BRWarmUpService(
    private val engine: L2BREngine,
    private val storage: NativeStorage,
    private val config: WarmUpConfig = WarmUpConfig.DEFAULT
) {
    private val logger = Logger.getLogger(L2BRWarmUpService::class.java.name)
    
    data class WarmUpConfig(
        val memoryPrefetchEnabled: Boolean = true,
        val jitIterations: Int = 100_000,
        val branchTrainingIterations: Int = 50_000,
        val validationIterations: Int = 10_000,
        val parallelWorkers: Int = Runtime.getRuntime().availableProcessors(),
        val targetLatencyP99Micros: Double = 5.0
    ) {
        companion object {
            val DEFAULT = WarmUpConfig()
        }
    }
    
    data class WarmUpResult(
        val totalTimeMs: Long,
        val memoryPhaseMs: Long,
        val jitPhaseMs: Long,
        val validationPhaseMs: Long,
        val finalLatencyP50: Double,
        val finalLatencyP99: Double,
        val finalLatencyP999: Double,
        val success: Boolean
    )
    
    suspend fun execute(): WarmUpResult = withContext(Dispatchers.Default) {
        val totalStart = System.currentTimeMillis()
        logger.info("[WarmUp] Iniciando warm-up para região...")
        
        val memoryTime = if (config.memoryPrefetchEnabled) {
            logger.info("[WarmUp] Fase 1/3: Memory prefetch...")
            measureTimeMillis { executeMemoryPrefetch() }
        } else 0
        
        logger.info("[WarmUp] Fase 2/3: JIT compilation (${config.jitIterations} iterações)...")
        val jitTime = measureTimeMillis { executeJitWarmUp() }
        
        logger.info("[WarmUp] Fase 3/3: Validação de performance...")
        val validationTime = measureTimeMillis { executeValidation() }
        
        val totalTime = System.currentTimeMillis() - totalStart
        
        val metrics = engine.getMetrics()
        val p50 = metrics.p50Estimate.toDouble()
        val p99 = metrics.p99Estimate.toDouble()
        val p999 = calculateP999()
        
        val success = p99 <= config.targetLatencyP99Micros
        
        val result = WarmUpResult(
            totalTimeMs = totalTime,
            memoryPhaseMs = memoryTime,
            jitPhaseMs = jitTime,
            validationPhaseMs = validationTime,
            finalLatencyP50 = p50,
            finalLatencyP99 = p99,
            finalLatencyP999 = p999,
            success = success
        )
        
        logResult(result)
        result
    }
    
    private fun executeMemoryPrefetch() {
        val nodeCount = storage.nodeCount
        val pageSize = 4096
        val nodesPerPage = pageSize / 32 
        
        var touched = 0
        for (i in 0 until nodeCount step nodesPerPage) {
            
            val segment = storage.getNodeSegment(i)
            segment.get(ValueLayout.JAVA_BYTE, 0L) 
            touched++
        }
        
        logger.info("[WarmUp] Memory prefetch: $touched páginas tocadas")
    }
    
    private fun executeJitWarmUp() {
        val random = ThreadLocalRandom.current()
        val bounds = calculateMapBounds()
        
        val iterations = config.jitIterations
        val progressInterval = iterations / 10
        
        repeat(iterations) { i ->
            val start = randomLocation(random, bounds)
            val end = randomLocation(random, bounds)
            
            engine.findPath(start, end)
            
            if (i % progressInterval == 0 && i > 0) {
                val progress = (i * 100 / iterations)
                logger.fine("[WarmUp] JIT progress: $progress%")
            }
        }
        
        System.gc()
        Thread.sleep(100)
    }
    
    private fun executeValidation() {
        val random = ThreadLocalRandom.current()
        val bounds = calculateMapBounds()
        val metrics = PerformanceMetrics()
        
        repeat(config.validationIterations) {
            val start = randomLocation(random, bounds)
            val end = randomLocation(random, bounds)
            
            val latency = measureNanoTime {
                engine.findPath(start, end)
            }
            
            metrics.record(latency)
        }
        
        val p50 = metrics.getPercentile(50.0) / 1000.0
        val p99 = metrics.getPercentile(99.0) / 1000.0
        val p999 = metrics.getPercentile(99.9) / 1000.0
        
        logger.info("[WarmUp] Validação - P50: ${p50}μs, P99: ${p99}μs, P99.9: ${p999}μs")
    }
    
    suspend fun executeParallel(): WarmUpResult = coroutineScope {
        val workers = config.parallelWorkers
        logger.info("[WarmUp] Warm-up paralelo com $workers workers...")
        
        val jobs = (0 until workers).map { workerId ->
            async(Dispatchers.Default) {
                val random = ThreadLocalRandom.current()
                val bounds = calculateMapBounds()
                val iterationsPerWorker = config.jitIterations / workers
                
                repeat(iterationsPerWorker) {
                    val start = randomLocation(random, bounds)
                    val end = randomLocation(random, bounds)
                    engine.findPath(start, end)
                }
                
                workerId
            }
        }
        
        jobs.awaitAll()
        
        executeValidation()
        
        val metrics = engine.getMetrics()
        WarmUpResult(
            totalTimeMs = 0, 
            memoryPhaseMs = 0,
            jitPhaseMs = 0,
            validationPhaseMs = 0,
            finalLatencyP50 = metrics.p50Estimate.toDouble(),
            finalLatencyP99 = metrics.p99Estimate.toDouble(),
            finalLatencyP999 = calculateP999(),
            success = metrics.p99Estimate.toDouble() <= config.targetLatencyP99Micros
        )
    }
    
    private fun calculateMapBounds(): MapBounds {
        
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        
        storage.forEachNode { _, node ->
            val loc = node.toLocation()
            minX = minOf(minX, loc.x)
            minY = minOf(minY, loc.y)
            maxX = maxOf(maxX, loc.x)
            maxY = maxOf(maxY, loc.y)
        }
        
        return MapBounds(minX, minY, maxX, maxY)
    }
    
    private fun randomLocation(random: ThreadLocalRandom, bounds: MapBounds): Location {
        return Location(
            x = random.nextInt(bounds.minX, bounds.maxX + 1),
            y = random.nextInt(bounds.minY, bounds.maxY + 1),
            z = 0 
        )
    }
    
    private fun calculateP999(): Double {
        
        val metrics = engine.getMetrics()
        
        return metrics.p99Estimate * 1.5 
    }
    
    private fun logResult(result: WarmUpResult) {
        if (result.success) {
            logger.info("[WarmUp] SUCESSO - P99: ${result.finalLatencyP99}μs (target: ${config.targetLatencyP99Micros}μs)")
        } else {
            logger.warning("[WarmUp] ALERTA - P99: ${result.finalLatencyP99}μs excede target (${config.targetLatencyP99Micros}μs)")
        }
        logger.info("[WarmUp] Tempo total: ${result.totalTimeMs}ms (Memory: ${result.memoryPhaseMs}ms, JIT: ${result.jitPhaseMs}ms)")
    }
    
    private data class MapBounds(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int
    )
    
    private inline fun measureTimeMillis(block: () -> Unit): Long {
        val start = System.currentTimeMillis()
        block()
        return System.currentTimeMillis() - start
    }
    
    private inline fun measureNanoTime(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }
}