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
import ext.mods.gameserver.model.actor.Creature
import ext.mods.gameserver.model.actor.Playable
import ext.mods.gameserver.model.location.Location
import ext.mods.gameserver.network.serverpackets.ExServerPrimitive
import java.awt.Color
import java.util.Collections
class AdvancedPathFinder {
    
    private val geoEngine = GeoEngine.getInstance()
    
    fun findPath(creature: Creature?, tx: Int, ty: Int, tz: Int, debug: ExServerPrimitive?): List<Location> {
        if (creature == null) return emptyList()
        val ox = creature.x
        val oy = creature.y
        val oz = creature.z
        if (PeaceZoneCollisionManager.canIgnoreCreatureCollision(creature)) {
            return findSimplifiedPath(ox, oy, oz, tx, ty, tz, debug)
        }
        return geoEngine.findPath(ox, oy, oz, tx, ty, tz, creature is Playable, debug)
    }
    private fun findSimplifiedPath(ox: Int, oy: Int, oz: Int, tx: Int, ty: Int, tz: Int, debug: ExServerPrimitive?): List<Location> {
        if (geoEngine.canMove(ox, oy, oz, tx, ty, tz, debug)) {
            return listOf(Location(tx, ty, tz))
        }
        return geoEngine.findPath(ox, oy, oz, tx, ty, tz, true, debug)
    }
    private fun findPathWithCollisionBox(creature: Creature, tx: Int, ty: Int, tz: Int, debug: ExServerPrimitive?): List<Location> {
        val ox = creature.x
        val oy = creature.y
        val oz = creature.z
        val collisionRadius = creature.collisionRadius
        if (geoEngine.canMoveWithCollisionBox(ox, oy, oz, tx, ty, tz, collisionRadius, debug)) {
            return listOf(Location(tx, ty, tz))
        }
        val path = geoEngine.findPath(ox, oy, oz, tx, ty, tz, creature is Playable, debug).toMutableList()
        if (path.size < 2) return path
        return optimizePathWithCollisionBox(path, collisionRadius, debug)
    }
    private fun optimizePathWithCollisionBox(path: MutableList<Location>, collisionRadius: Double, debug: ExServerPrimitive?): List<Location> {
        if (path.size < 3) return path
        val pointIterator = path.listIterator()
        var nodeAx = path[0].x
        var nodeAy = path[0].y
        var nodeAz = path[0].z
        
        var nodeB = pointIterator.next()
        while (pointIterator.hasNext()) {
            val nextIndex = pointIterator.nextIndex()
            val nodeC = path[nextIndex]
            if (geoEngine.canMoveWithCollisionBox(nodeAx, nodeAy, nodeAz, nodeC.x, nodeC.y, nodeC.z, collisionRadius, null)) {
                pointIterator.remove()
                if (debug != null) debug.addPoint(Color.RED, nodeB.x, nodeB.y, nodeB.z)
            } else {
                nodeAx = nodeB.x
                nodeAy = nodeB.y
                nodeAz = nodeB.z
                if (debug != null) debug.addPoint(Color.GREEN, nodeB.x, nodeB.y, nodeB.z)
            }
            nodeB = pointIterator.next()
        }
        return path
    }
}