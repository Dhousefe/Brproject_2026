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
import ext.mods.commons.logging.CLogger
import ext.mods.commons.random.Rnd
import ext.mods.gameserver.enums.AiEventType
import ext.mods.gameserver.enums.actors.MoveType
import ext.mods.gameserver.enums.actors.NpcSkillType
import ext.mods.gameserver.model.actor.Creature
import ext.mods.gameserver.model.actor.Npc
import ext.mods.gameserver.model.actor.ai.type.NpcAI
import ext.mods.gameserver.enums.skills.Stats
import ext.mods.gameserver.model.location.Location
import ext.mods.gameserver.network.serverpackets.StopMove
import ext.mods.gameserver.skills.L2Skill
import ext.mods.gameserver.skills.basefuncs.FuncMul
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.sqrt
import ext.mods.config.ConfigNpcs
public class NpcMove(actor: Npc) : CreatureMove<Npc>(actor) {
    companion object {
        private val LOGGER = CLogger(NpcMove::class.java.name)
    }
    
    private val frontSlowOwner = Any()
    private var frontSlowApplied = false
    override fun offensiveFollowTask(target: Creature, offset: Int) {
        val currentTask = _followTask
        
        if (currentTask == null || currentTask.isCancelled || target.isAlikeDead) {
            clearFrontSlow()
            cancelFollowTask()
            return
        }
        
        if (!_actor.knows(target)) {
            val ai = _actor.ai
            if (ai is NpcAI<*>) {
                ai.stopFollow()
            } else {
                _actor.broadcastPacket(StopMove(_actor))
                ai.notifyEvent(AiEventType.THINK, null, null)
            }
            if (ConfigNpcs.DEBUG_MELEE_ATTACK && offset <= 200) {
                LOGGER.info("[MeleeDebug] follow: lostKnownlist npc={} target={}", _actor.objectId, target.objectId)
            }
            clearFrontSlow()
            cancelFollowTask()
            return
        }
        val targetLoc = target.position
        val dist = _actor.distance3D(targetLoc)
        
        if (applySoftRepulsion(target)) {
            return
        }
        
        if (offset <= 200 && dist <= 300 && !_actor.getAllSkillsDisabled() && !_actor.cast.isCastingNow) {
            val magicSkill = selectMagicSkill()
            if (magicSkill != null) {
                val mpConsume = _actor.status.getMpConsume(magicSkill)
                val hasMp = mpConsume <= 0 || mpConsume <= _actor.status.mp
                val chance = if (hasMp) 60 else 30
                if (Rnd.get(100) < chance) {
                    val ai = _actor.ai
                    if (ai is NpcAI<*>) {
                        ai.addCastDesire(target, magicSkill, 100000.0, true, true)
                        return
                    }
                }
            }
        }
        
        val layer = (_actor.objectId % 3)
        val layeredOffset = if (offset > 200) {
            (offset + (layer * 20)).coerceAtLeast(100)
        } else {
            offset
        }
        
        val totalRange = layeredOffset + _actor.collisionRadius + target.collisionRadius
        
        if (offset <= 100 && tryRouteDeviationForFrontBlocker(target, offset)) {
            return
        }
        
        updateFrontSlow(target, offset)
        
        val attackMargin = ConfigNpcs.MONSTER_MAX_RANGE
        val minSafeRange = (totalRange - attackMargin).coerceAtLeast(0.0)
        var maxSafeRange = totalRange + attackMargin
        
        if (dist > maxSafeRange) {
            val blockers = _actor.getKnownTypeInRadius(Creature::class.java, dist.toInt())
            for (blocker in blockers) {
                if (blocker !is Npc || blocker == _actor || blocker == target || blocker.isAlikeDead) continue
                val distToBlocker = _actor.distance3D(blocker)
                val blockerToTarget = blocker.distance3D(target)
                if (Math.abs((distToBlocker + blockerToTarget) - dist) < 35.0) {
                    val adjustedRange = maxSafeRange + (blocker.collisionRadius * 2.0)
                    if (dist <= adjustedRange) {
                        maxSafeRange = adjustedRange
                        break
                    }
                }
            }
        }
        if (dist in minSafeRange..maxSafeRange) {
            _actor.position.setHeadingTo(targetLoc)
            
            if (offset <= 200) {
                if (ConfigNpcs.DEBUG_MELEE_ATTACK) {
                    LOGGER.info("[MeleeDebug] follow: meleeArrived npc={} target={} dist={} range={}", 
                        _actor.objectId, target.objectId, dist, totalRange)
                }
                clearFrontSlow()
                stop()
                
                val ai = _actor.ai
                if (ai is NpcAI<*>) {
                    ai.notifyEvent(AiEventType.ARRIVED, null, null)
                } else {
                    ai.notifyEvent(AiEventType.THINK, null, null)
                }
                return
            }
            
            if (Math.abs(_separationForceX) < 0.1 && Math.abs(_separationForceY) < 0.1) {
                stop()
                val ai = _actor.ai
                if (ai is NpcAI<*>) {
                    ai.notifyEvent(AiEventType.ARRIVED, null, null)
                } else {
                    ai.notifyEvent(AiEventType.THINK, null, null)
                }
                return
            }
        }
        
        val isBlocked = wouldCollideInPath(targetLoc)
        if (dist > maxSafeRange || isBlocked) {
            val bestSlot = findBestAttackSlot(target, offset)
            
            if (bestSlot != null) {
                if (_destination.distance3D(bestSlot) > 25) {
                    val usePathfinding = isBlocked || dist > 300
                    moveToLocation(bestSlot, usePathfinding)
                    return
                }
            } else {
                if (isBlocked) {
                    moveToLocation(targetLoc, true)
                    return
                }
            }
        }
        
        if (dist > maxSafeRange) {
            moveToLocation(targetLoc, true)
            return
        }
        
    }
    
