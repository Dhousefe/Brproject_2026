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

import java.nio.ByteBuffer;
import java.util.List;

import ext.mods.commons.lang.StringUtil;

public class ShowBoard extends L2GameServerPacket
{
	public static final ShowBoard STATIC_SHOWBOARD_102 = new ShowBoard(null, "102");
	public static final ShowBoard STATIC_SHOWBOARD_103 = new ShowBoard(null, "103");
	
	public static final ShowBoard STATIC_CLOSE = new ShowBoard();
	
	private static final byte[] STATIC_HEADER_BYTES;
	static
	{
		final String[] headers = {
			"bypass _bbshome",
			"bypass _bbsgetfav",
			"bypass _bbsloc",
			"bypass _bbsclan",
			"bypass _bbsmemo",
			"bypass _maillist_0_1_0_",
			"bypass _friendlist_0_",
			"bypass _bbsgetfav_add"
		};
		int totalBytes = 0;
		for (String h : headers)
			totalBytes += (h.length() + 1) * 2;
		
		final ByteBuffer buf = ByteBuffer.allocate(totalBytes);
		for (String h : headers)
		{
			for (int i = 0; i < h.length(); i++)
				buf.putChar(h.charAt(i));
			buf.putChar('\000');
		}
		STATIC_HEADER_BYTES = buf.array();
	}
	
	private final String _htmlCode;
	private final boolean _canShow;
	
	public ShowBoard(String htmlCode, String id)
	{
		_canShow = true;
		_htmlCode = (htmlCode == null) ? (id + "\u0008") : (id + "\u0008" + htmlCode);
	}
	
	public ShowBoard(List<String> arg)
	{
		_canShow = true;
		final StringBuilder sb = new StringBuilder();
		sb.append("1002\u0008");
		for (String str : arg)
			StringUtil.append(sb, str, " \u0008");
		_htmlCode = sb.toString();
	}
	
	public ShowBoard()
	{
		_canShow = false;
		_htmlCode = "";
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x6e);
		writeC(_canShow ? 0x01 : 0x00);
		
		if (_canShow)
		{
			writeB(STATIC_HEADER_BYTES);
			writeS(_htmlCode);
		}
	}
}