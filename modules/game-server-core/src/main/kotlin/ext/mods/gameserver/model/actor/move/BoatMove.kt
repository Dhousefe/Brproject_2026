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
package ext.mods.gameserver.model.actor.move
import ext.mods.commons.logging.CLogger
import ext.mods.gameserver.enums.actors.MoveType
import ext.mods.gameserver.enums.boats.BoatState
import ext.mods.gameserver.model.actor.Boat
import ext.mods.gameserver.model.location.BoatLocation
import ext.mods.gameserver.network.serverpackets.OnVehicleCheckLocation
import ext.mods.gameserver.network.serverpackets.VehicleDeparture
import ext.mods.gameserver.network.serverpackets.VehicleInfo
import ext.mods.gameserver.network.serverpackets.VehicleStarted
class BoatMove(actor: Boat) : CreatureMove<Boat>(actor) {
    private var currentPath: Array<BoatLocation>? = null
    private var pathIndex: Int = 0
    private var pathLength: Int = 0
    init {
        addMoveType(MoveType.FLY)
    }
    override fun stop() {
        cancelMoveTask()
        _actor.broadcastPacket(VehicleStarted(_actor, 0))
        _actor.broadcastPacket(VehicleInfo(_actor))
    }
    override fun updatePosition(): Boolean {
        val result = super.updatePosition()
        val passengers = _actor.passengers
        if (passengers.isNotEmpty()) {
            for (player in passengers) {
                player.setXYZ(_actor)
                player.revalidateZone(false)
                
                player.sendPacket(OnVehicleCheckLocation(_actor))
            }
        }
        return result
    }
    
    fun onArrival() {
        if (!isValidPath()) {
            LOGGER.warn("Boat arrival error: Path is null or empty. Boat: ${_actor.name}")
            setBoatDocked()
            return
        }
        currentPath?.getOrNull(pathIndex)?.arrivalMessages?.takeIf { it.isNotEmpty() }?.let { messages ->
            _actor.engine.broadcast(*messages)
        }
        pathIndex++
        when {
            pathIndex >= pathLength -> {
                setBoatDocked()
            }
            
            pathIndex == pathLength - 1 -> {
                _actor.engine.state = BoatState.READY_TO_MOVE_TO_DOCK
                moveToNextSegment()
            }
            
            else -> {
                moveToNextSegment()
            }
        }
    }
    fun moveToBoatLocation(loc: BoatLocation) {
        
        moveToLocation(loc, false)
        _actor.broadcastPacket(VehicleDeparture(_actor))
    }
    fun executePath(path: Array<BoatLocation>?) {
        if (path == null || path.isEmpty()) {
            LOGGER.warn("Invalid path provided to executePath. Boat: ${_actor.name}")
            return
        }
        pathIndex = 0
        currentPath = path.clone()
        pathLength = currentPath!!.size
        moveToNextSegment()
        _actor.broadcastPacket(VehicleStarted(_actor, 1))
    }
    private fun moveToNextSegment() {
        if (!isValidPath()) {
            setBoatDocked()
            return
        }
        if (pathIndex >= pathLength) {
            setBoatDocked()
            return
        }
        val loc = currentPath!![pathIndex]
        
        loc.departureMessages?.takeIf { it.isNotEmpty() }?.let { messages ->
            _actor.engine.broadcast(*messages)
        }
        
        moveToBoatLocation(loc)
    }
    private fun isValidPath(): Boolean {
        return currentPath != null && currentPath!!.isNotEmpty()
    }
    private fun setBoatDocked() {
        _actor.engine.state = BoatState.DOCKED
        stop()
    }
    fun getCurrentPathIndex(): Int = pathIndex
    fun getPathLength(): Int = pathLength
    fun isPathCompleted(): Boolean = pathIndex >= pathLength
}