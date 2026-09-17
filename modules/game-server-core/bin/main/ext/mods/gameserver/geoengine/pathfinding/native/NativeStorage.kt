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
package ext.mods.gameserver.geoengine.pathfinding.native
import ext.mods.gameserver.geoengine.pathfinding.model.L2BRHeader
import ext.mods.gameserver.geoengine.pathfinding.model.PathNode
import java.io.RandomAccessFile
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.logging.Logger
class NativeStorage private constructor(
    val arena: Arena,
    val segment: MemorySegment,
    val header: L2BRHeader,
    private val channel: FileChannel? = null
) : AutoCloseable {
    companion object {
        const val INDEX_ENTRY_SIZE = 32L
        private const val PREFETCH_PAGES = 32
        private const val PAGE_SIZE = 4096
        private val logger = Logger.getLogger(NativeStorage::class.java.name)
        fun open(path: Path, shared: Boolean = false): NativeStorage {
            
            val arena = if (shared) Arena.ofShared() else Arena.ofConfined()
            return try {
                val file = path.toFile()
                if (!file.exists()) {
                    throw java.io.FileNotFoundException("Arquivo não encontrado: $path")
                }
                
                if (!file.canRead()) {
                    throw java.io.IOException("Sem permissão de leitura: $path")
                }
                val channel = FileChannel.open(path, StandardOpenOption.READ)
                val fileSize = channel.size()
                
                require(fileSize >= L2BRHeader.HEADER_SIZE) {
                    "Arquivo muito pequeno para ter header válido: $fileSize bytes (min: ${L2BRHeader.HEADER_SIZE})"
                }
                val mapMode = FileChannel.MapMode.READ_ONLY
                val segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, arena)
                
                val header = try {
                    L2BRHeader.readFrom(segment)
                } catch (e: Exception) {
                    logger.severe("[NativeStorage] Erro ao ler header: ${e.javaClass.simpleName}: ${e.message}")
                    throw e
                }
                
                try {
                    validateHeader(header, fileSize, segment)
                } catch (e: Exception) {
                    logger.severe("[NativeStorage] Validação falhou: ${e.message}")
                    throw e
                }
                if (!shared) {
                    prefetchMemory(segment, fileSize)
                }
                NativeStorage(arena, segment, header, if (shared) channel else null)
                
            } catch (e: Exception) {
                logger.severe("[NativeStorage] Falha ao abrir: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                arena.close()
                throw NativeStorageException("Falha ao abrir storage: ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }
        fun create(path: Path, header: L2BRHeader, initialSize: Long? = null): NativeStorage {
            val arena = Arena.ofConfined()
            
            return try {
                val minSize = initialSize ?: (header.dataOffset + 1024)
                
                path.parent?.toFile()?.mkdirs()
                
                RandomAccessFile(path.toFile(), "rw").use { raf ->
                    raf.setLength(minSize)
                }
                val channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE)
                val segment = channel.map(FileChannel.MapMode.READ_WRITE, 0, minSize, arena)
                L2BRHeader.writeTo(segment, header)
                NativeStorage(arena, segment, header, channel)
            } catch (e: Exception) {
                arena.close()
                throw NativeStorageException("Falha ao criar storage: ${e.message}", e)
            }
        }
        private fun validateHeader(header: L2BRHeader, fileSize: Long, segment: MemorySegment) {
            if (!header.isValid()) {
                throw IllegalArgumentException(
                    "Magic ou versão inválida. Esperado: ${L2BRHeader.MAGIC}/${L2BRHeader.VERSION}, " +
                    "Obtido: ${header.magic}/${header.version}"
                )
            }
            
            if (header.nodeCount <= 0) {
                throw IllegalArgumentException("Node count inválido: ${header.nodeCount}")
            }
            
            if (header.indexOffset != 64L) {
                throw IllegalArgumentException("IndexOffset deve ser 64, obtido: ${header.indexOffset}")
            }
            
            val indexEnd = header.indexOffset + (header.nodeCount * INDEX_ENTRY_SIZE)
            if (indexEnd > fileSize) {
                throw IllegalArgumentException(
                    "Índice excede tamanho do arquivo. " +
                    "IndexEnd: $indexEnd, FileSize: $fileSize, " +
                    "NodeCount: ${header.nodeCount}, EntrySize: $INDEX_ENTRY_SIZE"
                )
            }
            
            if (header.dataOffset < indexEnd) {
                throw IllegalArgumentException(
                    "DataOffset (${header.dataOffset}) deve ser >= IndexEnd ($indexEnd)"
                )
            }
        }
        private fun prefetchMemory(segment: MemorySegment, size: Long) {
            var offset = 0L
            while (offset < size) {
                segment.get(ValueLayout.JAVA_BYTE, offset)
                offset += PAGE_SIZE
            }
        }
    }
    val nodeCount: Int get() = header.nodeCount
    val mappedSize: Long get() = segment.byteSize()
    fun getNode(nodeId: Int): PathNode {
        if (nodeId < 0 || nodeId >= header.nodeCount) {
            throw IndexOutOfBoundsException(
                "NodeId $nodeId fora do range [0, ${header.nodeCount})"
            )
        }
        
        val offset = header.indexOffset + (nodeId * INDEX_ENTRY_SIZE)
        return PathNode.fromMemory(segment.asSlice(offset, INDEX_ENTRY_SIZE), nodeId)
    }
    fun getNodeSegment(nodeId: Int): MemorySegment {
        if (nodeId < 0 || nodeId >= header.nodeCount) {
            throw IndexOutOfBoundsException("NodeId $nodeId fora do range [0, ${header.nodeCount})")
        }
        
        val offset = header.indexOffset + (nodeId * INDEX_ENTRY_SIZE)
        return segment.asSlice(offset, INDEX_ENTRY_SIZE)
    }
    fun forEachNode(action: (Int, PathNode) -> Unit) {
        var offset = header.indexOffset
        val maxOffset = offset + (header.nodeCount * INDEX_ENTRY_SIZE)
        
        if (maxOffset > segment.byteSize()) {
            throw IllegalStateException(
                "forEachNode iria acessar bytes além do limite. " +
                "IndexEnd: $maxOffset, FileSize: ${segment.byteSize()}"
            )
        }
        
        for (i in 0 until header.nodeCount) {
            val node = PathNode.fromMemory(segment.asSlice(offset, INDEX_ENTRY_SIZE), i)
            action(i, node)
            offset += INDEX_ENTRY_SIZE
        }
    }
    fun forEachNodeParallel(chunkSize: Int = 1024, action: (Int, PathNode) -> Unit) {
        val chunks = (header.nodeCount + chunkSize - 1) / chunkSize
        
        (0 until chunks).toList().parallelStream().forEach { chunkIdx ->
            val start = chunkIdx * chunkSize
            val end = minOf(start + chunkSize, header.nodeCount)
            
            var offset = header.indexOffset + (start * INDEX_ENTRY_SIZE)
            
            for (i in start until end) {
                val node = PathNode.fromMemory(segment.asSlice(offset, INDEX_ENTRY_SIZE), i)
                action(i, node)
                offset += INDEX_ENTRY_SIZE
            }
        }
    }
    fun writeAt(offset: Long, data: MemorySegment) {
        if (offset < 0 || offset + data.byteSize() > segment.byteSize()) {
            throw IndexOutOfBoundsException(
                "Write at offset $offset, size ${data.byteSize()} " +
                "exceeds segment size ${segment.byteSize()}"
            )
        }
        MemorySegment.copy(data, 0L, segment, offset, data.byteSize())
    }
    fun writeNode(nodeId: Int, builder: ext.mods.gameserver.geoengine.pathfinding.model.PathNodeBuilder) {
        writeIndexEntry(nodeId, builder)
    }
    fun writeIndexEntry(nodeId: Int, builder: ext.mods.gameserver.geoengine.pathfinding.model.PathNodeBuilder) {
        if (nodeId < 0 || nodeId >= header.nodeCount) {
            throw IndexOutOfBoundsException("Cannot write node $nodeId")
        }
        
        val offset = header.indexOffset + (nodeId * INDEX_ENTRY_SIZE)
        builder.writeTo(segment, offset)
    }
    fun force() {
        segment.force()
    }
    fun getMemoryStats(): MemoryStats {
        val indexSize = header.nodeCount * INDEX_ENTRY_SIZE
        return MemoryStats(
            fileSize = segment.byteSize(),
            mappedSize = segment.byteSize(),
            nodeCount = header.nodeCount,
            indexSize = indexSize,
            dataOffset = header.dataOffset,
            headerSize = L2BRHeader.HEADER_SIZE.toLong()
        )
    }
    override fun close() {
        try {
            segment.force()
        } catch (e: Exception) {
        }
        arena.close()
        channel?.close()
    }
    data class MemoryStats(
        val fileSize: Long,
        val mappedSize: Long,
        val nodeCount: Int,
        val indexSize: Long,
        val dataOffset: Long,
        val headerSize: Long
    ) {
        val indexEndOffset: Long get() = headerSize + indexSize
        val efficiency: Double get() = if (fileSize > 0) indexSize.toDouble() / fileSize else 0.0
    }
}
class NativeStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)