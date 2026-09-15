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
package ext.mods.gameserver.geoengine.pathfinding
import ext.mods.Config
import ext.mods.commons.util.PriorityQueueSet
import ext.mods.gameserver.geoengine.GeoEngine
import ext.mods.gameserver.geoengine.PeaceZoneCollisionManager
import ext.mods.gameserver.geoengine.geodata.ABlock
import ext.mods.gameserver.geoengine.geodata.GeoStructure
import ext.mods.gameserver.model.actor.Creature
import ext.mods.gameserver.model.actor.Playable
import ext.mods.gameserver.model.location.Location
import ext.mods.gameserver.network.serverpackets.ExServerPrimitive
import java.awt.Color
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import ext.mods.config.ConfigGeoengine
class PathFinder {
    private val _opened = PriorityQueueSet<Node>()
    private val _closed = HashSet<Node>()
    
    private var _gtx: Int = 0
    private var _gty: Int = 0
    private var _gtz: Int = 0
    
    private var _current: Node? = null
    
    private val _currentTime: Long = System.currentTimeMillis()
    private val _geoEngine = GeoEngine.getInstance()
    
    fun findPath(
        gox: Int, goy: Int, goz: Int,
        gtx: Int, gty: Int, gtz: Int,
        debug: ExServerPrimitive?
    ): List<Location> {
        return findPathLegacy(gox, goy, goz, gtx, gty, gtz, debug)
    }
    
    fun findPath(
        creature: Creature?,
        gox: Int, goy: Int, goz: Int,
        gtx: Int, gty: Int, gtz: Int,
        debug: ExServerPrimitive?
    ): List<Location> {
        if (creature == null) {
            return findPathLegacy(gox, goy, goz, gtx, gty, gtz, debug)
        }
        if (PeaceZoneCollisionManager.canIgnoreCreatureCollision(creature)) {
            return findSimplifiedPath(gox, goy, goz, gtx, gty, gtz, debug)
        }
        val rawPath = findPathLegacy(gox, goy, goz, gtx, gty, gtz, debug)
        
        if (rawPath.isEmpty() || rawPath.size < 2) {
            return rawPath
        }
        return applyPathOptimizations(rawPath, creature, debug)
    }
    
    private fun findSimplifiedPath(
        gox: Int, goy: Int, goz: Int,
        gtx: Int, gty: Int, gtz: Int,
        debug: ExServerPrimitive?
    ): List<Location> {
        if (_geoEngine.canMove(gox, goy, goz, gtx, gty, gtz, debug)) {
            if (debug != null) {
                debug.addLine("Simplified Path (Direct)", Color.CYAN, true, gox, goy, goz, gtx, gty, gtz)
            }
            return listOf(Location(gtx, gty, gtz))
        }
        
        return findPathLegacy(gox, goy, goz, gtx, gty, gtz, debug)
    }
    
    private fun findPathWithCollisionBox(
        creature: Creature,
        gox: Int, goy: Int, goz: Int,
        gtx: Int, gty: Int, gtz: Int,
        debug: ExServerPrimitive?
    ): List<Location> {
        val collisionRadius = creature.collisionRadius
        if (_geoEngine.canMoveWithCollisionBox(gox, goy, goz, gtx, gty, gtz, collisionRadius, debug)) {
            return listOf(Location(gtx, gty, gtz))
        }
        val path = findPathLegacy(gox, goy, goz, gtx, gty, gtz, debug).toMutableList()
        if (path.size < 2) return path
        return optimizePathWithCollisionBox(path, collisionRadius, debug)
    }
    
