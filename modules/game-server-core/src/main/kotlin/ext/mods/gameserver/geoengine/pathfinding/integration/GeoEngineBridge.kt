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
package ext.mods.gameserver.geoengine.pathfinding.integration
import ext.mods.gameserver.geoengine.pathfinding.core.L2BREngine
import ext.mods.gameserver.geoengine.pathfinding.dynamic.DynamicObstacleLayer
import ext.mods.gameserver.geoengine.pathfinding.model.Location
import ext.mods.gameserver.geoengine.pathfinding.model.World
import ext.mods.gameserver.geoengine.pathfinding.native.NativeStorage
import ext.mods.gameserver.geoengine.pathfinding.warmup.L2BRWarmUpService
import ext.mods.gameserver.geoengine.pathfinding.serialization.L2BRSerializer
import ext.mods.gameserver.geoengine.pathfinding.model.L2BRHeader
import ext.mods.gameserver.geoengine.pathfinding.model.PathNodeBuilder
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
class GeoEngineBridge private constructor() {
    
    private val logger = Logger.getLogger(GeoEngineBridge::class.java.name)
    
    private lateinit var legacyEngine: GeoEngineInterface
    private val l2brEngines = ConcurrentHashMap<RegionKey, L2BREngine>()
    private val basePath: Path = Paths.get("data", "pathfinder")
    
    data class BridgeConfig(
        val globalEnabled: Boolean = true,
        val fallbackToLegacy: Boolean = true,
        val metricsEnabled: Boolean = true,
        val autoReload: Boolean = false,
        val warmupOnLoad: Boolean = false,
        val maxPathfindingTimeMicros: Long = 100 
    )
    
    @Volatile
    private var config = BridgeConfig()
    
    data class Metrics(
        val l2brCalls: AtomicLong = AtomicLong(0),
        val legacyCalls: AtomicLong = AtomicLong(0),
        val l2brTotalTimeNs: AtomicLong = AtomicLong(0),
        val legacyTotalTimeNs: AtomicLong = AtomicLong(0),
        val fallbackCalls: AtomicLong = AtomicLong(0)
    )
    
    private val metrics = Metrics()
    
    companion object {
        private val INSTANCE = GeoEngineBridge()
        
        @JvmStatic
        fun getInstance(): GeoEngineBridge = INSTANCE
        
        private data class RegionKey(val x: Int, val y: Int) {
            override fun toString(): String = "[$x, $y]"
        }
    }
    
    interface GeoEngineInterface {
        fun findPath(
            ox: Int, oy: Int, oz: Int,
            tx: Int, ty: Int, tz: Int,
            playable: Boolean,
            debug: Any?
        ): List<Location>
        
        fun canMove(ox: Int, oy: Int, oz: Int, tx: Int, ty: Int, tz: Int): Boolean
        fun canSee(
            ox: Int, oy: Int, oz: Int, oheight: Double,
            tx: Int, ty: Int, tz: Int, theight: Double,
            ignore: Any?, debug: Any?
        ): Boolean
        
        fun getHeight(worldX: Int, worldY: Int, worldZ: Int): Short
        fun getValidLocation(ox: Int, oy: Int, oz: Int, tx: Int, ty: Int, tz: Int, debug: Any?): Location
        fun addGeoObject(obj: Any)
        fun removeGeoObject(obj: Any)
        fun getGeoX(worldX: Int): Int
        fun getGeoY(worldY: Int): Int
        fun getWorldX(geoX: Int): Int
        fun getWorldY(geoY: Int): Int
    }
    
    private var _initialized = false
    
    fun initialize(legacy: GeoEngineInterface) {
        this.legacyEngine = legacy
        _initialized = true
        
        basePath.toFile().mkdirs()
        
        logger.info("[GeoEngineBridge] Inicializado. Diretório: $basePath")
    }
    
    fun isInitialized(): Boolean = _initialized
    @Volatile
    private var _adminCommands: L2BRAdminCommands? = null
    
