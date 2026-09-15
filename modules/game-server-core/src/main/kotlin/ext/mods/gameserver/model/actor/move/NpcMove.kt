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
import ext.mods.gameserver.geoengine.geodata.GeoStructure
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
        val movingTolerance = if (target.isMoving) 40.0 else 10.0
        val realAttackRange = totalRange + movingTolerance
        
        // If already within legitimate attack range, halt and notify arrival
        if (dist <= realAttackRange) {
            _actor.position.setHeadingTo(targetLoc)
            
            if (ConfigNpcs.DEBUG_MELEE_ATTACK && offset <= 200) {
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
        
        // Still outside legitimate attack range: navigate towards target or surround slot
        updateFrontSlow(target, offset)
        
        val isBlocked = wouldCollideInPath(targetLoc)
        val surroundingCount = if (offset <= 200) {
            _actor.getKnownTypeInRadius(Npc::class.java, (totalRange + 100).toInt())
                .count { it != _actor && !it.isAlikeDead && it.target == target }
        } else 0
        
        val dest = if (offset <= 200 && (isBlocked || surroundingCount > 0)) {
            findBestAttackSlot(target, offset) ?: targetLoc
        } else {
            targetLoc
        }
        
        val distToDest = _actor.distance3D(dest)
        if (distToDest > realAttackRange && (!_actor.isMoving || _destination.distance3D(dest) > 40)) {
            val usePathfinding = isBlocked || distToDest > 300
            moveToLocation(dest, usePathfinding)
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
            _separationForceX = totalPushX.coerceIn(-15.0, 15.0)
            _separationForceY = totalPushY.coerceIn(-15.0, 15.0)
        } else {
            _separationForceX = 0.0
            _separationForceY = 0.0
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
    
        if (!_actor.isMoving || _destination.distance3D(finalLocation) > 80) {
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
        
        val actorCol = _actor.collisionRadius
        val targetCol = target.collisionRadius
        
        // Ring 1: within melee attack range (~55-75 units)
        val ring1Radius = if (baseRange <= 200) {
            (baseRange + actorCol + targetCol - 10.0).coerceAtLeast(actorCol + targetCol)
        } else {
            baseRange.toDouble()
        }
        
        // Ring 2: secondary surround ring if ring 1 is occupied
        val ring2Radius = ring1Radius + actorCol * 2.0 + 15.0
        
        val searchRadius = (ring2Radius + 100.0).toInt()
        val blockers = _actor.getKnownTypeInRadius(Creature::class.java, searchRadius)
            .filter { other -> other != _actor && other != target && !other.isAlikeDead }
            
        // Base approach angle from target to this actor
        val baseAngle = Math.toDegrees(Math.atan2((_actor.y - centerY).toDouble(), (_actor.x - centerX).toDouble()))
        
        // Candidate angular offsets: direct, flank left/right, wider flanks, rear
        val angleOffsets = doubleArrayOf(0.0, 45.0, -45.0, 90.0, -90.0, 135.0, -135.0, 180.0)
        
        // Test Ring 1 first, then Ring 2
        val rings = if (baseRange <= 200) doubleArrayOf(ring1Radius, ring2Radius) else doubleArrayOf(ring1Radius)
        
        for (radius in rings) {
            for (angleOffset in angleOffsets) {
                val angleRad = Math.toRadians(baseAngle + angleOffset)
                val testX = (centerX + radius * Math.cos(angleRad)).toInt()
                val testY = (centerY + radius * Math.sin(angleRad)).toInt()
                val testZ = geoEngine.getHeight(testX, testY, centerZ + 2 * GeoStructure.CELL_HEIGHT).toInt()
                
                // Discard invalid vertical steps (slopes / cliffs)
                if (abs(testZ - centerZ) > 80) continue
                
                val testLoc = Location(testX, testY, testZ)
                
                // Check if slot is already occupied by another creature (shoulder-to-shoulder clearance)
                val isOccupied = blockers.any { other ->
                    other.distance2D(testLoc) < (other.collisionRadius + actorCol - 5.0)
                }
                if (isOccupied) continue
                
                // Verify direct line/reach from slot to target
                if (!geoEngine.canMoveToTarget(testX, testY, testZ, centerX, centerY, centerZ)) continue
                
                // Verify path from actor to slot
                if (!geoEngine.canMoveToTarget(_actor.x, _actor.y, _actor.z, testX, testY, testZ)) continue
                
                return testLoc
            }
        }
        
        return null
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
        
        _blocked = true
        return false
    }
}