    private fun optimizePathWithCollisionBox(
        path: MutableList<Location>,
        collisionRadius: Double,
        debug: ExServerPrimitive?
    ): List<Location> {
        if (path.size < 3) return path
        val optimized = mutableListOf<Location>()
        optimized.add(path[0])
        var nodeA = path[0]
        var nodeBIndex = 1
        while (nodeBIndex < path.size) {
            val nodeB = path[nodeBIndex]
            var farthestIndex = nodeBIndex
            for (i in nodeBIndex + 1 until path.size) {
                val nodeC = path[i]
                
                if (_geoEngine.canMoveWithCollisionBox(
                    nodeA.x, nodeA.y, nodeA.z,
                    nodeC.x, nodeC.y, nodeC.z,
                    collisionRadius, null
                )) {
                    farthestIndex = i
                } else {
                    break
                }
            }
            if (farthestIndex > nodeBIndex) {
                optimized.add(path[farthestIndex])
                nodeA = path[farthestIndex]
                nodeBIndex = farthestIndex + 1
                
                if (debug != null && nodeBIndex <= path.size) {
                    debug.addPoint(Color.RED, nodeB.x, nodeB.y, nodeB.z)
                }
            } else {
                optimized.add(nodeB)
                nodeA = nodeB
                nodeBIndex++
                
                if (debug != null) {
                    debug.addPoint(Color.GREEN, nodeB.x, nodeB.y, nodeB.z)
                }
            }
        }
        return optimized
    }
    
    private fun applyPathOptimizations(
        path: List<Location>,
        creature: Creature?,
        debug: ExServerPrimitive?
    ): List<Location> {
        if (path.size < 2) return path
        val smoothed = smoothPath(path, creature, debug)
        if (ConfigGeoengine.ENABLE_PATH_SMOOTHING && smoothed.size >= 3) {
            return applyCurveInterpolation(smoothed, debug)
        }
        return smoothed
    }
    
    private fun smoothPath(
        path: List<Location>,
        creature: Creature?,
        debug: ExServerPrimitive?
    ): List<Location> {
        if (path.size < 3) return path
        val smoothed = mutableListOf<Location>()
        smoothed.add(path[0])
        var currentIndex = 0
        while (currentIndex < path.size - 1) {
            var farthestIndex = currentIndex + 1
            for (i in currentIndex + 2 until path.size) {
                val current = path[currentIndex]
                val target = path[i]
                val heightDiff = abs(target.z - current.z)
                if (heightDiff > 150) break
                if (_geoEngine.canMoveToTarget(
                    current.x, current.y, current.z,
                    target.x, target.y, target.z
                )) {
                    farthestIndex = i
                } else {
                    break
                }
            }
            if (farthestIndex > currentIndex) {
                smoothed.add(path[farthestIndex])
            }
            currentIndex = farthestIndex
        }
        if (smoothed.lastOrNull() != path.lastOrNull()) {
            smoothed.add(path.last())
        }
        return smoothed
    }
    
    private fun applyCurveInterpolation(
        path: List<Location>,
        debug: ExServerPrimitive?
    ): List<Location> {
        if (path.size < 3) return path
        val interpolated = mutableListOf<Location>()
        interpolated.add(path[0])
        for (i in 0 until path.size - 1) {
            val p0 = if (i > 0) path[i - 1] else path[i]
            val p1 = path[i]
            val p2 = path[i + 1]
            val p3 = if (i + 2 < path.size) path[i + 2] else path[i + 1]
            val distance = p1.distance3D(p2)
            val calculatedSteps = (distance / ConfigGeoengine.OBSTACLE_SMOOTHING_DISTANCE.toDouble()).toInt().coerceAtMost(10)
            val steps = if (calculatedSteps < 2) 2 else calculatedSteps
            val stepsDouble = steps.toDouble()
            for (j in 1 until steps) {
                val t = j.toDouble() / stepsDouble
                val interpolatedPoint = catmullRomSpline(p0, p1, p2, p3, t)
                
                if (_geoEngine.canMoveToTarget(
                    p1.x, p1.y, p1.z,
                    interpolatedPoint.x, interpolatedPoint.y, interpolatedPoint.z
                )) {
                    interpolated.add(interpolatedPoint)
                    
                    if (debug != null) {
                        debug.addPoint(Color.BLUE, interpolatedPoint.x, interpolatedPoint.y, interpolatedPoint.z)
                    }
                }
            }
            interpolated.add(p2)
        }
        return interpolated
    }
    
