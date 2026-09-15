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
package ext.mods.gameserver.geoengine.pathfinding.core.spatial
import ext.mods.gameserver.geoengine.pathfinding.model.GeoLocation
import ext.mods.gameserver.geoengine.pathfinding.model.Location
import java.util.*
class RTreeIndex(
    private val maxEntries: Int = 16,
    private val minEntries: Int = maxEntries / 2
) {
    private var root: Node = LeafNode()
    private var size = 0
    
    fun insert(entry: Entry) {
        root = insertRecursive(root, entry)
        size++
    }
    
    fun search(rect: Rectangle): List<Entry> {
        val results = mutableListOf<Entry>()
        searchRecursive(root, rect, results)
        return results
    }
    
    fun nearestNeighbor(x: Int, y: Int): Entry? {
        return nearestNeighborRecursive(root, x, y, null, Double.MAX_VALUE)?.first
    }
    
    fun bulkLoad(entries: List<Entry>) {
        if (entries.isEmpty()) return
        
        root = buildSTR(entries.sortedWith(compareBy({ it.rect.minX }, { it.rect.minY })))
        size = entries.size
    }
    private fun buildSTR(entries: List<Entry>): Node {
        if (entries.size <= maxEntries) {
            return LeafNode(entries.toMutableList())
        }
        val sliceCount = kotlin.math.ceil(entries.size.toDouble() / maxEntries).toInt()
        val sliceCapacity = kotlin.math.ceil(entries.size.toDouble() / sliceCount).toInt()
        val slices = entries.chunked(sliceCapacity)
        
        val children = slices.flatMap { slice ->
            slice.sortedBy { it.rect.minY }
                .chunked(maxEntries)
                .map { chunk -> LeafNode(chunk.toMutableList()) }
        }
        return buildTreeLevel(children)
    }
    private fun buildTreeLevel(nodes: List<Node>): Node {
        if (nodes.size <= maxEntries) {
            return InternalNode(nodes.toMutableList())
        }
        val parents = nodes.chunked(maxEntries).map { chunk ->
            InternalNode(chunk.toMutableList())
        }
        
        return buildTreeLevel(parents)
    }
    private fun insertRecursive(node: Node, entry: Entry): Node {
        if (node is LeafNode) {
            node.entries.add(entry)
            if (node.entries.size > maxEntries) {
                return splitNode(node)
            }
            return node
        }
        node as InternalNode
        val bestChild = chooseBestChild(node, entry)
        val newChild = insertRecursive(bestChild, entry)
        
        val index = node.children.indexOf(bestChild)
        if (index >= 0) {
            node.children[index] = newChild
        }
        if (node.children.size > maxEntries) {
            return splitNode(node)
        }
        
        return node
    }
    private fun chooseBestChild(node: InternalNode, entry: Entry): Node {
        return node.children.minByOrNull { child ->
            val expanded = child.mbr.expand(entry.rect)
            expanded.area() - child.mbr.area()
        } ?: node.children.first()
    }
    private fun splitNode(node: Node): Node {
        val entries = when (node) {
            is LeafNode -> node.entries
            is InternalNode -> node.children.map { Entry(it.mbr, -1) }.toMutableList()
        }
        var seed1 = 0
        var seed2 = 1
        var maxDist = -1.0
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val dist = entries[i].rect.distance(entries[j].rect)
                if (dist > maxDist) {
                    maxDist = dist
                    seed1 = i
                    seed2 = j
                }
            }
        }
        val group1 = mutableListOf(entries[seed1])
        val group2 = mutableListOf(entries[seed2])
        val remaining = entries.filterIndexed { i, _ -> i != seed1 && i != seed2 }.toMutableList()
        while (remaining.isNotEmpty()) {
            if (group1.size + remaining.size <= minEntries) {
                group1.addAll(remaining)
                break
            }
            if (group2.size + remaining.size <= minEntries) {
                group2.addAll(remaining)
                break
            }
            val next = remaining.removeFirst()
            val mbr1 = group1.fold(Rectangle.EMPTY) { acc, e -> acc.expand(e.rect) }
            val mbr2 = group2.fold(Rectangle.EMPTY) { acc, e -> acc.expand(e.rect) }
            val cost1 = mbr1.expand(next.rect).area() - mbr1.area()
            val cost2 = mbr2.expand(next.rect).area() - mbr2.area()
            if (cost1 < cost2) group1.add(next) else group2.add(next)
        }
        return when (node) {
            is LeafNode -> {
                node.entries.clear()
                node.entries.addAll(group1)
                LeafNode(group2.toMutableList())
            }
            is InternalNode -> {
                node
            }
        }
    }
    private fun searchRecursive(node: Node, rect: Rectangle, results: MutableList<Entry>) {
        if (!node.mbr.intersects(rect)) return
        when (node) {
            is LeafNode -> {
                results.addAll(node.entries.filter { it.rect.intersects(rect) })
            }
            is InternalNode -> {
                node.children.forEach { searchRecursive(it, rect, results) }
            }
        }
    }
    private fun nearestNeighborRecursive(
        node: Node, 
        x: Int, y: Int, 
        bestEntry: Entry?, 
        bestDist: Double
    ): Pair<Entry, Double>? {
        if (node.mbr.distanceToPoint(x, y) > bestDist) return bestEntry?.to(bestDist)
        when (node) {
            is LeafNode -> {
                var best = bestEntry
                var dist = bestDist
                
                node.entries.forEach { entry ->
                    val d = entry.rect.distanceToPoint(x, y)
                    if (d < dist) {
                        dist = d
                        best = entry
                    }
                }
                
                return best?.to(dist)
            }
            is InternalNode -> {
                val sorted = node.children.sortedBy { it.mbr.distanceToPoint(x, y) }
                var best = bestEntry
                var dist = bestDist
                
                sorted.forEach { child ->
                    val result = nearestNeighborRecursive(child, x, y, best, dist)
                    if (result != null && result.second < dist) {
                        best = result.first
                        dist = result.second
                    }
                }
                
                return best?.to(dist)
            }
        }
    }
    fun size(): Int = size
    
    data class Rectangle(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int
    ) {
        fun area(): Long = (maxX - minX).toLong() * (maxY - minY)
        
        fun expand(other: Rectangle): Rectangle = Rectangle(
            minOf(minX, other.minX),
            minOf(minY, other.minY),
            maxOf(maxX, other.maxX),
            maxOf(maxY, other.maxY)
        )
        
        fun intersects(other: Rectangle): Boolean {
            return minX <= other.maxX && maxX >= other.minX &&
                   minY <= other.maxY && maxY >= other.minY
        }
        
        fun distance(other: Rectangle): Double {
            val dx = when {
                maxX < other.minX -> other.minX - maxX
                minX > other.maxX -> minX - other.maxX
                else -> 0
            }
            val dy = when {
                maxY < other.minY -> other.minY - maxY
                minY > other.maxY -> minY - other.maxY
                else -> 0
            }
            return kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
        }
        
        fun distanceToPoint(x: Int, y: Int): Double {
            val dx = when {
                x < minX -> minX - x
                x > maxX -> x - maxX
                else -> 0
            }
            val dy = when {
                y < minY -> minY - y
                y > maxY -> y - maxY
                else -> 0
            }
            return kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
        }
        companion object {
            val EMPTY = Rectangle(0, 0, 0, 0)
        }
    }
    
    data class Entry(
        val rect: Rectangle,
        val nodeId: Int,
        val data: Any? = null
    )
    
    sealed class Node {
        abstract val mbr: Rectangle
        abstract fun calculateMBR(): Rectangle
    }
    class InternalNode(
        val children: MutableList<Node> = mutableListOf()
    ) : Node() {
        override val mbr: Rectangle by lazy { calculateMBR() }
        override fun calculateMBR(): Rectangle {
            if (children.isEmpty()) return Rectangle.EMPTY
            return children.fold(children.first().mbr) { acc, child -> acc.expand(child.mbr) }
        }
    }
    class LeafNode(
        val entries: MutableList<Entry> = mutableListOf()
    ) : Node() {
        override val mbr: Rectangle by lazy { calculateMBR() }
        override fun calculateMBR(): Rectangle {
            if (entries.isEmpty()) return Rectangle.EMPTY
            return entries.fold(entries.first().rect) { acc, entry -> acc.expand(entry.rect) }
        }
    }
}
class SpatialIndexAdapter(private val rtree: RTreeIndex = RTreeIndex()) {
    
