/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
* * L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
* * L2Brproject is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* General Public License for more details.
* * You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
* Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
* Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY, 
* SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
* as a contribution for the forum L2JBrasil.com
 */
package ext.mods.gameserver.network.clientpackets;

import ext.mods.gameserver.enums.RestartType;
import ext.mods.gameserver.enums.actors.MoveType;
import ext.mods.gameserver.model.WorldObject;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.location.Location;
import ext.mods.gameserver.network.serverpackets.MoveToLocation;
import ext.mods.gameserver.network.serverpackets.MoveToPawn;
import ext.mods.gameserver.network.serverpackets.ValidateLocation;
import ext.mods.gameserver.network.serverpackets.ValidateLocationInVehicle;

/**
 * Client packet for position validation (Opcode 0x48).
 * Synchronizes client coordinates with server using a 3-zone smooth reconciliation strategy:
 * <ul>
 *   <li><b>Green Zone (dist &lt;= SoftThreshold):</b> Normal network jitter / socket latency. No packet sent, no hard snap.</li>
 *   <li><b>Yellow Zone (SoftThreshold &lt; dist &lt;= HardThreshold):</b> Soft reconciliation via smooth movement packets (MoveToPawn / MoveToLocation) without teleporting.</li>
 *   <li><b>Red Zone (dist &gt; HardThreshold or severe wall collision):</b> Hard snap via ValidateLocation (Opcode 0x61) strictly for anti-cheat/noclip.</li>
 * </ul>
 */
public class ValidatePosition extends L2GameClientPacket
{
	private int _x;
	private int _y;
	private int _z;
	private int _heading;
	private int _data;
	
	@Override
	protected void readImpl()
	{
		_x = readD();
		_y = readD();
		_z = readD();
		_heading = readD();
		_data = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getClient().getPlayer();
		if (player == null || player.isDead() || player.isTeleporting() || player.isInObserverMode())
			return;
		
		if (player.isFalling(_z))
			return;
		
		final int realX = player.getX();
		final int realY = player.getY();
		final int realZ = player.getZ();
		
		final double dx = _x - realX;
		final double dy = _y - realY;
		final double diffSq = dx * dx + dy * dy;
		
		// Vehicles / Boats handling
		if (player.isInBoat())
		{
			if (diffSq > 250000.0) // > 500 units in boat
				player.sendPacket(new ValidateLocationInVehicle(player));
			return;
		}
		
		final double speed = player.getStatus().getMoveSpeed();
		final double softThreshold = Math.max(speed * 1.2, 180.0);
		final double softThresholdSq = softThreshold * softThreshold;
		final double hardThresholdSq = Math.max(speed * 2.5, 500.0) * Math.max(speed * 2.5, 500.0);
		
		final int dz = Math.abs(_z - realZ);
		final boolean isGround = player.getMove().getMoveType() == MoveType.GROUND;
		
		
		if (_z < -30000 || _z > 30000)
		{
			player.incIncorrectValidateCount();
			if (player.getIncorrectValidateCount() >= 2)
			{
				player.teleportTo(RestartType.TOWN);
				player.resetIncorrectValidateCount();
				return;
			}
			player.sendPacket(new ValidateLocation(player));
			return;
		}

		// If player is not moving (idle), reconcile position if small drift without physical loop conflict
		if (!player.isMoving())
		{
			if (diffSq <= softThresholdSq && (dz <= 120 || !isGround))
			{
				if (diffSq >= 16) // Only set if difference is meaningful
					player.getPosition().set(_x, _y, _z, _heading);
				player.resetIncorrectValidateCount();
			}
			else
			{
				player.incIncorrectValidateCount();
				if (player.getIncorrectValidateCount() >= 3)
				{
					player.teleportTo(RestartType.TOWN);
					player.resetIncorrectValidateCount();
					return;
				}
				player.sendPacket(new ValidateLocation(player));
			}
			return;
		}
		
		// Player is actively moving: the server physical simulation is authoritative.
		// Never call player.getPosition().set() while moving, as it conflicts with PlayerMove's sub-tick accumulators.
		
		// 1. Red Zone: Severe desync (> HardThreshold or massive Z drop > 350 on ground) -> Hard snap or Town rescue
		if (diffSq > hardThresholdSq || (isGround && dz > 350 && !player.isFlying() && !player.isInWater()))
		{
			player.incIncorrectValidateCount();
			if (player.getIncorrectValidateCount() >= 3)
			{
				player.teleportTo(RestartType.TOWN);
				player.resetIncorrectValidateCount();
				return;
			}
			player.sendPacket(new ValidateLocation(player));
			return;
		}
		
		// 2. Yellow Zone: Moderate drift -> Soft correction via movement packets (No teleport / No hard snap)
		if (diffSq > softThresholdSq)
		{
			final WorldObject pawn = player.getMove().getPawn();
			if (pawn != null)
			{
				player.sendPacket(new MoveToPawn(player, pawn, player.getMove().getOffset()));
			}
			else
			{
				final Location destination = player.getMove().getDestination();
				if (destination != null && destination.getX() != 0)
				{
					player.sendPacket(new MoveToLocation(player, destination));
				}
			}
			return;
		}
		
		// 3. Green Zone (diffSq <= softThresholdSq): Natural latency convergence, reset counter!
		player.resetIncorrectValidateCount();
	}
}