    fun getAdminCommands(): L2BRAdminCommands? {
        if (!_initialized) return null
        if (_adminCommands == null) {
            synchronized(this) {
                if (_adminCommands == null) {
                    val geo = ext.mods.gameserver.geoengine.GeoEngine.getInstance()
                    val provider = GeoEngineGeoDataProvider(geo)
                    _adminCommands = L2BRAdminCommands(this, provider)
                }
            }
        }
        return _adminCommands
    }
    fun unloadRegion(regionX: Int, regionY: Int) {
        val key = RegionKey(regionX, regionY)
        l2brEngines[key]?.close()
        l2brEngines.remove(key)
        logger.info("Região $key descarregada")
    }
    
    fun loadL2BRRegions(regions: List<Pair<Int, Int>> = discoverRegions()) {
        logger.info("[GeoEngineBridge] Carregando ${regions.size} regiões...")
        
        var loaded = 0
        var failed = 0
        val loadedRegions = mutableListOf<String>() 
        
        regions.forEach { (rx, ry) ->
            val path = basePath.resolve("${rx}_${ry}.l2br")
            
            if (path.toFile().exists()) {
                try {
                    loadL2BREngine(rx, ry, path)
                    loaded++
                    loadedRegions.add("✓ [$rx, $ry] ") 
                } catch (e: Exception) {
                    failed++
                    logger.warning("✗ Falha ao carregar L2BR para [$rx, $ry]: ${e.message}")
                }
            } else {
                logger.fine("○ Região [$rx, $ry] → GeoEngine (legacy)")
            }
        }
        
        if (loadedRegions.isNotEmpty()) {
            logger.info(loadedRegions.joinToString(", "))
        }
        
        logger.info("[GeoEngineBridge] $loaded regiões em L2BR, ${regions.size - loaded - failed} em legacy" +
                   if (failed > 0) ", $failed falhas" else "")
    }
    
    private fun loadL2BREngine(regionX: Int, regionY: Int, path: Path) {
        val file = path.toFile()
        val storage = NativeStorage.open(path, shared = true)
        val dynamicLayer = DynamicObstacleLayer(storage.nodeCount)
        
        val engine = L2BREngine(storage, dynamicLayer)
        
        if (config.warmupOnLoad) {
            runBlocking {
                val warmUpService = L2BRWarmUpService(engine, storage)
                val result = warmUpService.execute()
                
                if (result.finalLatencyP99 > 10.0) {
                    logger.warning("[$regionX, $regionY] Warm-up alerta: P99=${result.finalLatencyP99}μs")
                } else {
                    logger.info("[$regionX, $regionY] Warm-up OK: P99=${result.finalLatencyP99}μs")
                }
            }
        }
        
        l2brEngines[RegionKey(regionX, regionY)] = engine
    }
    
    private fun discoverRegions(): List<Pair<Int, Int>> {
        return (World.TILE_X_MIN..World.TILE_X_MAX).flatMap { rx ->
            (World.TILE_Y_MIN..World.TILE_Y_MAX).map { ry -> rx to ry }
        }
    }
    
    fun findPath(
        ox: Int, oy: Int, oz: Int,
        tx: Int, ty: Int, tz: Int,
        playable: Boolean = false,
        debug: Any? = null
    ): List<Location> {
        
        if (!config.globalEnabled) {
            return measureLegacy {
                legacyEngine.findPath(ox, oy, oz, tx, ty, tz, playable, debug)
            }
        }
        
        val startLoc = Location(ox, oy, oz)
        val endLoc = Location(tx, ty, tz)
        
        val startL2BR = getL2BREngineForLocation(ox, oy)
        val endL2BR = getL2BREngineForLocation(tx, ty)
        
        return if (startL2BR != null && endL2BR != null && startL2BR === endL2BR) {
            measureL2BR {
                startL2BR.findPath(startLoc, endLoc)
            }
        } else if (config.fallbackToLegacy) {
            metrics.fallbackCalls.incrementAndGet()
            measureLegacy {
                legacyEngine.findPath(ox, oy, oz, tx, ty, tz, playable, debug)
            }
        } else {
            emptyList()
        }
    }
    
