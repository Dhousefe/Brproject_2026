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
package ext.mods.gameserver.network.serverpackets;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.mmocore.SendablePacket;

import ext.mods.Config;
import ext.mods.gameserver.network.GameClient;
import ext.mods.config.ConfigServer;

public abstract class L2GameServerPacket extends SendablePacket<GameClient>
{
	protected static final CLogger LOGGER = new CLogger(L2GameServerPacket.class.getName());
	
	private static final ThreadLocal<Boolean> CURRENT_FAILED = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private boolean _hasFailed = false;

	protected abstract void writeImpl();
	
	@Override
	protected void write()
	{
		CURRENT_FAILED.set(Boolean.FALSE);
		_hasFailed = false;
		if (ConfigServer.PACKET_HANDLER_DEBUG && !ConfigServer.SERVER_PACKETS.contains(getClass().getSimpleName()))
			LOGGER.info(getType());
		
		try
		{
			writeImpl();
		}
		catch (Exception e)
		{
			CURRENT_FAILED.set(Boolean.TRUE);
			_hasFailed = true;
			LOGGER.error("Failed writing {} for {}. ", e, getType(), getClient() != null ? getClient().toString() : "null");
		}
	}
	
	public boolean hasFailed()
	{
		return CURRENT_FAILED.get() || _hasFailed;
	}
	
	public void runImpl()
	{
	}
	
	public String getType()
	{
		if (getClient() != null && getClient().getPlayer() != null)
			return "[" + getClient().getPlayer().getName() + "] " + "[S] " + getClass().getSimpleName();
		
		return "[S] " + getClass().getSimpleName();
	}
}