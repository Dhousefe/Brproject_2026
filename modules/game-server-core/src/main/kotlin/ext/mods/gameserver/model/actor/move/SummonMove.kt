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
import ext.mods.Config
import ext.mods.gameserver.model.actor.Creature
import ext.mods.gameserver.model.actor.Summon
import ext.mods.gameserver.model.location.Location
import ext.mods.config.ConfigGeoengine
class SummonMove(actor: Summon) : CreatureMove<Summon>(actor) {
    override fun moveToLocation(destination: Location, pathfinding: Boolean) {
        super.moveToLocation(destination, pathfinding && ConfigGeoengine.SISTEMA_PATHFINDING)
    }
    override fun shouldStopMovementTask(): Boolean {
        return false
    }
    
    override fun avoidAttack(attacker: Creature) {
        val owner = _actor.owner ?: return
        
        if (!_actor.attack.isAttackingNow && 
            !_actor.cast.isCastingNow && 
            !_actor.isMoving) {
            
            if (!_actor.isIn2DRadius(owner, 150)) {
                val dest = Location(owner.x, owner.y, owner.z)
                moveToLocation(dest, true)
            }
        }
    }
}