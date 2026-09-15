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
package ext.mods.gameserver.geoengine.pathfinding.serialization
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
class L2BRSerializer {
    
    companion object {
        const val HEADER_SIZE = 64
        const val INDEX_ENTRY_SIZE = 32
        const val ZSTD_LEVEL = 3
        
        const val MAGIC_STRING = "L2BRv5"
        val MAGIC: Long = 0x4C32425276350000L
        const val VERSION: Short = 5
    }
    
    fun serialize(region: IndexedRegion, outputPath: Path) {
        outputPath.parent.toFile().mkdirs()
        
        val nodeCount = region.nodes.size
        val sortedNodes = region.nodes.toSortedMap()
        
        if (sortedNodes.firstKey() != 0 || sortedNodes.lastKey() != nodeCount - 1) {
            throw IOException("IDs dos nós não são sequenciais de 0 a ${nodeCount-1}. " +
                "Min: ${sortedNodes.firstKey()}, Max: ${sortedNodes.lastKey()}, Count: $nodeCount")
        }
        
        val dataBuffer = ByteArrayOutputStream()
        val dataStream = DataOutputStream(BufferedOutputStream(ZstdOutputStream(dataBuffer, ZSTD_LEVEL)))
        
        val indexEntries = Array<NodeIndexEntry?>(nodeCount) { null }
        
        try {
            
            dataStream.writeInt(region.regionX)
            dataStream.writeInt(region.regionY)
            dataStream.writeInt(region.sourceType.ordinal)
            dataStream.writeLong(region.generationTimeMs)
            
            dataStream.writeInt(nodeCount)
            
            sortedNodes.forEach { (index, node) ->
                if (index < 0 || index >= nodeCount) {
                    throw IOException("ID do nó fora dos limites: $index (esperado: 0..${nodeCount-1})")
                }
                
                val offset = dataStream.size() 
                writeNode(dataStream, node)
                
                indexEntries[index] = NodeIndexEntry(
                    offset = offset,
                    x = node.x.toShort(),
                    y = node.y.toShort(),
                    z = node.z.toShort(),
                    originalId = node.originalId,
                    flags = buildFlags(node),
                    edgeCount = node.edges.size.toShort()
                )
            }
            
            if (indexEntries.any { it == null }) {
                val missing = indexEntries.withIndex().filter { it.value == null }.map { it.index }
                throw IOException("Nós não indexados (buracos): ${missing.take(10)}...")
            }
            
            writeSpatialGrid(dataStream, region.spatialGrid)
            writeConnectivity(dataStream, region.connectivity)
            
        } finally {
            dataStream.close()
        }
        
        val compressedData = dataBuffer.toByteArray()
        val indexSize = nodeCount * INDEX_ENTRY_SIZE
        val dataOffset = HEADER_SIZE.toLong() + indexSize
        val expectedFileSize = dataOffset + compressedData.size
        
        if (indexSize % 32 != 0) {
            throw IOException("Tamanho do índice não é múltiplo de 32: $indexSize")
        }
        
        FileOutputStream(outputPath.toFile()).use { fos ->
            
            val headerBytes = ByteBuffer.allocate(HEADER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    putLong(MAGIC)
                    putShort(VERSION)
                    putShort(region.regionX.toShort())
                    putShort(region.regionY.toShort())
                    
                    putInt(16, nodeCount)
                    putInt(20, region.walkableCount)
                    putLong(24, HEADER_SIZE.toLong())
                    putLong(32, dataOffset)
                    putLong(40, calculateChecksum(compressedData).toLong())
                    putInt(48, compressedData.size)
                    putInt(52, INDEX_ENTRY_SIZE)
                    
                }
            fos.write(headerBytes.array())
            
            val indexBuffer = ByteBuffer.allocate(indexSize).order(ByteOrder.LITTLE_ENDIAN)
            
            indexEntries.forEachIndexed { i, entry ->
                if (entry == null) {
                    throw IOException("Entry nulo no índice na posição $i")
                }
                
                indexBuffer.apply {
                    putLong(entry.offset.toLong())
                    putInt(entry.originalId)
                    putShort(entry.x)
                    putShort(entry.y)
                    putShort(entry.z)
                    put(entry.flags)
                    putShort(entry.edgeCount)
                    
                    position(position() + 11)
                }
            }
            
            if (indexBuffer.position() != indexSize) {
                throw IOException("Buffer do índice incompleto: ${indexBuffer.position()} vs $indexSize")
            }
            
            fos.write(indexBuffer.array())
            fos.write(compressedData)
            fos.flush()
        }
        
        val actualSize = outputPath.toFile().length()
        if (actualSize != expectedFileSize) {
            throw IOException("Tamanho do arquivo incorreto! Esperado: $expectedFileSize, " +
                "Real: $actualSize, Diferença: ${expectedFileSize - actualSize}")
        }
        
        println("[L2BR] Arquivo gerado com sucesso: ${outputPath.fileName}")
        println("       Nodes: $nodeCount, Index: $indexSize bytes, Data: ${compressedData.size} bytes")
        println("       Total: $actualSize bytes, DataOffset: $dataOffset")
    }
    
