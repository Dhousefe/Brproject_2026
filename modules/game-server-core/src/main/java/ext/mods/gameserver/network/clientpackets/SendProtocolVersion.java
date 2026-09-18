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

import ext.mods.Config;
import ext.mods.gameserver.network.serverpackets.L2GameServerPacket;
import ext.mods.gameserver.network.serverpackets.VersionCheck;
import ext.mods.protection.hwid.hwid;
import ext.mods.config.ConfigProtection;
import ext.mods.config.ConfigServer;

public final class SendProtocolVersion extends L2GameClientPacket
{
	private int _version;
	private byte _data[];
	private String _hwidHdd = "NoHWID-HD";
	private String _hwidMac = "NoHWID-MAC";
	private String _hwidCPU = "NoHWID-CPU";

	
	@Override
	protected void readImpl()
	{
		_version = readD();
		if (ConfigServer.DEBUG_NET)
		{
			LOGGER.info("[PROTOCOL] SendProtocolVersion readImpl: version={}, remainingBytes={}, client={}",
				_version, _buf.remaining(), getClient());
		}
		
		if (hwid.isProtectionOn())
		{
			if (_buf.remaining() >= 266)
			{
				try
				{
					getClient().setHasLegacyGuard(true);
					_data = new byte[260];
					readB(_data);
					_hwidHdd = readS();
					_hwidMac = readS();
					_hwidCPU = readS();
					
					if (_hwidHdd.equals("NoHWID-HD") && _hwidMac.equals("NoHWID-MAC") && _hwidCPU.equals("NoHWID-CPU"))
					{
						LOGGER.warn("[PROTOCOL] Legacy Guard rejected empty HWID from {}", getClient());
						getClient().close((L2GameServerPacket) null);
					}
				}
				catch (Exception e)
				{
					LOGGER.warn("[PROTOCOL] Error reading legacy guard HWID data from {}: {}", getClient(), e.getMessage());
					getClient().setHasLegacyGuard(false);
				}
			}
			else
			{
				getClient().setHasLegacyGuard(false);
				if (_buf.hasRemaining())
				{
					_data = new byte[_buf.remaining()];
					readB(_data);
				}
			}
		}
		else if (_buf.hasRemaining())
		{
			_data = new byte[_buf.remaining()];
			readB(_data);
		}
	}
	
	@Override
	protected void runImpl()
	{
		if (hwid.isProtectionOn() && getClient().hasLegacyGuard())
		{
			switch (ConfigProtection.GET_CLIENT_HWID)
			{
				case 1:
					getClient().setHWID(_hwidHdd);
					break;
				case 2:
					getClient().setHWID(_hwidMac);
					break;
				case 3:
					getClient().setHWID(_hwidCPU);
					break;
			}
		}

		if (_version == -2)
		{
			LOGGER.info("[PROTOCOL] Client ping check (_version == -2) from {}. Closing connection gracefully.", getClient());
			getClient().close((L2GameServerPacket) null);
			return;
		}
		
		switch (_version)
		{
			case 737:
			case 740:
			case 744:
			case 746:
				if (ConfigServer.DEBUG_NET)
				{
					LOGGER.info("[PROTOCOL] Accepted client protocol version={} from {}, sending VersionCheck with blowfish key.",
						_version, getClient());
				}
				getClient().sendPacket(new VersionCheck(getClient().enableCrypt()));
				break;
			
			default:
				LOGGER.warn("[PROTOCOL] Unsupported or unknown client protocol version={} from {}. Rejecting connection.",
					_version, getClient());
				getClient().close((L2GameServerPacket) null);
				break;
		}
	}
}