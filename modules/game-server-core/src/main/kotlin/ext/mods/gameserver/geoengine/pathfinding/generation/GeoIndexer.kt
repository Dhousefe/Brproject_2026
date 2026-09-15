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
package ext.mods.gameserver.geoengine.pathfinding.generation
import ext.mods.gameserver.geoengine.pathfinding.model.*
import ext.mods.gameserver.geoengine.pathfinding.core.jps.JumpPointGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
class GeoIndexer(
    private val geoDataProvider: GeoDataProvider
) {
    
    data class IndexationResult(
        val regionX: Int,
        val regionY: Int,
        val nodes: List<IndexedNode>,
        val walkableCount: Int,
        val multilayerCount: Int,
        val durationMs: Long,
        val sourceInfo: SourceInfo
    )
    
    data class SourceInfo(
        val l2jAvailable: Boolean,
        val convDatAvailable: Boolean,
        val primarySource: DataSource,
        val mergedData: Boolean
    )
    enum class DataSource {
        L2J_ONLY,
        CONV_DAT_ONLY,
        MERGED,
        NONE
    }
    
    interface GeoDataProvider {
        
        fun getBlockType(geoX: Int, geoY: Int): BlockType
        fun getHeight(geoX: Int, geoY: Int): Short
        fun getNswe(geoX: Int, geoY: Int): Byte
        fun getLayers(geoX: Int, geoY: Int): List<LayerInfo>
        fun isLoaded(geoX: Int, geoY: Int): Boolean
        
        fun hasL2JData(regionX: Int, regionY: Int): Boolean
        fun hasConvDatData(regionX: Int, regionY: Int): Boolean
        fun getConvDatHeight(geoX: Int, geoY: Int): Short?
        fun getSourcePriority(): SourcePriority
        
        enum class SourcePriority {
            L2J_PRIORITY,
            CONV_DAT_PRIORITY,
            MERGE
        }
        
        data class LayerInfo(val height: Short, val nswe: Byte)
    }
    
    open class MultiSourceGeoProvider(
        private val geoDataPath: Path = Paths.get("data/geodata"),
        private val convDatPath: Path = Paths.get("data/geodata/convdat"),
        private val priority: GeoDataProvider.SourcePriority = GeoDataProvider.SourcePriority.CONV_DAT_PRIORITY
    ) : GeoDataProvider {
        
        private val convDatCache = ConcurrentHashMap<Pair<Int, Int>, ConvDatFile?>()
        
        protected open fun getRawBlockType(geoX: Int, geoY: Int): BlockType = BlockType.FLAT
        protected open fun getRawHeight(geoX: Int, geoY: Int): Short = 0
        protected open fun getRawNswe(geoX: Int, geoY: Int): Byte = 0x0F
        protected open fun getRawLayers(geoX: Int, geoY: Int): List<GeoDataProvider.LayerInfo> = emptyList()
        protected open fun isRawLoaded(geoX: Int, geoY: Int): Boolean = false
        
        override fun getBlockType(geoX: Int, geoY: Int): BlockType = getRawBlockType(geoX, geoY)
        
        override fun getHeight(geoX: Int, geoY: Int): Short {
            
            if (priority == GeoDataProvider.SourcePriority.CONV_DAT_PRIORITY || 
                priority == GeoDataProvider.SourcePriority.MERGE) {
                getConvDatHeight(geoX, geoY)?.let { return it }
            }
            return getRawHeight(geoX, geoY)
        }
        
        override fun getNswe(geoX: Int, geoY: Int): Byte = getRawNswe(geoX, geoY)
        
        override fun getLayers(geoX: Int, geoY: Int): List<GeoDataProvider.LayerInfo> {
            
            if (priority == GeoDataProvider.SourcePriority.CONV_DAT_PRIORITY) {
                val convHeight = getConvDatHeight(geoX, geoY)
                if (convHeight != null) {
                    
                    val rawLayers = getRawLayers(geoX, geoY)
                    if (rawLayers.isNotEmpty()) {
                        return listOf(
                            GeoDataProvider.LayerInfo(
                                height = convHeight,
                                nswe = rawLayers.first().nswe
                            )
                        ) + rawLayers.drop(1)
                    }
                    return listOf(GeoDataProvider.LayerInfo(convHeight, 0x0F.toByte()))
                }
            }
            return getRawLayers(geoX, geoY)
        }
        
        override fun isLoaded(geoX: Int, geoY: Int): Boolean = isRawLoaded(geoX, geoY)
        
        override fun hasL2JData(regionX: Int, regionY: Int): Boolean {
            val fileName = "${regionX}_${regionY}.l2j"
            return geoDataPath.resolve(fileName).toFile().exists()
        }
        
        override fun hasConvDatData(regionX: Int, regionY: Int): Boolean {
            return getConvDatFile(regionX, regionY) != null
        }
        
        override fun getConvDatHeight(geoX: Int, geoY: Int): Short? {
            
            val regionX = (geoX / GeoStructure.REGION_CELLS_X) + World.TILE_X_MIN
            val regionY = (geoY / GeoStructure.REGION_CELLS_Y) + World.TILE_Y_MIN
            
            val convFile = getConvDatFile(regionX, regionY) ?: return null
            
            val localX = geoX % GeoStructure.REGION_CELLS_X
            val localY = geoY % GeoStructure.REGION_CELLS_Y
            
            return convFile.getHeight(localX, localY)
        }
        
        override fun getSourcePriority(): GeoDataProvider.SourcePriority = priority
        
        private fun getConvDatFile(regionX: Int, regionY: Int): ConvDatFile? {
            val key = Pair(regionX, regionY)
            return convDatCache.computeIfAbsent(key) {
                loadConvDatFile(regionX, regionY)
            }
        }
        
        private fun loadConvDatFile(regionX: Int, regionY: Int): ConvDatFile? {
            
            val possibleNames = listOf(
                "${regionX}_${regionY}_conv.dat",
                "${regionX}_${regionY}.conv.dat",
                "conv_${regionX}_${regionY}.dat",
                "${regionX}_${regionY}_height.dat"
            )
            
            for (name in possibleNames) {
                val file = convDatPath.resolve(name).toFile()
                if (file.exists()) {
                    return try {
                        ConvDatFile(file)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }
            return null
        }
    }
    
    class ConvDatFile(file: java.io.File) {
        private val heights: ShortArray
        private val width: Int
        private val height: Int
        
        init {
            RandomAccessFile(file, "r").use { raf ->
                val channel = raf.channel
                val buffer = ByteBuffer.allocate(raf.length().toInt()).order(ByteOrder.LITTLE_ENDIAN)
                channel.read(buffer)
                buffer.flip()
                
                val fileSize = buffer.remaining()
                
                when {
                    fileSize >= 256 * 256 * 2 + 8 -> {
                        width = 256
                        height = 256
                        buffer.position(8) 
                    }
                    fileSize >= 128 * 128 * 2 -> {
                        width = 128
                        height = 128
                        buffer.position(0)
                    }
                    else -> {
                        val totalCells = fileSize / 2
                        val dim = kotlin.math.sqrt(totalCells.toDouble()).toInt()
                        width = dim
                        height = dim
                        buffer.position(0)
                    }
                }
                
                heights = ShortArray(width * height)
                for (i in heights.indices) {
                    if (buffer.remaining() >= 2) {
                        heights[i] = buffer.short
                    } else {
                        heights[i] = 0
                    }
                }
            }
        }
        
        fun getHeight(x: Int, y: Int): Short? {
            if (x < 0 || x >= width || y < 0 || y >= height) return null
            return heights[y * width + x]
        }
    }
    
    suspend fun indexRegion(
        regionX: Int,
        regionY: Int,
        parallelism: Int = Runtime.getRuntime().availableProcessors() / 2,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): IndexationResult = withContext(Dispatchers.Default) {
        
        val startTime = System.currentTimeMillis()
        
        val hasL2J = geoDataProvider.hasL2JData(regionX, regionY)
        val hasConvDat = geoDataProvider.hasConvDatData(regionX, regionY)
        
        if (!hasL2J && !hasConvDat) {
            throw IllegalStateException(
                "Nenhuma fonte de dados encontrada para região [$regionX, $regionY]. " +
                "Procure por: ${regionX}_${regionY}.l2j ou ${regionX}_${regionY}_conv.dat"
            )
        }
        
        val sourceInfo = SourceInfo(
            l2jAvailable = hasL2J,
            convDatAvailable = hasConvDat,
            primarySource = when {
                hasL2J && hasConvDat -> DataSource.MERGED
                hasL2J -> DataSource.L2J_ONLY
                hasConvDat -> DataSource.CONV_DAT_ONLY
                else -> DataSource.NONE
            },
            mergedData = hasL2J && hasConvDat && 
                        geoDataProvider.getSourcePriority() == GeoDataProvider.SourcePriority.MERGE
        )
        
        onProgress(
            "Fontes detectadas: L2J=$hasL2J, ConvDat=$hasConvDat", 
            0
        )
        
        val baseGeoX = (regionX - World.TILE_X_MIN) * GeoStructure.REGION_CELLS_X
        val baseGeoY = (regionY - World.TILE_Y_MIN) * GeoStructure.REGION_CELLS_Y
        
        val maxGeoX = min(baseGeoX + GeoStructure.REGION_CELLS_X, GeoStructure.GEO_CELLS_X)
        val maxGeoY = min(baseGeoY + GeoStructure.REGION_CELLS_Y, GeoStructure.GEO_CELLS_Y)
        
        val totalCells = (maxGeoX - baseGeoX) * (maxGeoY - baseGeoY)
        val processed = AtomicInteger(0)
        
        onProgress("Preparando chunks", 5)
        
        val chunkSize = 32 
        val chunks = generateChunks(baseGeoX, baseGeoY, maxGeoX, maxGeoY, chunkSize)
        
        onProgress("Indexando ${chunks.size} chunks", 10)
        
        val nodeChannel = Channel<IndexedNode>(Channel.UNLIMITED)
        
        val collectorDeferred = async {
            val allNodes = mutableListOf<IndexedNode>()
            for (node in nodeChannel) {
                allNodes.add(node)
            }
            allNodes
        }
        
        val chunkJobs = chunks.map { chunk ->
            async {
                val localNodes = mutableListOf<IndexedNode>()
                
                for (geoX in chunk.startX until chunk.endX) {
                    for (geoY in chunk.startY until chunk.endY) {
                        if (!geoDataProvider.isLoaded(geoX, geoY)) {
                            if (!hasConvDat) continue
                        }
                        
                        val blockType = geoDataProvider.getBlockType(geoX, geoY)
                        
                        when (blockType) {
                            BlockType.FLAT -> {
                                val height = geoDataProvider.getHeight(geoX, geoY)
                                val nswe = geoDataProvider.getNswe(geoX, geoY)
                                
                                localNodes.add(createNode(geoX, geoY, height, nswe, 0, blockType))
                            }
                            BlockType.COMPLEX -> {
                                val height = geoDataProvider.getHeight(geoX, geoY)
                                val nswe = geoDataProvider.getNswe(geoX, geoY)
                                
                                localNodes.add(createNode(geoX, geoY, height, nswe, 0, blockType))
                            }
                            BlockType.MULTILAYER -> {
                                val layers = geoDataProvider.getLayers(geoX, geoY)
                                layers.forEachIndexed { layerIdx, layer ->
                                    localNodes.add(
                                        createNode(
                                            geoX, geoY, 
                                            layer.height, 
                                            layer.nswe, 
                                            layerIdx, 
                                            blockType
                                        )
                                    )
                                }
                            }
                        }
                        
                        val count = processed.incrementAndGet()
                        if (count % 1000 == 0) {
                            val percent = 10 + (count * 80 / totalCells)
                            onProgress(
                                "Processando células ($count/$totalCells) [${sourceInfo.primarySource}]", 
                                percent.coerceAtMost(85)
                            )
                        }
                    }
                }
                
                localNodes.forEach { nodeChannel.send(it) }
            }
        }
        
        chunkJobs.forEach { it.await() }
        nodeChannel.close()
        
        onProgress("Finalizando coleta", 90)
        
        val allNodes = collectorDeferred.await()
        
        val nodesWithIds = allNodes.mapIndexed { index: Int, node: IndexedNode ->
            node.copy(id = index)
        }
        
        val walkableCount = nodesWithIds.count { it.isWalkable }
        val multilayerCount = nodesWithIds.count { it.blockType == BlockType.MULTILAYER }
        
        onProgress("Indexação concluída (${sourceInfo.primarySource})", 100)
        
        IndexationResult(
            regionX = regionX,
            regionY = regionY,
            nodes = nodesWithIds,
            walkableCount = walkableCount,
            multilayerCount = multilayerCount,
            durationMs = System.currentTimeMillis() - startTime,
            sourceInfo = sourceInfo
        )
    }
    
    private fun generateChunks(
        baseX: Int, baseY: Int, 
        maxX: Int, maxY: Int, 
        chunkSize: Int
    ): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        
        for (x in baseX until maxX step chunkSize) {
            for (y in baseY until maxY step chunkSize) {
                chunks.add(Chunk(
                    startX = x,
                    startY = y,
                    endX = min(x + chunkSize, maxX),
                    endY = min(y + chunkSize, maxY)
                ))
            }
        }
        
        return chunks
    }
    private fun createNode(
        geoX: Int, 
        geoY: Int, 
        height: Short, 
        nswe: Byte, 
        layer: Int, 
        blockType: BlockType
    ): IndexedNode {
        return IndexedNode(
            id = -1, 
            geoX = geoX,
            geoY = geoY,
            worldZ = height,
            nswe = nswe,
            layer = layer,
            blockType = blockType
        )
    }
    
    fun calculateJumpPoints(
        nodes: List<IndexedNode>,
        onProgress: (Int) -> Unit = {}
    ): Map<Int, Int> { 
        val generator = JumpPointGenerator(nodes)
        val results = ConcurrentHashMap<Int, Int>()
        
        nodes.parallelStream().forEach { node ->
            if (node.isWalkable) {
                val packedDistances = generator.calculateJpsDistances(node)
                results[node.id] = packedDistances
            }
            
            if (node.id % 1000 == 0) {
                onProgress((node.id * 100 / nodes.size).coerceAtMost(100))
            }
        }
        
        return results.toMap()
    }
    private data class Chunk(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int
    )
}
object GeoStructure {
    const val REGION_CELLS_X = 256
    const val REGION_CELLS_Y = 256
    const val CELL_SIZE = 16
    const val GEO_CELLS_X = 32768
    const val GEO_CELLS_Y = 32768
    
    const val CELL_FLAG_NONE: Byte = 0
    const val CELL_FLAG_N: Byte = 1
    const val CELL_FLAG_S: Byte = 2
    const val CELL_FLAG_W: Byte = 4
    const val CELL_FLAG_E: Byte = 8
}
object World {
    const val TILE_X_MIN = 16
    const val TILE_X_MAX = 26
    const val TILE_Y_MIN = 10
    const val TILE_Y_MAX = 26
}