    fun deserialize(inputPath: Path): IndexedRegion {
        FileInputStream(inputPath.toFile()).use { fis ->
            val header = ByteArray(HEADER_SIZE)
            if (fis.read(header) != HEADER_SIZE) {
                throw IOException("Header incompleto")
            }
            
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            
            val magic = buffer.long
            if (magic != MAGIC) throw IOException("Formato inválido: 0x${magic.toString(16)}")
            
            val version = buffer.short
            if (version != VERSION) throw IOException("Versão não suportada: $version")
            
            val regionX = buffer.short.toInt()
            val regionY = buffer.short.toInt()
            buffer.position(buffer.position() + 2) 
            
            val nodeCount = buffer.int
            val walkableCount = buffer.int
            val indexOffset = buffer.long
            val dataOffset = buffer.long
            val checksum = buffer.long
            val compressedSize = buffer.int
            val indexEntrySize = buffer.int 
            
            if (indexOffset != HEADER_SIZE.toLong()) {
                throw IOException("IndexOffset inválido: $indexOffset (esperado: $HEADER_SIZE)")
            }
            
            val expectedIndexSize = nodeCount * INDEX_ENTRY_SIZE
            val expectedDataOffset = HEADER_SIZE + expectedIndexSize
            
            if (dataOffset != expectedDataOffset.toLong()) {
                throw IOException("DataOffset inconsistente: $dataOffset vs esperado $expectedDataOffset")
            }
            
            if (indexEntrySize != INDEX_ENTRY_SIZE) {
                throw IOException("IndexEntrySize incorreto: $indexEntrySize (esperado: $INDEX_ENTRY_SIZE)")
            }
            
            val fileSize = inputPath.toFile().length()
            val expectedFileSize = dataOffset + compressedSize
            
            if (fileSize < expectedFileSize) {
                throw IOException("Arquivo truncado: $fileSize < $expectedFileSize")
            }
            
            val skipBytes = dataOffset - HEADER_SIZE
            if (fis.skip(skipBytes) != skipBytes) {
                throw IOException("Falha ao pular índice")
            }
            
            val compressedData = ByteArray(compressedSize)
            if (fis.read(compressedData) != compressedSize) {
                throw IOException("Dados comprimidos incompletos")
            }
            
            if (calculateChecksum(compressedData).toLong() != checksum) {
                throw IOException("Checksum inválido")
            }
            
            val dataStream = DataInputStream(BufferedInputStream(ZstdInputStream(ByteArrayInputStream(compressedData))))
            
            return try {
                val readRegionX = dataStream.readInt()
                val readRegionY = dataStream.readInt()
                val sourceType = SourceType.values().getOrElse(dataStream.readInt()) {
                    throw IOException("SourceType inválido")
                }
                val genTime = dataStream.readLong()
                
                val nodes = readNodes(dataStream, nodeCount)
                val spatialGrid = readSpatialGrid(dataStream)
                val connectivity = readConnectivity(dataStream)
                
                if (readRegionX != regionX || readRegionY != regionY) {
                    throw IOException("Inconsistência de metadados: header($regionX,$regionY) vs data($readRegionX,$readRegionY)")
                }
                
                IndexedRegion(
                    regionX = regionX,
                    regionY = regionY,
                    nodes = nodes,
                    walkableCount = walkableCount,
                    spatialGrid = spatialGrid,
                    connectivity = connectivity,
                    sourceType = sourceType,
                    generationTimeMs = genTime
                )
            } finally {
                dataStream.close()
            }
        }
    }
    
    private fun buildFlags(node: Node): Byte {
        var flags = 0.toByte()
        if (node.isWalkable) flags = (flags.toInt() or 0x01).toByte()
        if (node.isCellEdge) flags = (flags.toInt() or 0x02).toByte()
        return flags
    }
    
    private fun writeNode(stream: DataOutputStream, node: Node) {
        stream.writeShort(node.x)
        stream.writeShort(node.y)
        stream.writeShort(node.z)
        stream.writeInt(node.originalId)
        stream.writeByte(buildFlags(node).toInt())
        
        stream.writeShort(node.edges.size)
        node.edges.forEach { edge ->
            stream.writeInt(edge.targetId)
            stream.writeShort((edge.cost * 10).toInt())
            stream.writeByte(edge.type.ordinal)
        }
    }
    