    fun canMove(ox: Int, oy: Int, oz: Int, tx: Int, ty: Int, tz: Int): Boolean {
        val l2br = getL2BREngineForLocation(ox, oy)
        
        return if (l2br != null && l2br === getL2BREngineForLocation(tx, ty)) {
            measureL2BR {
                l2br.canMove(Location(ox, oy, oz), Location(tx, ty, tz))
            }
        } else {
            measureLegacy {
                legacyEngine.canMove(ox, oy, oz, tx, ty, tz)
            }
        }
    }
    
    fun canSee(
        ox: Int, oy: Int, oz: Int, oheight: Double,
        tx: Int, ty: Int, tz: Int, theight: Double,
        ignore: Any? = null,
        debug: Any? = null
    ): Boolean {
        return legacyEngine.canSee(ox, oy, oz, oheight, tx, ty, tz, theight, ignore, debug)
    }
    
    fun getHeight(worldX: Int, worldY: Int, worldZ: Int): Short {
        val l2br = getL2BREngineForLocation(worldX, worldY)
        return l2br?.getHeight(Location(worldX, worldY, worldZ))
            ?: legacyEngine.getHeight(worldX, worldY, worldZ)
    }
    
    fun getValidLocation(
        ox: Int, oy: Int, oz: Int,
        tx: Int, ty: Int, tz: Int,
        debug: Any? = null
    ): Location {
        return legacyEngine.getValidLocation(ox, oy, oz, tx, ty, tz, debug)
    }
    
    fun addGeoObject(obj: Any) {
        legacyEngine.addGeoObject(obj)
        
    }
    
    fun removeGeoObject(obj: Any) {
        legacyEngine.removeGeoObject(obj)
    }
    
    private fun getL2BREngineForLocation(worldX: Int, worldY: Int): L2BREngine? {
        val geoX = legacyEngine.getGeoX(worldX)
        val geoY = legacyEngine.getGeoY(worldY)
        
        val regionX = geoX / 256 + World.TILE_X_MIN  
        val regionY = geoY / 256 + World.TILE_Y_MIN
        
        return l2brEngines[RegionKey(regionX, regionY)]
    }
    
    private inline fun <T> measureL2BR(block: () -> T): T {
        if (!config.metricsEnabled) return block()
        
        val start = System.nanoTime()
        return block().also {
            metrics.l2brCalls.incrementAndGet()
            metrics.l2brTotalTimeNs.addAndGet(System.nanoTime() - start)
        }
    }
    
    private inline fun <T> measureLegacy(block: () -> T): T {
        if (!config.metricsEnabled) return block()
        
        val start = System.nanoTime()
        return block().also {
            metrics.legacyCalls.incrementAndGet()
            metrics.legacyTotalTimeNs.addAndGet(System.nanoTime() - start)
        }
    }
    
