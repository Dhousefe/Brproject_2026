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
import ext.mods.commons.logging.CLogger
import ext.mods.gameserver.geoengine.GeoEngine
import ext.mods.gameserver.geoengine.geodata.GeoStructure
import ext.mods.gameserver.model.location.Location
import ext.mods.gameserver.network.serverpackets.ExServerPrimitive
import java.awt.Color
import kotlin.math.*
import ext.mods.config.ConfigGeoengine
import ext.mods.config.ConfigServer
object SmoothObstacleAvoidance {
    
    private val LOGGER = CLogger(SmoothObstacleAvoidance::class.java.name)
    
    private val geoEngine: GeoEngine = GeoEngine.getInstance()
    
    private const val MIN_PATH_SIZE = 3
    private const val MIN_SEGMENT_DISTANCE = 32.0
    private const val MIN_STEPS = 2
    private const val STEP_SIZE = 16
    private const val ANGLE_STEP = 45
    private const val MAX_ANGLE = 360
    private const val MAX_HEIGHT_DIFF = 100
    private const val DISTANCE_CHECK_STEP = 16
    
    private val FLAG_N = GeoStructure.CELL_FLAG_N.toInt()
    private val FLAG_S = GeoStructure.CELL_FLAG_S.toInt()
    private val FLAG_E = GeoStructure.CELL_FLAG_E.toInt()
    private val FLAG_W = GeoStructure.CELL_FLAG_W.toInt()
    private val FLAG_ALL = GeoStructure.CELL_FLAG_ALL.toInt()
    
    private val ANGLE_OFFSETS = (0 until MAX_ANGLE step ANGLE_STEP).map { Math.toRadians(it.toDouble()) }
    
    @JvmStatic
    fun getInstance(): SmoothObstacleAvoidance = this
    
    fun createSmoothPath(originalPath: List<Location>, debug: ExServerPrimitive?): List<Location> {
        return when {
            !ConfigGeoengine.ENABLE_SMOOTH_OBSTACLE_AVOIDANCE || originalPath.isEmpty() -> originalPath
            originalPath.size < MIN_PATH_SIZE -> originalPath
            else -> {
                val smoothPath = ArrayList<Location>(originalPath.size)
                smoothPath.add(originalPath[0])
                
                val pathSet = HashSet<Location>(originalPath.size)
                (1 until originalPath.size - 1).forEach { i ->
                    val current = originalPath[i]
                    val next = originalPath[i + 1]
                    when {
                        current.distance3D(next) < MIN_SEGMENT_DISTANCE -> {
                            smoothPath.add(next)
                            pathSet.add(next)
                        }
                        else -> {
                            val smoothSegment = smoothPathSegment(current, next, debug)
                            
                            when {
                                smoothSegment.isEmpty() -> {  }
                                smoothSegment.any { pathSet.contains(it) } -> {
                                    smoothPath.add(next)
                                    pathSet.add(next)
                                }
                                else -> {
                                    smoothPath.addAll(smoothSegment)
                                    pathSet.addAll(smoothSegment)
                                }
                            }
                        }
                    }
                }
                smoothPath.add(originalPath.last())
                val simplified = simplifyPath(smoothPath)
                if (ConfigServer.DEVELOPER) {
                    LOGGER.debug("SmoothObstacleAvoidance: Path smoothed from {} to {} points (simplified: {})", 
                        originalPath.size, smoothPath.size, simplified.size)
                }
                simplified
            }
        }
    }
    
    private fun smoothPathSegment(start: Location, end: Location, debug: ExServerPrimitive?): List<Location> {
        val distance = start.distance3D(end)
        val steps = max(MIN_STEPS, (distance / ConfigGeoengine.OBSTACLE_SMOOTHING_DISTANCE).toInt())
        
        val smoothed = ArrayList<Location>(steps - 1)
        
        val stepInverse = 1.0 / steps
        for (i in 1 until steps) {
            val ratio = i * stepInverse
            val interpolated = interpolateLocation(start, end, ratio)
            val smoothedPoint = applyObstacleAvoidance(interpolated, start, end, debug)
            smoothed.add(smoothedPoint)
        }
        return smoothed
    }
    
    private fun applyObstacleAvoidance(point: Location, start: Location, end: Location, debug: ExServerPrimitive?): Location {
        val bestPoint = when {
            isPointClear(point) -> point
            else -> {
                val bestPointClone = point.clone()
                findBestAvoidancePoint(point, start, end)?.let { bestPointClone.set(it) }
                bestPointClone
            }
        }
        val finalPoint = applySmoothingLevel(bestPoint, start, end)
        debug?.addPoint(Color.GREEN, finalPoint.x, finalPoint.y, finalPoint.z)
        return finalPoint
    }
    
    private fun findBestAvoidancePoint(blockedPoint: Location, start: Location, end: Location): Location? {
        val smoothingDistance = ConfigGeoengine.OBSTACLE_SMOOTHING_DISTANCE
        val angle = calculateDirectionAngle(start, end)
        return generateSequence(STEP_SIZE) { it + STEP_SIZE }
            .takeWhile { it <= smoothingDistance }
            .flatMap { radius ->
                ANGLE_OFFSETS.asSequence().map { angleOffsetRad ->
                    val testAngle = angle + angleOffsetRad
                    calculateOffsetPoint(blockedPoint, testAngle, radius)
                }
            }
            .firstOrNull { testPoint ->
                isPointClear(testPoint) && isPointReachable(testPoint, start, end)
            }
    }
    
