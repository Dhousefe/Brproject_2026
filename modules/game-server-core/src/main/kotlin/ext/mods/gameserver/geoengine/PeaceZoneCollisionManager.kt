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
package ext.mods.gameserver.geoengine
import ext.mods.Config
import ext.mods.gameserver.enums.ZoneId
import ext.mods.gameserver.model.actor.Creature
import ext.mods.gameserver.model.actor.Player
object PeaceZoneCollisionManager {
    
    fun canIgnoreCreatureCollision(creature: Creature?): Boolean {
        return when {
            creature == null -> false
            !isFeatureEnabled() -> false
            !creature.isInsideZone(ZoneId.PEACE) -> false
            else -> {
                val zoneIds = getNoCollisionZoneIds()
                when {
                    zoneIds.isEmpty() -> true
                    else -> true
                }
            }
        }
    }
    
    fun canBypassCollision(creature1: Creature?, creature2: Creature?): Boolean {
        return when {
            !isFeatureEnabled() -> false
            creature1 == null || creature2 == null -> false
            else -> isInNoCollisionZone(creature1) && isInNoCollisionZone(creature2)
        }
    }
    private fun isInNoCollisionZone(creature: Creature): Boolean {
        return creature.isInsideZone(ZoneId.PEACE)
    }
    
    private fun isFeatureEnabled(): Boolean {
        return try {
            val field = Config::class.java.getDeclaredField("ENABLE_PEACE_ZONE_NO_COLLISION")
            field.isAccessible = true
            field.getBoolean(null)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getNoCollisionZoneIds(): List<Int> {
        return try {
            val field = Config::class.java.getDeclaredField("PEACE_ZONE_NO_COLLISION_ZONE_IDS")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (field.get(null) as? IntArray)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}