    private fun readNodes(stream: DataInputStream, expectedCount: Int): Map<Int, Node> {
        val count = stream.readInt()
        if (count != expectedCount) throw IOException("Contagem inconsistente: $count vs $expectedCount")
        
        val nodes = Int2ObjectOpenHashMap<Node>(count)
        repeat(count) { index ->
            val x = stream.readShort().toInt()
            val y = stream.readShort().toInt()
            val z = stream.readShort().toInt()
            val originalId = stream.readInt()
            val flags = stream.readByte()
            
            val isWalkable = (flags.toInt() and 0x01) != 0
            val isCellEdge = (flags.toInt() and 0x02) != 0
            
            val edgeCount = stream.readShort().toInt()
            val edges = ArrayList<Edge>(edgeCount).apply {
                repeat(edgeCount) {
                    add(Edge(
                        targetId = stream.readInt(),
                        cost = stream.readShort().toDouble() / 10.0,
                        type = EdgeType.values().getOrElse(stream.readByte().toInt()) { EdgeType.WALK }
                    ))
                }
            }
            
            nodes.put(index, Node(
                x = x, y = y, z = z,
                originalId = originalId,
                isWalkable = isWalkable,
                isCellEdge = isCellEdge,
                edges = edges
            ))
        }
        return nodes
    }
    
    private fun writeSpatialGrid(stream: DataOutputStream, grid: SpatialGrid) {
        stream.writeInt(grid.cellSize)
        stream.writeInt(grid.width)
        stream.writeInt(grid.height)
        
        for (x in 0 until grid.width) {
            for (y in 0 until grid.height) {
                val nodes = grid.getCell(x, y)
                stream.writeShort(nodes.size)
                nodes.forEach { stream.writeInt(it) }
            }
        }
    }
    
    private fun readSpatialGrid(stream: DataInputStream): SpatialGrid {
        val cellSize = stream.readInt()
        val width = stream.readInt()
        val height = stream.readInt()
        
        val grid = SpatialGrid(cellSize, width, height)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val count = stream.readShort().toInt()
                repeat(count) { grid.addNode(x, y, stream.readInt()) }
            }
        }
        return grid
    }
    
    private fun writeConnectivity(stream: DataOutputStream, connectivity: NodeConnectivity) {
        stream.writeInt(connectivity.edges.size)
        connectivity.edges.forEach { conn ->
            stream.writeInt(conn.from)
            stream.writeInt(conn.to)
            stream.writeShort((conn.cost * 10).toInt())
            stream.writeByte(conn.type.ordinal)
        }
    }
    
    private fun readConnectivity(stream: DataInputStream): NodeConnectivity {
        val count = stream.readInt()
        val edges = ArrayList<Connection>(count)
        repeat(count) {
            edges.add(Connection(
                from = stream.readInt(),
                to = stream.readInt(),
                cost = stream.readShort().toDouble() / 10.0,
                type = EdgeType.values().getOrElse(stream.readByte().toInt()) { EdgeType.WALK }
            ))
        }
        return NodeConnectivity(edges)
    }
    
    private fun calculateChecksum(data: ByteArray): Int {
        var a = 1
        var b = 0
        for (byte in data) {
            a = (a + byte) % 65521
            b = (b + a) % 65521
        }
        return (b shl 16) or a
    }
    
    private data class NodeIndexEntry(
        val offset: Int,
        val x: Short,
        val y: Short,
        val z: Short,
        val originalId: Int,
        val flags: Byte,
        val edgeCount: Short
    )
}
data class IndexedRegion(
    val regionX: Int,
    val regionY: Int,
    val nodes: Map<Int, Node>,
    val walkableCount: Int,
    val spatialGrid: SpatialGrid,
    val connectivity: NodeConnectivity,
    val sourceType: SourceType,
    val generationTimeMs: Long
)
data class Node(
    val x: Int,
    val y: Int,
    val z: Int,
    val originalId: Int,
    val isWalkable: Boolean,
    val isCellEdge: Boolean,
    val edges: List<Edge> = emptyList()
)
data class Edge(val targetId: Int, val cost: Double, val type: EdgeType)
enum class EdgeType { WALK, JUMP, CLIMB, FALL }
data class SpatialGrid(val cellSize: Int, val width: Int, val height: Int) {
    private val cells = Array(width) { Array(height) { IntArrayList() } }
    
    fun addNode(cellX: Int, cellY: Int, nodeId: Int) {
        if (cellX in 0 until width && cellY in 0 until height) {
            cells[cellX][cellY].add(nodeId)
        }
    }
    
    fun getCell(x: Int, y: Int): IntArrayList = 
        if (x in 0 until width && y in 0 until height) cells[x][y] else IntArrayList()
}
data class NodeConnectivity(val edges: List<Connection>)
data class Connection(val from: Int, val to: Int, val cost: Double, val type: EdgeType)
enum class SourceType { L2J, CONV_DAT, MIXED }