    private fun applySoftRepulsion(target: Creature): Boolean {
        val neighbors = _actor.getKnownTypeInRadius(Creature::class.java, 120)
        if (neighbors.isNullOrEmpty()) return false
    
        var totalPushX = 0.0
        var totalPushY = 0.0
        var needsMovement = false
    
        for (other in neighbors) {
            if (other == _actor || other == target || other.isAlikeDead) continue
    
            val curDist = other.distance3D(_actor)
            val minDist = _actor.collisionRadius + other.collisionRadius + 15.0
    
            if (curDist > 0 && curDist < minDist) {
                
                val overlap = minDist - curDist
                
                val dx = (_actor.x - other.x).toDouble()
                val dy = (_actor.y - other.y).toDouble()
                
                val pushForce = overlap * 0.5
                
                totalPushX += (dx / curDist) * pushForce
                totalPushY += (dy / curDist) * pushForce
                
                needsMovement = true
            }
        }
    
        if (needsMovement) {
            val nx = _actor.x + totalPushX
            val ny = _actor.y + totalPushY
            val nz = _actor.z
    
            moveToLocation(Location(nx.toInt(), ny.toInt(), nz), false)
            return true
        }
    
        return false
    }
    
    private fun updateFrontSlow(target: Creature, offset: Int) {
        if (offset > 100) {
            clearFrontSlow()
            return
        }
        
        val hasBlocker = hasFrontBlocker(target)
        if (hasBlocker && !frontSlowApplied) {
            _actor.addStatFunc(FuncMul(frontSlowOwner, Stats.RUN_SPEED, 0.9, null))
            frontSlowApplied = true
        } else if (!hasBlocker && frontSlowApplied) {
            clearFrontSlow()
        }
    }
    
    private fun clearFrontSlow() {
        if (frontSlowApplied) {
            _actor.removeStatsByOwner(frontSlowOwner)
            frontSlowApplied = false
        }
    }
    
    private fun tryRouteDeviationForFrontBlocker(target: Creature, offset: Int): Boolean {
        if (!hasFrontBlocker(target)) {
            return false
        }
    
        val bestSlot = findBestAttackSlot(target, offset) ?: return false
        
        val nx = bestSlot.x
        val ny = bestSlot.y
        
        val nz = target.z 
        
        val finalLocation = Location(nx, ny, nz)
    
        if (_destination.distance3D(finalLocation) > 25) {
            moveToLocation(finalLocation, false)
            return true
        }
        
        return false
    }
    
    private fun hasFrontBlocker(target: Creature): Boolean {
        val tx = target.x - _actor.x
        val ty = target.y - _actor.y
        val tLen = sqrt((tx * tx + ty * ty).toDouble())
        if (tLen <= 0.1) return false
        
        val dirX = tx / tLen
        val dirY = ty / tLen
        
        return _actor.getKnownTypeInRadius(Creature::class.java, 120).any { other ->
            if (other == _actor || other == target || other.isAlikeDead) return@any false
            val ox = other.x - _actor.x
            val oy = other.y - _actor.y
            val oLen = sqrt((ox * ox + oy * oy).toDouble())
            if (oLen <= 1.0) return@any false
            val dot = (dirX * (ox / oLen)) + (dirY * (oy / oLen))
            val closerToTarget = other.distance3D(target) < _actor.distance3D(target)
            dot > 0.7 && closerToTarget
        }
    }
    