    fun insertNode(geoX: Int, geoY: Int, nodeId: Int) {
        val rect = RTreeIndex.Rectangle(geoX, geoY, geoX + 1, geoY + 1)
        rtree.insert(RTreeIndex.Entry(rect, nodeId))
    }
    
    fun findAt(geo: GeoLocation): Int? {
        val rect = RTreeIndex.Rectangle(geo.x, geo.y, geo.x + 1, geo.y + 1)
        return rtree.search(rect).firstOrNull()?.nodeId
    }
    
    fun findNearest(location: Location): Int? {
        val geo = location.toGeo()
        return rtree.nearestNeighbor(geo.x, geo.y)?.nodeId
    }
    
    fun findInArea(minX: Int, minY: Int, maxX: Int, maxY: Int): List<Int> {
        val rect = RTreeIndex.Rectangle(minX, minY, maxX, maxY)
        return rtree.search(rect).map { it.nodeId }
    }
    
    fun bulkLoadFromNodes(nodes: List<ext.mods.gameserver.geoengine.pathfinding.model.IndexedNode>) {
        val entries = nodes.map { node ->
            RTreeIndex.Entry(
                RTreeIndex.Rectangle(node.geoX, node.geoY, node.geoX + 1, node.geoY + 1),
                node.id
            )
        }
        rtree.bulkLoad(entries)
    }
}