    private fun catmullRomSpline(
        p0: Location,
        p1: Location,
        p2: Location,
        p3: Location,
        t: Double
    ): Location {
        val t2 = t * t
        val t3 = t2 * t
        val x = 0.5 * (
            (2 * p1.x) +
            (-p0.x + p2.x) * t +
            (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
            (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3
        )
        val y = 0.5 * (
            (2 * p1.y) +
            (-p0.y + p2.y) * t +
            (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
            (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3
        )
        val z = p1.z + (p2.z - p1.z) * t
        val zInt = z.toInt()
        val xInt = x.toInt()
        val yInt = y.toInt()
        val finalZ = _geoEngine.getHeight(xInt, yInt, zInt).toInt()
        return Location(xInt, yInt, finalZ)
    }
    
    private fun findPathLegacy(
        gox: Int, goy: Int, goz: Int,
        gtx: Int, gty: Int, gtz: Int,
        debug: ExServerPrimitive?
    ): List<Location> {
        _gtx = gtx
        _gty = gty
        _gtz = gtz
        
        _opened.clear()
        _closed.clear()
        val startNswe = _geoEngine.getNsweNearest(gox, goy, goz)
        
        _current = Node(gox, goy, goz, startNswe)
        _current!!.setCost(null, 0, getCostH(gox, goy, goz))
        
        _opened.add(_current!!)
        var count = 0
        while (!_opened.isEmpty() && count < ConfigGeoengine.MAX_ITERATIONS) {
            _current = _opened.poll()
            
            if (_current == null) break 
            
            if (_current!!.geoX == _gtx && _current!!.geoY == _gty && 
                abs(_current!!.z - _gtz) < GeoStructure.CELL_HEIGHT * 2) {
                return constructPath(debug)
            }
            _closed.add(_current!!)
            expand(_current!!)
            count++
        }
        return emptyList()
    }
    
    private fun constructPath(debug: ExServerPrimitive?): List<Location> {
        val path = LinkedList<Location>()
        var dx = 0
        var dy = 0
        
        var node = _current
        var parent = node?.parent
        while (parent != null) {
            val nx = parent.geoX - node!!.geoX
            val ny = parent.geoY - node.geoY
            if (dx != nx || dy != ny) {
                val worldX = GeoEngine.getWorldX(node.geoX)
                val worldY = GeoEngine.getWorldY(node.geoY)
                path.addFirst(Location(worldX, worldY, node.z))
                
                dx = nx
                dy = ny
            }
            node = parent
            parent = node.parent
        }
        
        if (debug != null && _current != null) {
            val worldX = GeoEngine.getWorldX(_current!!.geoX)
            val worldY = GeoEngine.getWorldY(_current!!.geoY)
            debug.addPoint(
                "${System.currentTimeMillis() - _currentTime}ms",
                Color.RED,
                true,
                worldX,
                worldY,
                _current!!.z + 16
            )
        }
        return path
    }
    
    private fun expand(current: Node) {
        val nswe = current.nswe
        if (nswe == GeoStructure.CELL_FLAG_NONE) return
        val x = current.geoX
        val y = current.geoY
        val z = current.z + GeoStructure.CELL_IGNORE_HEIGHT
        val nsweN = addDirectionalNode(current, x, y, z, nswe, 0, -1, GeoStructure.CELL_FLAG_N)
        val nsweS = addDirectionalNode(current, x, y, z, nswe, 0, 1, GeoStructure.CELL_FLAG_S)
        val nsweW = addDirectionalNode(current, x, y, z, nswe, -1, 0, GeoStructure.CELL_FLAG_W)
        val nsweE = addDirectionalNode(current, x, y, z, nswe, 1, 0, GeoStructure.CELL_FLAG_E)
        addCornerNode(current, x, y, z, nswe, -1, -1, GeoStructure.CELL_FLAG_W, GeoStructure.CELL_FLAG_N, nsweW, nsweN)
        addCornerNode(current, x, y, z, nswe, 1, -1, GeoStructure.CELL_FLAG_E, GeoStructure.CELL_FLAG_N, nsweE, nsweN)
        addCornerNode(current, x, y, z, nswe, -1, 1, GeoStructure.CELL_FLAG_W, GeoStructure.CELL_FLAG_S, nsweW, nsweS)
        addCornerNode(current, x, y, z, nswe, 1, 1, GeoStructure.CELL_FLAG_E, GeoStructure.CELL_FLAG_S, nsweE, nsweS)
    }
    
    private fun addDirectionalNode(
        parent: Node, x: Int, y: Int, z: Int, 
        nswe: Byte, dx: Int, dy: Int, directionFlag: Byte
    ): Byte {
        if ((nswe.toInt() and directionFlag.toInt()) != 0) {
            return addNode(parent, x + dx, y + dy, z, false)
        }
        return GeoStructure.CELL_FLAG_NONE
    }
    
    private fun addCornerNode(
        parent: Node, x: Int, y: Int, z: Int, nswe: Byte, 
        dx: Int, dy: Int, 
        dirFlagX: Byte, dirFlagY: Byte, 
        nsweX: Byte, nsweY: Byte
    ) {
        if ((nsweX.toInt() and dirFlagY.toInt()) != 0 && (nsweY.toInt() and dirFlagX.toInt()) != 0) {
             addNode(parent, x + dx, y + dy, z, true)
        }
    }
    
    private fun addNode(parent: Node, gx: Int, gy: Int, checkZ: Int, diagonal: Boolean): Byte {
        if (gx < 0 || gx >= GeoStructure.GEO_CELLS_X || gy < 0 || gy >= GeoStructure.GEO_CELLS_Y) {
            return GeoStructure.CELL_FLAG_NONE
        }
        val block = _geoEngine.getBlock(gx, gy)
        val index = block.getIndexBelow(gx, gy, checkZ, null)
        
        if (index < 0) return GeoStructure.CELL_FLAG_NONE
        val newZ = block.getHeight(index, null).toInt()
        val nswe = block.getNswe(index, null)
        val node = Node(gx, gy, newZ, nswe)
        if (_closed.contains(node)) return nswe
        
        var weight = if (nswe == GeoStructure.CELL_FLAG_ALL) {
            if (diagonal) ConfigGeoengine.MOVE_WEIGHT_DIAG else ConfigGeoengine.MOVE_WEIGHT
        } else {
            if (diagonal) ConfigGeoengine.OBSTACLE_WEIGHT_DIAG else ConfigGeoengine.OBSTACLE_WEIGHT
        }
        if (ConfigGeoengine.ENABLE_BOUNDARY_CELL_PENALTY && nswe != GeoStructure.CELL_FLAG_NONE &&
            _geoEngine.hasBlockedNeighborAtSameLevel(gx, gy, newZ)) {
            weight += ConfigGeoengine.BOUNDARY_CELL_PENALTY
        }
        val hCost = getCostH(gx, gy, newZ)
        
        val existingNode = _opened.find { it == node }
        if (existingNode != null) {
            if (parent.costG + weight < existingNode.costG) {
                existingNode.setCost(parent, weight, hCost)
                _opened.remove(existingNode)
                _opened.add(existingNode)
            }
        } else {
            node.setCost(parent, weight, hCost)
            _opened.add(node)
        }
        return nswe
    }
    
    private fun getCostH(gx: Int, gy: Int, gz: Int): Int {
        val dx = abs(gx - _gtx)
        val dy = abs(gy - _gty)
        val dz = abs(gz - _gtz) / GeoStructure.CELL_HEIGHT
        
        return (sqrt((dx * dx + dy * dy + dz * dz).toDouble()) * ConfigGeoengine.HEURISTIC_WEIGHT).toInt()
    }
}