    private fun selectMagicSkill(): L2Skill? {
        val skills = _actor.template.getSkills(
            NpcSkillType.DD_MAGIC,
            NpcSkillType.DD_MAGIC1,
            NpcSkillType.DD_MAGIC2,
            NpcSkillType.DD_MAGIC3,
            NpcSkillType.DD_MAGIC_SLOW,
            NpcSkillType.LONG_RANGE_DD_MAGIC1,
            NpcSkillType.RANGE_DD,
            NpcSkillType.RANGE_DD_MAGIC1,
            NpcSkillType.RANGE_DD_MAGIC_A,
            NpcSkillType.W_LONG_RANGE_DD_MAGIC,
            NpcSkillType.W_LONG_RANGE_DD_MAGIC1,
            NpcSkillType.W_LONG_RANGE_DD_MAGIC2,
            NpcSkillType.W_MIDDLE_RANGE_DD_MAGIC,
            NpcSkillType.W_SHORT_RANGE_DD_MAGIC,
            NpcSkillType.SELF_RANGE_DD_MAGIC,
            NpcSkillType.SELF_RANGE_DD_MAGIC1,
            NpcSkillType.SELF_RANGE_DD_MAGIC2,
            NpcSkillType.SELF_RANGE_DD_MAGIC3
        )
        if (skills.isEmpty()) {
            return null
        }
        val filtered = skills.filter { skill -> skill != null && skill.isMagic && (skill.isOffensive || skill.isDebuff) }
        if (filtered.isEmpty()) {
            return null
        }
        return filtered[Rnd.get(filtered.size)]
    }
    
    private fun findBestAttackSlot(target: Creature, baseRange: Int): Location? {
        val centerX = target.x
        val centerY = target.y
        val centerZ = target.z
        
        val layer = (_actor.objectId % 3)
        val layeredRange = if (baseRange > 200) {
            (baseRange + (layer * 20)).coerceAtLeast(100)
        } else {
            baseRange
        }
        
        val angles = 12
        val angleStep = 360.0 / angles
        
        val startAngle = Math.toDegrees(Math.atan2((_actor.y - centerY).toDouble(), (_actor.x - centerX).toDouble()))
        
        val searchRadius = layeredRange + 150
        val potentialBlockers = _actor.getKnownTypeInRadius(Creature::class.java, searchRadius)
            .filter { other -> other != _actor && other != target && !other.isAlikeDead }
            
        var bestLoc: Location? = null
        var minScore = Double.MAX_VALUE
        val collisionGap = 60.0
        
        for (i in 0 until angles) {
            val angleRadians = Math.toRadians(startAngle + (i * angleStep))
            
            val jitter = (_actor.objectId % 4) * 5 
            val currentRange = layeredRange + jitter
            
            val testX = (centerX + currentRange * Math.cos(angleRadians)).toInt()
            val testY = (centerY + currentRange * Math.sin(angleRadians)).toInt()
            
            val testZ = geoEngine.getHeight(testX, testY, centerZ).toInt()
            val testLoc = Location(testX, testY, testZ)
            
            if (!geoEngine.canMoveToTarget(_actor.x, _actor.y, _actor.z, testX, testY, testZ)) continue
            
            val isOccupied = potentialBlockers.any { other ->
                other.distance3D(testLoc) < (other.collisionRadius + _actor.collisionRadius + collisionGap)
            }
            
            if (isOccupied) continue
            
            val score = _actor.distance3D(testLoc)
            if (score < minScore) {
                minScore = score
                bestLoc = testLoc
            }
        }
        
        return bestLoc
    }
    
    private fun wouldCollideInPath(dest: Location): Boolean {
        val checkDist = _actor.collisionRadius * 2.2
        return _actor.getKnownTypeInRadius(Creature::class.java, checkDist.toInt()).any { neighbor ->
            if (neighbor == _actor || neighbor == _pawn || neighbor.isAlikeDead) return@any false
            
            _actor.distance3D(neighbor) < checkDist
        }
    }
    override fun handleNextPosition(nextX: Int, nextY: Int, nextZ: Int, type: MoveType): Boolean {
        if (super.handleNextPosition(nextX, nextY, nextZ, type)) return true
        
        val curX = _actor.x
        val curY = _actor.y
        val curZ = _actor.z
        
        if (geoEngine.canMoveToTarget(curX, curY, curZ, nextX, curY, nextZ)) {
            _actor.setXYZ(nextX, curY, nextZ)
            _actor.revalidateZone(false)
            return true
        }
        
        if (geoEngine.canMoveToTarget(curX, curY, curZ, curX, nextY, nextZ)) {
            _actor.setXYZ(curX, nextY, nextZ)
            _actor.revalidateZone(false)
            return true
        }
        
        _blocked = true
        return false
    }
}