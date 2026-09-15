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
import ext.mods.gameserver.geoengine.GeoEngine
import ext.mods.gameserver.geoengine.pathfinding.generation.GeoIndexer
import ext.mods.gameserver.geoengine.pathfinding.model.BlockType
import java.nio.file.Path
import java.nio.file.Paths
class GeoEngineGeoDataProvider(
    private val geoEngine: GeoEngine,
    private val geoDataPath: Path = Paths.get("data/geodata"),
    private val convDatPath: Path = Paths.get("data/geodata"),
    private val priority: GeoIndexer.GeoDataProvider.SourcePriority = GeoIndexer.GeoDataProvider.SourcePriority.CONV_DAT_PRIORITY
) : GeoIndexer.GeoDataProvider {
    
    private val convDatCache = mutableMapOf<Pair<Int, Int>, ConvDatFile?>()
    
    override fun getBlockType(geoX: Int, geoY: Int): BlockType {
        return try {
            val type = geoEngine.javaClass.getMethod("getBlockType", Int::class.java, Int::class.java)
                ?.invoke(geoEngine, geoX, geoY) as? Int 
                ?: 0
            when (type) {
                0 -> BlockType.FLAT
                1 -> BlockType.COMPLEX
                2 -> BlockType.MULTILAYER
                else -> BlockType.FLAT
            }
        } catch (e: Exception) {
            BlockType.FLAT
        }
    }
    
    override fun getHeight(geoX: Int, geoY: Int): Short {
        if (priority == GeoIndexer.GeoDataProvider.SourcePriority.CONV_DAT_PRIORITY) {
            getConvDatHeight(geoX, geoY)?.let { return it }
        }
        
        return try {
            geoEngine.javaClass.getMethod("getHeight", Int::class.java, Int::class.java, Int::class.java)
                ?.invoke(geoEngine, geoX, geoY, 0) as? Short 
                ?: 0
        } catch (e: Exception) {
            try {
                geoEngine.javaClass.getMethod("getHeight", Int::class.java, Int::class.java)
                    ?.invoke(geoEngine, geoX, geoY) as? Short 
                    ?: 0
            } catch (e2: Exception) {
                0
            }
        }
    }
    
    override fun getNswe(geoX: Int, geoY: Int): Byte {
        return try {
            geoEngine.javaClass.getMethod("getNswe", Int::class.java, Int::class.java)
                ?.invoke(geoEngine, geoX, geoY) as? Byte 
                ?: 0x0F.toByte()
        } catch (e: Exception) {
            0x0F.toByte()
        }
    }
    
    override fun getLayers(geoX: Int, geoY: Int): List<GeoIndexer.GeoDataProvider.LayerInfo> {
        return listOf(GeoIndexer.GeoDataProvider.LayerInfo(getHeight(geoX, geoY), getNswe(geoX, geoY)))
    }
    
    override fun isLoaded(geoX: Int, geoY: Int): Boolean {
        return try {
            geoEngine.javaClass.getMethod("isLoaded", Int::class.java, Int::class.java)
                ?.invoke(geoEngine, geoX, geoY) as? Boolean 
                ?: true
        } catch (e: Exception) {
            true
        }
    }
    
    override fun hasL2JData(regionX: Int, regionY: Int): Boolean {
        return geoDataPath.resolve("${regionX}_${regionY}.l2j").toFile().exists()
    }
    
    override fun hasConvDatData(regionX: Int, regionY: Int): Boolean {
        return getConvDatFile(regionX, regionY) != null
    }
    
    override fun getConvDatHeight(geoX: Int, geoY: Int): Short? {
        val regionX = (geoX / 256) + 16
        val regionY = (geoY / 256) + 10
        val convFile = getConvDatFile(regionX, regionY) ?: return null
        return convFile.getHeight(geoX % 256, geoY % 256)
    }
    
    override fun getSourcePriority() = priority
    
    private fun getConvDatFile(regionX: Int, regionY: Int): ConvDatFile? {
        return convDatCache.computeIfAbsent(Pair(regionX, regionY)) {
            loadConvDatFile(regionX, regionY)
        }
    }
    
    private fun loadConvDatFile(regionX: Int, regionY: Int): ConvDatFile? {
        val names = listOf(
            "${regionX}_${regionY}_conv.dat",
            "${regionX}_${regionY}.conv.dat",
            "conv_${regionX}_${regionY}.dat"
        )
        for (name in names) {
            val file = convDatPath.resolve(name).toFile()
            if (file.exists()) return ConvDatFile(file)
        }
        return null
    }
    
    class ConvDatFile(file: java.io.File) {
        private val heights: ShortArray
        private val width: Int
        private val height: Int
        
        init {
            java.io.RandomAccessFile(file, "r").use { raf ->
                val buffer = java.nio.ByteBuffer.allocate(raf.length().toInt())
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                raf.channel.read(buffer)
                buffer.flip()
                
                when {
                    buffer.remaining() >= 256 * 256 * 2 -> { width = 256; height = 256 }
                    buffer.remaining() >= 128 * 128 * 2 -> { width = 128; height = 128 }
                    else -> {
                        val dim = kotlin.math.sqrt((buffer.remaining() / 2).toDouble()).toInt()
                        width = dim; height = dim
                    }
                }
                
                heights = ShortArray(width * height) { 
                    if (buffer.remaining() >= 2) buffer.short else 0 
                }
            }
        }
        
        fun getHeight(x: Int, y: Int): Short? {
            if (x < 0 || x >= width || y < 0 || y >= height) return null
            return heights[y * width + x]
        }
    }
}