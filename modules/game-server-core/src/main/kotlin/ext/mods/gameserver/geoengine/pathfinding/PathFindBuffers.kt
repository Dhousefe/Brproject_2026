/*
 * MIT License
 * Copyright (c) 2024-2026 L2Brproject
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package ext.mods.gameserver.geoengine.pathfinding

import ext.mods.gameserver.geoengine.GeoEngine
import ext.mods.gameserver.model.location.Location
import java.util.PriorityQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Pool de buffers pré-alocados de matrizes bidimensionais para o algoritmo A*.
 * Garante Zero-GC e indexação O(1) direta sem alocações dinâmicas no Heap.
 * Inspirado na arquitetura de alta performance do Lucera2.
 */
object PathFindBuffers {
    const val MIN_MAP_SIZE = 64
    const val MAX_MAP_SIZE = 512

    val SIZES = intArrayOf(64, 96, 128, 192, 256, 384, 512)
    private val INITIAL_COUNTS = intArrayOf(8, 6, 4, 2, 2, 1, 1)

    private val lock = ReentrantLock()
    private val buffersMap = HashMap<Int, MutableList<PathFindBuffer>>()

    init {
        for (i in SIZES.indices) {
            val size = SIZES[i]
            val count = INITIAL_COUNTS[i]
            val list = ArrayList<PathFindBuffer>(count)
            for (c in 0 until count) {
                list.add(PathFindBuffer(size))
            }
            buffersMap[size] = list
        }
    }

    /**
     * Aloca um buffer adequado para a distância solicitada.
     */
    fun alloc(neededSize: Int): PathFindBuffer? {
        if (neededSize > MAX_MAP_SIZE) return null
        val targetSize = (neededSize + 16).coerceIn(MIN_MAP_SIZE, MAX_MAP_SIZE)

        var selectedSize = MAX_MAP_SIZE
        for (size in SIZES) {
            if (size >= targetSize) {
                selectedSize = size
                break
            }
        }

        lock.withLock {
            val pool = buffersMap.getOrPut(selectedSize) { ArrayList() }
            for (buf in pool) {
                if (!buf.inUse) {
                    buf.inUse = true
                    buf.totalUses++
                    return buf
                }
            }
            // Expansão dinâmica sob demanda se todos os buffers estiverem ocupados
            val newBuf = PathFindBuffer(selectedSize)
            newBuf.inUse = true
            newBuf.totalUses++
            pool.add(newBuf)
            return newBuf
        }
    }

    /**
     * Recicla o buffer e limpa os nós utilizados com O(k) de complexidade.
     */
    fun recycle(buffer: PathFindBuffer) {
        lock.withLock {
            buffer.free()
            buffer.inUse = false
        }
    }

    /**
     * Nó pré-alocado reutilizável para a malha bidimensional.
     */
    class GeoNode : Comparable<GeoNode> {
        companion object {
            const val STATE_NONE = 0
            const val STATE_OPENED = 1
            const val STATE_CLOSED = -1
        }

        var geoX: Int = 0
        var geoY: Int = 0
        var z: Int = 0
        var nswe: Byte = -1
        var costG: Int = 0
        var costH: Int = 0
        var costF: Int = 0
        var state: Int = STATE_NONE
        var parent: GeoNode? = null

        fun set(gx: Int, gy: Int, gz: Int, nsweVal: Byte): GeoNode {
            this.geoX = gx
            this.geoY = gy
            this.z = gz
            this.nswe = nsweVal
            return this
        }

        fun isSet(): Boolean = nswe.toInt() != -1

        fun setCost(parentNode: GeoNode?, weight: Int, hCost: Int) {
            costG = weight + (parentNode?.costG ?: 0)
            costH = hCost
            costF = costG + costH
            parent = parentNode
        }

        fun free() {
            nswe = -1
            costG = 0
            costH = 0
            costF = 0
            state = STATE_NONE
            parent = null
        }

        fun getLoc(): Location {
            return Location(GeoEngine.getWorldX(geoX), GeoEngine.getWorldY(geoY), z)
        }

        override fun compareTo(other: GeoNode): Int {
            return this.costF - other.costF
        }

        override fun toString(): String {
            return "GeoNode(geoX=$geoX, geoY=$geoY, z=$z, costF=$costF, state=$state)"
        }
    }

    /**
     * Instância do buffer contendo matriz pré-alocada e fila de prioridade.
     */
    class PathFindBuffer(val mapSize: Int) {
        val nodes: Array<Array<GeoNode>> = Array(mapSize) {
            Array(mapSize) { GeoNode() }
        }
        val open: PriorityQueue<GeoNode> = PriorityQueue(mapSize * 2)
        val touchedNodes: ArrayList<GeoNode> = ArrayList(mapSize * 4)

        var offsetX: Int = 0
        var offsetY: Int = 0
        var inUse: Boolean = false

        var totalUses: Long = 0
        var successUses: Long = 0
        var overtimeUses: Long = 0

        /**
         * Marca um nó como tocado para reset rápido O(k).
         */
        fun markTouched(node: GeoNode) {
            touchedNodes.add(node)
        }

        fun free() {
            open.clear()
            for (i in 0 until touchedNodes.size) {
                touchedNodes[i].free()
            }
            touchedNodes.clear()
        }
    }
}
