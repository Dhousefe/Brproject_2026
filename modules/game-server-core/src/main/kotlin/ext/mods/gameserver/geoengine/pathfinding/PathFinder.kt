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
        val dx = abs(gtx - gox)
        val dy = abs(gty - goy)
        val neededSize = maxOf(dx, dy) + 16
        if (neededSize > PathFindBuffers.MAX_MAP_SIZE) {
            return emptyList()
        }

        val buffer = PathFindBuffers.alloc(neededSize) ?: return emptyList()
        val startTime = System.currentTimeMillis()

        try {
            buffer.offsetX = minOf(gox, gtx) - 8
            buffer.offsetY = minOf(goy, gty) - 8

            val sx = gox - buffer.offsetX
            val sy = goy - buffer.offsetY
            if (sx < 0 || sx >= buffer.mapSize || sy < 0 || sy >= buffer.mapSize) {
                return emptyList()
            }

            val startNswe = _geoEngine.getNsweNearest(gox, goy, goz)
            val startNode = buffer.nodes[sx][sy].set(gox, goy, goz, startNswe)
            startNode.setCost(null, 0, getCostH(gox, goy, goz, gtx, gty, gtz))
            startNode.state = PathFindBuffers.GeoNode.STATE_OPENED
            buffer.markTouched(startNode)
            buffer.open.add(startNode)

            var count = 0
            while (!buffer.open.isEmpty() && count < ConfigGeoengine.MAX_ITERATIONS) {
                val current = buffer.open.poll() ?: break

                if (current.geoX == gtx && current.geoY == gty &&
                    abs(current.z - gtz) < GeoStructure.CELL_HEIGHT * 2) {
                    buffer.successUses++
                    return constructPath(current, debug, startTime)
                }

                current.state = PathFindBuffers.GeoNode.STATE_CLOSED
                expand(buffer, current, gtx, gty, gtz)
                count++
            }

            if (count >= ConfigGeoengine.MAX_ITERATIONS) {
                buffer.overtimeUses++
            }
            return emptyList()
        } finally {
            PathFindBuffers.recycle(buffer)
        }
    }

    private fun constructPath(targetNode: PathFindBuffers.GeoNode, debug: ExServerPrimitive?, startTime: Long): List<Location> {
        val path = LinkedList<Location>()
        var dx = 0
        var dy = 0

        var node: PathFindBuffers.GeoNode? = targetNode
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

        if (debug != null) {
            val worldX = GeoEngine.getWorldX(targetNode.geoX)
            val worldY = GeoEngine.getWorldY(targetNode.geoY)
            debug.addPoint(
                "${System.currentTimeMillis() - startTime}ms",
                Color.RED,
                true,
                worldX,
                worldY,
                targetNode.z + 16
            )
        }
        return path
    }

    private fun expand(
        buffer: PathFindBuffers.PathFindBuffer,
        current: PathFindBuffers.GeoNode,
        gtx: Int, gty: Int, gtz: Int
    ) {
        val nswe = current.nswe
        if (nswe == GeoStructure.CELL_FLAG_NONE) return
        val x = current.geoX
        val y = current.geoY
        val z = current.z + GeoStructure.CELL_IGNORE_HEIGHT

        // 4 passos ortogonais
        val nsweN = addDirectionalNode(buffer, current, x, y, z, 0, -1, GeoStructure.CELL_FLAG_N, gtx, gty, gtz)
        val nsweS = addDirectionalNode(buffer, current, x, y, z, 0, 1, GeoStructure.CELL_FLAG_S, gtx, gty, gtz)
        val nsweW = addDirectionalNode(buffer, current, x, y, z, -1, 0, GeoStructure.CELL_FLAG_W, gtx, gty, gtz)
        val nsweE = addDirectionalNode(buffer, current, x, y, z, 1, 0, GeoStructure.CELL_FLAG_E, gtx, gty, gtz)

        // Validação diagonal ortogonal estrita de 4 bits (Anti-Corner Cutting - Lucera2)
        // Noroeste (NW: x - 1, y - 1)
        if ((nswe.toInt() and GeoStructure.CELL_FLAG_W.toInt()) != 0 && (nswe.toInt() and GeoStructure.CELL_FLAG_N.toInt()) != 0) {
            if ((nsweW.toInt() and GeoStructure.CELL_FLAG_N.toInt()) != 0 && (nsweN.toInt() and GeoStructure.CELL_FLAG_W.toInt()) != 0) {
                addCornerNode(buffer, current, x - 1, y - 1, z, gtx, gty, gtz)
            }
        }
        // Nordeste (NE: x + 1, y - 1)
        if ((nswe.toInt() and GeoStructure.CELL_FLAG_E.toInt()) != 0 && (nswe.toInt() and GeoStructure.CELL_FLAG_N.toInt()) != 0) {
            if ((nsweE.toInt() and GeoStructure.CELL_FLAG_N.toInt()) != 0 && (nsweN.toInt() and GeoStructure.CELL_FLAG_E.toInt()) != 0) {
                addCornerNode(buffer, current, x + 1, y - 1, z, gtx, gty, gtz)
            }
        }
        // Sudoeste (SW: x - 1, y + 1)
        if ((nswe.toInt() and GeoStructure.CELL_FLAG_W.toInt()) != 0 && (nswe.toInt() and GeoStructure.CELL_FLAG_S.toInt()) != 0) {
            if ((nsweW.toInt() and GeoStructure.CELL_FLAG_S.toInt()) != 0 && (nsweS.toInt() and GeoStructure.CELL_FLAG_W.toInt()) != 0) {
                addCornerNode(buffer, current, x - 1, y + 1, z, gtx, gty, gtz)
            }
        }
        // Sudeste (SE: x + 1, y + 1)
        if ((nswe.toInt() and GeoStructure.CELL_FLAG_E.toInt()) != 0 && (nswe.toInt() and GeoStructure.CELL_FLAG_S.toInt()) != 0) {
            if ((nsweE.toInt() and GeoStructure.CELL_FLAG_S.toInt()) != 0 && (nsweS.toInt() and GeoStructure.CELL_FLAG_E.toInt()) != 0) {
                addCornerNode(buffer, current, x + 1, y + 1, z, gtx, gty, gtz)
            }
        }
    }

    private fun addDirectionalNode(
        buffer: PathFindBuffers.PathFindBuffer,
        parent: PathFindBuffers.GeoNode,
        x: Int, y: Int, z: Int,
        dx: Int, dy: Int, directionFlag: Byte,
        gtx: Int, gty: Int, gtz: Int
    ): Byte {
        if ((parent.nswe.toInt() and directionFlag.toInt()) != 0) {
            return addNode(buffer, parent, x + dx, y + dy, z, false, gtx, gty, gtz)
        }
        return GeoStructure.CELL_FLAG_NONE
    }

    private fun addCornerNode(
        buffer: PathFindBuffers.PathFindBuffer,
        parent: PathFindBuffers.GeoNode,
        gx: Int, gy: Int, z: Int,
        gtx: Int, gty: Int, gtz: Int
    ) {
        addNode(buffer, parent, gx, gy, z, true, gtx, gty, gtz)
    }

    private fun addNode(
        buffer: PathFindBuffers.PathFindBuffer,
        parent: PathFindBuffers.GeoNode,
        gx: Int, gy: Int, checkZ: Int, diagonal: Boolean,
        gtx: Int, gty: Int, gtz: Int
    ): Byte {
        if (gx < 0 || gx >= GeoStructure.GEO_CELLS_X || gy < 0 || gy >= GeoStructure.GEO_CELLS_Y) {
            return GeoStructure.CELL_FLAG_NONE
        }

        val bx = gx - buffer.offsetX
        val by = gy - buffer.offsetY
        if (bx < 0 || bx >= buffer.mapSize || by < 0 || by >= buffer.mapSize) {
            return GeoStructure.CELL_FLAG_NONE
        }

        val node = buffer.nodes[bx][by]
        if (node.state == PathFindBuffers.GeoNode.STATE_CLOSED) {
            return node.nswe
        }

        if (!node.isSet()) {
            val block = _geoEngine.getBlock(gx, gy)
            val index = block.getIndexBelow(gx, gy, checkZ, null)
            if (index < 0) return GeoStructure.CELL_FLAG_NONE
            val newZ = block.getHeight(index, null).toInt()
            val nswe = block.getNswe(index, null)
            node.set(gx, gy, newZ, nswe)
            buffer.markTouched(node)
        }

        val nswe = node.nswe
        var weight = if (nswe == GeoStructure.CELL_FLAG_ALL) {
            if (diagonal) ConfigGeoengine.MOVE_WEIGHT_DIAG else ConfigGeoengine.MOVE_WEIGHT
        } else {
            if (diagonal) ConfigGeoengine.OBSTACLE_WEIGHT_DIAG else ConfigGeoengine.OBSTACLE_WEIGHT
        }
        if (ConfigGeoengine.ENABLE_BOUNDARY_CELL_PENALTY && nswe != GeoStructure.CELL_FLAG_NONE &&
            _geoEngine.hasBlockedNeighborAtSameLevel(gx, gy, node.z)) {
            weight += ConfigGeoengine.BOUNDARY_CELL_PENALTY
        }
        val hCost = getCostH(gx, gy, node.z, gtx, gty, gtz)

        if (node.state == PathFindBuffers.GeoNode.STATE_OPENED) {
            if (parent.costG + weight < node.costG) {
                buffer.open.remove(node)
                node.setCost(parent, weight, hCost)
                buffer.open.add(node)
            }
        } else {
            node.setCost(parent, weight, hCost)
            node.state = PathFindBuffers.GeoNode.STATE_OPENED
            buffer.open.add(node)
        }
        return nswe
    }

    private fun getCostH(gx: Int, gy: Int, gz: Int, gtx: Int, gty: Int, gtz: Int): Int {
        val dx = abs(gx - gtx)
        val dy = abs(gy - gty)
        val dz = abs(gz - gtz) / GeoStructure.CELL_HEIGHT

        if (ConfigGeoengine.ENABLE_SIMD_HEURISTICS) {
            // Octile Heuristic 3D: perfectly matches 8-connected grid geometry without expensive sqrt or floating-point conversions
            val minXY = if (dx < dy) dx else dy
            val maxXY = if (dx > dy) dx else dy
            val octile2D = maxXY * ConfigGeoengine.MOVE_WEIGHT + minXY * (ConfigGeoengine.MOVE_WEIGHT_DIAG - ConfigGeoengine.MOVE_WEIGHT)
            return octile2D + dz * ConfigGeoengine.MOVE_WEIGHT
        }

        return (sqrt((dx * dx + dy * dy + dz * dz).toDouble()) * ConfigGeoengine.HEURISTIC_WEIGHT).toInt()
    }
}