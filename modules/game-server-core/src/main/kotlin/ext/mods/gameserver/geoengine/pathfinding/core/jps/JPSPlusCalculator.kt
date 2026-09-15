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
package ext.mods.gameserver.geoengine.pathfinding.core.jps
import ext.mods.gameserver.geoengine.pathfinding.model.*
import ext.mods.gameserver.geoengine.pathfinding.native.NativeStorage
import java.util.*
import kotlin.math.abs
class JPSPlusCalculator(
    private val storage: NativeStorage,
    private val dynamicLayer: ext.mods.gameserver.geoengine.pathfinding.dynamic.DynamicObstacleLayer
) {
    private val openSet = PriorityQueue<NodeEntry>(1024, compareBy { it.fScore })
    private val gScores = IntArray(storage.nodeCount) { Int.MAX_VALUE }
    private val fScores = IntArray(storage.nodeCount) { Int.MAX_VALUE }
    private val cameFrom = IntArray(storage.nodeCount) { -1 }
    private val closedSet = BitSet(storage.nodeCount)
    
    private val nodeCache = Array(storage.nodeCount) { idx ->
        lazy { storage.getNode(idx) }
    }
    
    fun findPath(start: Location, goal: Location): List<Location> {
        val startGeo = start.toGeo()
        val goalGeo = goal.toGeo()
        
        val startId = getNodeId(startGeo)
        val goalId = getNodeId(goalGeo)
        
        if (startId < 0 || goalId < 0) return emptyList()
        if (startId == goalId) return listOf(start)
        if (!isWalkable(goalId)) return emptyList()
        
        resetStructures()
        
        gScores[startId] = 0
        fScores[startId] = heuristic(startId, goalId)
        openSet.add(NodeEntry(startId, fScores[startId]))
        
        var iterations = 0
        val maxIterations = storage.nodeCount / 4
        
        while (openSet.isNotEmpty() && iterations < maxIterations) {
            iterations++
            val current = openSet.poll()?.nodeId ?: break
            
            if (current == goalId) {
                return reconstructPath(current, start, goal)
            }
            
            if (closedSet.get(current)) continue
            closedSet.set(current)
            
            expandNode(current, goalId)
        }
        
        return emptyList()
    }
    
    private fun expandNode(currentId: Int, goalId: Int) {
        val currentNode = nodeCache[currentId].value
        val currentG = gScores[currentId]
        
        for (dirIdx in 0..7) {
            val jumpDistance = currentNode.getJpsDistance(dirIdx)
            if (jumpDistance == 0) continue
            
            val direction = Direction.fromIndex(dirIdx)
            val neighborId = jump(currentId, direction, jumpDistance)
            
            if (neighborId < 0 || closedSet.get(neighborId)) continue
            if (!isWalkable(neighborId)) continue
            
            if (dynamicLayer.isBlocked(neighborId)) continue
            
            val moveCost = direction.cost * jumpDistance
            val tentativeG = currentG + moveCost
            
            if (tentativeG < gScores[neighborId]) {
                cameFrom[neighborId] = currentId
                gScores[neighborId] = tentativeG
                fScores[neighborId] = tentativeG + heuristic(neighborId, goalId)
                openSet.add(NodeEntry(neighborId, fScores[neighborId]))
            }
        }
    }
    
    private fun jump(fromId: Int, direction: Direction, maxDistance: Int): Int {
        var currentId = fromId
        var remaining = maxDistance
        
        while (remaining > 0) {
            val node = nodeCache[currentId].value
            val nextId = getNeighborId(currentId, direction)
            
            if (nextId < 0 || !isWalkable(nextId) || dynamicLayer.isBlocked(nextId)) {
                return -1
            }
            
            currentId = nextId
            remaining--
            
            if (remaining > 0 && hasForcedNeighbor(currentId, direction)) {
                return currentId
            }
        }
        
        return currentId
    }
    
    private fun hasForcedNeighbor(nodeId: Int, direction: Direction): Boolean {
        val node = nodeCache[nodeId].value
        
        if (direction.dx != 0 && direction.dy != 0) {
            val dirX = Direction.fromDelta(direction.dx, 0) ?: return false
            val dirY = Direction.fromDelta(0, direction.dy) ?: return false
            
            return node.getJpsDistance(dirX.ordinal) > 0 || 
                   node.getJpsDistance(dirY.ordinal) > 0
        }
        
        return false
    }
    
    private fun heuristic(fromId: Int, toId: Int): Int {
        val from = nodeCache[fromId].value
        val to = nodeCache[toId].value
        
        val dx = abs(from.x - to.x)
        val dy = abs(from.y - to.y)
        
        return minOf(dx, dy) * 14 + abs(dx - dy) * 10
    }
    
    private fun reconstructPath(endId: Int, start: Location, goal: Location): List<Location> {
        val path = LinkedList<Location>()
        var currentId = endId
        
        path.addFirst(goal)
        
        while (currentId >= 0 && cameFrom[currentId] >= 0) {
            val node = nodeCache[currentId].value
            path.addFirst(node.toLocation())
            currentId = cameFrom[currentId]
        }
        
        if (path.firstOrNull() != start) {
            path.addFirst(start)
        }
        
        return path
    }
    
    private fun getNodeId(geo: GeoLocation): Int {
        val localX = geo.x % GeoStructure.REGION_CELLS_X
        val localY = geo.y % GeoStructure.REGION_CELLS_Y
        return localY * GeoStructure.REGION_CELLS_X + localX
    }
    
    private fun getNeighborId(nodeId: Int, direction: Direction): Int {
        val node = nodeCache[nodeId].value
        val newX = node.x + direction.dx
        val newY = node.y + direction.dy
        
        if (newX < 0 || newX >= GeoStructure.REGION_CELLS_X ||
            newY < 0 || newY >= GeoStructure.REGION_CELLS_Y) {
            return -1
        }
        
        return newY * GeoStructure.REGION_CELLS_X + newX
    }
    
    private fun isWalkable(nodeId: Int): Boolean {
        if (nodeId < 0 || nodeId >= storage.nodeCount) return false
        return nodeCache[nodeId].value.isWalkable
    }
    
    private fun resetStructures() {
        openSet.clear()
        closedSet.clear()
        Arrays.fill(gScores, Int.MAX_VALUE)
        Arrays.fill(fScores, Int.MAX_VALUE)
        Arrays.fill(cameFrom, -1)
    }
    
    private data class NodeEntry(
        val nodeId: Int,
        val fScore: Int
    )
    
    data class SearchStats(
        val nodesExplored: Int,
        val iterations: Int,
        val timeNanos: Long
    )
}
class JumpPointGenerator(
    private val nodes: List<IndexedNode>,
    private val regionWidth: Int = 256,
    private val regionHeight: Int = 256
) {
    private val nodeMap = Array(regionHeight) { IntArray(regionWidth) { -1 } }
    
    init {
        nodes.forEach { node ->
            val localX = node.geoX % regionWidth
            val localY = node.geoY % regionHeight
            if (localY in 0 until regionHeight && localX in 0 until regionWidth) {
                nodeMap[localY][localX] = node.id
            }
        }
    }
    
    fun calculateJpsDistances(node: IndexedNode): Int {
        var packed = 0
        
        Direction.entries.forEachIndexed { index, dir ->
            val distance = calculateJumpDistance(node, dir)
            packed = packed or ((distance and 0xF) shl (index * 4))
        }
        
        return packed
    }
    
    private fun calculateJumpDistance(node: IndexedNode, direction: Direction): Int {
        val startX = node.geoX % regionWidth
        val startY = node.geoY % regionHeight
        
        var x = startX + direction.dx
        var y = startY + direction.dy
        var distance = 0
        
        while (x in 0 until regionWidth && y in 0 until regionHeight && distance < 15) {
            val nodeId = nodeMap[y][x]
            if (nodeId < 0 || !nodes[nodeId].isWalkable) {
                return distance
            }
            
            distance++
            
            if (hasForcedNeighbor(x, y, direction)) {
                return distance
            }
            
            x += direction.dx
            y += direction.dy
        }
        
        return distance.coerceAtMost(15)
    }
    
    private fun hasForcedNeighbor(x: Int, y: Int, direction: Direction): Boolean {
        
        if (direction.dx != 0 && direction.dy != 0) {
            val check1 = isBlocked(x - direction.dx, y)
            val check2 = isBlocked(x, y - direction.dy)
            return check1 || check2
        } else {
            val diag1 = if (direction.dx != 0) {
                isBlocked(x, y - 1) || isBlocked(x + direction.dx, y - 1)
            } else {
                isBlocked(x - 1, y) || isBlocked(x - 1, y + direction.dy)
            }
            return diag1
        }
    }
    private fun isBlocked(x: Int, y: Int): Boolean {
        if (x !in 0 until regionWidth || y !in 0 until regionHeight) return true
        val nodeId = nodeMap[y][x]
        return nodeId < 0 || !nodes[nodeId].isWalkable
    }
}