    fun getMetrics(): Map<String, Any> {
        val l2brCalls = metrics.l2brCalls.get()
        val legacyCalls = metrics.legacyCalls.get()
        val totalCalls = l2brCalls + legacyCalls
        
        return mapOf(
            "l2brRegions" to l2brEngines.size,
            "l2brCalls" to l2brCalls,
            "legacyCalls" to legacyCalls,
            "fallbackCalls" to metrics.fallbackCalls.get(),
            "l2brPercentage" to if (totalCalls > 0) (l2brCalls * 100 / totalCalls) else 0,
            "avgL2BRTimeUs" to if (l2brCalls > 0) metrics.l2brTotalTimeNs.get() / l2brCalls / 1000 else 0,
            "avgLegacyTimeUs" to if (legacyCalls > 0) metrics.legacyTotalTimeNs.get() / legacyCalls / 1000 else 0,
            "engines" to l2brEngines.map { (key, engine) ->
                mapOf(
                    "region" to key.toString(),
                    "nodes" to engine.storage.nodeCount,
                    "metrics" to engine.getMetrics()
                )
            }
        )
    }
    fun verifyL2BRFile(regionX: Int, regionY: Int): String {
        val filePath = java.nio.file.Paths.get("data/pathfinder/${regionX}_${regionY}.l2br")
        
        if (!filePath.toFile().exists()) {
            return "ARQUIVO_NAO_ENCONTRADO"
        }
        
        return try {
            java.io.RandomAccessFile(filePath.toFile(), "r").use { raf ->
                val buffer = ByteArray(64)
                raf.read(buffer)
                
                val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
                val magic = bb.long
                val version = bb.short
                
                when {
                    magic != ext.mods.gameserver.geoengine.pathfinding.model.L2BRHeader.MAGIC -> 
                        "MAGIC_INVALIDO (esperado: ${ext.mods.gameserver.geoengine.pathfinding.model.L2BRHeader.MAGIC}, obtido: $magic)"
                    version != ext.mods.gameserver.geoengine.pathfinding.model.L2BRHeader.VERSION -> 
                        "VERSAO_INVALIDA (esperado: ${ext.mods.gameserver.geoengine.pathfinding.model.L2BRHeader.VERSION}, obtido: $version)"
                    else -> "OK - Magic e Versão corretos"
                }
            }
        } catch (e: Exception) {
            "ERRO_LEITURA: ${e.message}"
        }
    }
    
    fun reloadRegion(regionX: Int, regionY: Int) {
        val filePath = java.nio.file.Paths.get("data/pathfinder/${regionX}_${regionY}.l2br")
        
        println("[L2BR] Tentando carregar: $filePath")
        
        if (!filePath.toFile().exists()) {
            throw java.io.FileNotFoundException("Arquivo não encontrado: $filePath")
        }
        
        try {
            val serializer = ext.mods.gameserver.geoengine.pathfinding.serialization.L2BRSerializer()
            val region = serializer.deserialize(filePath)
            
            println("[L2BR] Região ${region.regionX}_${region.regionY} carregada: ${region.nodes.size} nós")
            
            val storageSize = 64L + (region.nodes.size * 32L) + 4096 
            
            val storage = ext.mods.gameserver.geoengine.pathfinding.native.NativeStorage.create(
                filePath, 
                ext.mods.gameserver.geoengine.pathfinding.model.L2BRHeader(
                    regionX = region.regionX.toShort(),
                    regionY = region.regionY.toShort(),
                    nodeCount = region.nodes.size,
                    indexOffset = 64L,
                    dataOffset = 64L + (region.nodes.size * 32L),
                    checksum = 0L
                ),
                storageSize 
            )
            
            val builder = ext.mods.gameserver.geoengine.pathfinding.model.PathNodeBuilder()
            region.nodes.values.forEachIndexed { index, node ->
                builder.setPosition(node.x, node.y, node.z.toShort())
                builder.setWalkable(node.isWalkable)
                builder.jpsDistances = 0 
                builder.successorOffset = 0
                builder.successorCount = 0
                builder.clusterId = 0
                
                val nodeOffset = 64L + (index * 32L)
                builder.writeTo(storage.segment, nodeOffset)
            }
            
            val engine = ext.mods.gameserver.geoengine.pathfinding.core.L2BREngine(
                storage, 
                ext.mods.gameserver.geoengine.pathfinding.dynamic.DynamicObstacleLayer(region.nodes.size)
            )
            
            val key = RegionKey(regionX, regionY)
            l2brEngines[key] = engine
            
            println("[L2BR] Região [$regionX, $regionY] carregada com sucesso!")
            
        } catch (e: Exception) {
            println("[L2BR] ERRO ao carregar região: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    fun updateConfig(newConfig: BridgeConfig) {
        this.config = newConfig
        logger.info("Configuração atualizada: $newConfig")
    }
    
    fun getConfig(): BridgeConfig = config
    
    fun unloadAll() {
        l2brEngines.values.forEach { it.close() }
        l2brEngines.clear()
        logger.info("Todas as regiões L2BR descarregadas")
    }
}
object GeoStructure {
    const val REGION_CELLS_X = 256
    const val REGION_CELLS_Y = 256
}