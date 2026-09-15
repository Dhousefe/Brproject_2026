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

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.serverpackets.ValidateLocation;
import ext.mods.gameserver.network.serverpackets.ValidateLocationInVehicle;

/**
 * Client packet for position validation (Opcode 0x48).
 * Synchronizes client coordinates with server within safe thresholds.
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
		if (player == null || player.isDead() || player.isTeleporting())
			return;
		
		final int realX = player.getX();
		final int realY = player.getY();
		
		final int dx = _x - realX;
		final int dy = _y - realY;
		final int diffSq = dx * dx + dy * dy;
		
		if (diffSq < 36)
			return;
		
		if (player.isInBoat())
		{
			if (diffSq > 1048576)
				player.sendPacket(new ValidateLocationInVehicle(player));
			
			return;
		}
		
		if (player.isFalling(_z))
			return;
		
		final int dz = Math.abs(_z - player.getZ());
		
		// If severe desync (> 1024 units in XY or > 500 units in Z without flying/swimming), send ValidateLocation to resync
		if (diffSq > 1048576 || (dz > 500 && !player.isFlying() && !player.isInWater()))
		{
			player.sendPacket(new ValidateLocation(player));
		}
		else
		{
			player.getPosition().set(_x, _y, _z, _heading);
		}
	}
}