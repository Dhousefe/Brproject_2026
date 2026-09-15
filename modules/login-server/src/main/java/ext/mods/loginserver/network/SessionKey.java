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
package ext.mods.loginserver.network;

import java.security.SecureRandom;

/**
 * <p>
 * This class is used to represent session keys used by the client to authenticate in the gameserver
 * </p>
 * <p>
 * A SessionKey is made up of two 8 bytes keys. One is send in the {@link ext.mods.loginserver.network.serverpackets.LoginOk LoginOk} packet and the other is sent in {@link ext.mods.loginserver.network.serverpackets.PlayOk PlayOk}
 * </p>
 * <p>
 * att-ver-3.0: generation must use {@link #secureRandom()}; server-side {@link #equals(SessionKey)}
 * always compares the full 128-bit material (client SHOW_LICENCE only affects which packets the client sends).
 * </p>
 */
public class SessionKey
{
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	
	public int playOkID1;
	public int playOkID2;
	public int loginOkID1;
	public int loginOkID2;
	
	public SessionKey(int loginOK1, int loginOK2, int playOK1, int playOK2)
	{
		playOkID1 = playOK1;
		playOkID2 = playOK2;
		loginOkID1 = loginOK1;
		loginOkID2 = loginOK2;
	}
	
	/** Cryptographically strong session material for login→GS handoff. */
	public static SessionKey secureRandom()
	{
		return new SessionKey(SECURE_RANDOM.nextInt(), SECURE_RANDOM.nextInt(), SECURE_RANDOM.nextInt(), SECURE_RANDOM.nextInt());
	}
	
	@Override
	public String toString()
	{
		return "PlayOk: " + playOkID1 + " " + playOkID2 + " LoginOk:" + loginOkID1 + " " + loginOkID2;
	}
	
	public boolean checkLoginPair(int loginOk1, int loginOk2)
	{
		return loginOkID1 == loginOk1 && loginOkID2 == loginOk2;
	}
	
	/**
	 * Full 128-bit compare for GS auth (PlayOk + LoginOk pairs).
	 * @param key other key
	 * @return true if keys are equal
	 */
	public boolean equals(SessionKey key)
	{
		if (key == null)
			return false;
		return playOkID1 == key.playOkID1
			&& loginOkID1 == key.loginOkID1
			&& playOkID2 == key.playOkID2
			&& loginOkID2 == key.loginOkID2;
	}
}