    private fun applySmoothingLevel(point: Location, start: Location, end: Location): Location {
        val smoothingLevel = ConfigGeoengine.PATHFINDING_SMOOTHING_LEVEL
        
        return when {
            smoothingLevel <= 1 -> point
            else -> {
                val smoothingFactor = smoothingLevel / 10.0
                val directRatio = 1.0 - smoothingFactor
                val directX = start.x * directRatio + end.x * (1 - directRatio)
                val directY = start.y * directRatio + end.y * (1 - directRatio)
                val directZ = start.z * directRatio + end.z * (1 - directRatio)
                val smoothedX = point.x * smoothingFactor + directX * (1 - smoothingFactor)
                val smoothedY = point.y * smoothingFactor + directY * (1 - smoothingFactor)
                val smoothedZ = point.z * smoothingFactor + directZ * (1 - smoothingFactor)
                Location(smoothedX.toInt(), smoothedY.toInt(), smoothedZ.toInt())
            }
        }
    }
    
    private fun isPointClear(point: Location): Boolean {
        val nswe = geoEngine.getNswe(point.x, point.y, point.z)
        val nsweInt = nswe.toInt()
        
        return when (nsweInt) {
            0 -> false
            FLAG_ALL -> true
            else -> {
                val freeDirs = listOf(
                    (nsweInt and FLAG_N) != 0,
                    (nsweInt and FLAG_S) != 0,
                    (nsweInt and FLAG_E) != 0,
                    (nsweInt and FLAG_W) != 0
                ).count { it }
                
                freeDirs >= 2
            }
        }
    }
    
    private fun isPointReachable(point: Location, start: Location, end: Location): Boolean {
        return geoEngine.canMoveToTarget(start.x, start.y, start.z, point.x, point.y, point.z) &&
               geoEngine.canMoveToTarget(point.x, point.y, point.z, end.x, end.y, end.z)
    }
    
    private fun interpolateLocation(start: Location, end: Location, ratio: Double): Location {
        val x = start.x + (end.x - start.x) * ratio
        val y = start.y + (end.y - start.y) * ratio
        val z = start.z + (end.z - start.z) * ratio
        return Location(x.toInt(), y.toInt(), z.toInt())
    }
    
    private fun calculateDirectionAngle(start: Location, end: Location): Double {
        val dx = (end.x - start.x).toDouble()
        val dy = (end.y - start.y).toDouble()
        return atan2(dy, dx)
    }
    
    private fun calculateOffsetPoint(center: Location, angle: Double, distance: Int): Location {
        val offsetX = (cos(angle) * distance).toInt()
        val offsetY = (sin(angle) * distance).toInt()
        val newX = center.x + offsetX
        val newY = center.y + offsetY
        val newZ = geoEngine.getHeight(newX, newY, center.z).toInt()
        return Location(newX, newY, newZ)
    }
    
    fun shouldAvoidObstacle(current: Location, target: Location): Boolean {
        if (!ConfigGeoengine.ENABLE_REAL_TIME_OBSTACLE_AVOIDANCE) return false
        val distance = current.distance3D(target)
        if (distance > ConfigGeoengine.OBSTACLE_DETECTION_DISTANCE) return false
        val heightDiff = abs(target.z - current.z)
        if (heightDiff > MAX_HEIGHT_DIFF) return false
        
        return !geoEngine.canMoveToTarget(current.x, current.y, current.z, target.x, target.y, target.z)
    }
    
    fun calculateAvoidanceDirection(current: Location, target: Location): Location {
        val smoothingDistance = ConfigGeoengine.OBSTACLE_SMOOTHING_DISTANCE
        val angle = calculateDirectionAngle(current, target)
        val perpendicularAngle1 = angle + PI / 2
        val perpendicularAngle2 = angle - PI / 2
        val option1 = calculateOffsetPoint(current, perpendicularAngle1, smoothingDistance)
        val option2 = calculateOffsetPoint(current, perpendicularAngle2, smoothingDistance)
        return when {
            isPointClear(option1) -> option1
            isPointClear(option2) -> option2
            else -> target
        }
    }

    /**
     * String-Pulling e Supressão de Nós Colineares Padrão Brproject3.
     * Elimina waypoints redundantes em linha reta e atalha trechos onde há linha de visão desobstruída.
     */
    fun simplifyPath(rawPath: List<Location>): List<Location> {
        if (rawPath.size <= 2) return rawPath
        val path = ArrayList(rawPath)
        
        // 1. Supressão de nós colineares
        var size = path.size
        var i = 2
        while (i < size) {
            val p0 = path[i - 2]
            val p1 = path[i - 1]
            val p2 = path[i]
            if (p0 == p1 || p1 == p2 || isCollinear(p0, p1, p2)) {
                path.removeAt(i - 1)
                size--
                i = max(2, i - 1)
            } else {
                i++
            }
        }
        
        // 2. String-pulling com canMoveToTarget
        var startIdx = 0
        while (startIdx < path.size - 2) {
            val start = path[startIdx]
            var endIdx = path.size - 1
            while (endIdx > startIdx + 1) {
                val end = path[endIdx]
                if (geoEngine.canMoveToTarget(start.x, start.y, start.z, end.x, end.y, end.z)) {
                    while (startIdx + 1 < endIdx) {
                        path.removeAt(startIdx + 1)
                        endIdx--
                    }
                    break
                }
                endIdx--
            }
            startIdx++
        }
        return path
    }
    
    private fun isCollinear(p0: Location, p1: Location, p2: Location): Boolean {
        return if ((p0.x != p2.x || p2.x != p1.x) && (p0.y != p2.y || p2.y != p1.y)) {
            val dx1 = (p0.x - p1.x).toLong()
            val dy1 = (p0.y - p1.y).toLong()
            val dx2 = (p1.x - p2.x).toLong()
            val dy2 = (p1.y - p2.y).toLong()
            dx1 * dy2 == dx2 * dy1
        } else {
            true
        }
    }
}