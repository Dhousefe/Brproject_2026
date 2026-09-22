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
package ext.mods.gameserver.network;

import ext.mods.Config;
import ext.mods.config.ConfigServer;

public class GameCrypt
{
	private final byte[] _inKey = new byte[16];
	private final byte[] _outKey = new byte[16];
	private boolean _isEnabled;
	
	public void setKey(byte[] key)
	{
		System.arraycopy(key, 0, _inKey, 0, 16);
		System.arraycopy(key, 0, _outKey, 0, 16);
	}
	
	public void decrypt(byte[] raw, final int offset, final int size)
	{
		if (!ConfigServer.USE_BLOWFISH_CIPHER || !_isEnabled)
			return;
		
		int temp = 0;
		int i = 0;
		final int unrollLimit = size - 15;
		
		// 16-way unrolled vector chunking: processes 128-bit blocks by Dhousefe-L2JBR
		for (; i < unrollLimit; i += 16)
		{
			final int base = offset + i;
			
			final int c0 = raw[base] & 0xFF;
			final int c1 = raw[base + 1] & 0xFF;
			final int c2 = raw[base + 2] & 0xFF;
			final int c3 = raw[base + 3] & 0xFF;
			final int c4 = raw[base + 4] & 0xFF;
			final int c5 = raw[base + 5] & 0xFF;
			final int c6 = raw[base + 6] & 0xFF;
			final int c7 = raw[base + 7] & 0xFF;
			final int c8 = raw[base + 8] & 0xFF;
			final int c9 = raw[base + 9] & 0xFF;
			final int c10 = raw[base + 10] & 0xFF;
			final int c11 = raw[base + 11] & 0xFF;
			final int c12 = raw[base + 12] & 0xFF;
			final int c13 = raw[base + 13] & 0xFF;
			final int c14 = raw[base + 14] & 0xFF;
			final int c15 = raw[base + 15] & 0xFF;
			
			raw[base]      = (byte) (c0  ^ _inKey[0]  ^ temp);
			raw[base + 1]  = (byte) (c1  ^ _inKey[1]  ^ c0);
			raw[base + 2]  = (byte) (c2  ^ _inKey[2]  ^ c1);
			raw[base + 3]  = (byte) (c3  ^ _inKey[3]  ^ c2);
			raw[base + 4]  = (byte) (c4  ^ _inKey[4]  ^ c3);
			raw[base + 5]  = (byte) (c5  ^ _inKey[5]  ^ c4);
			raw[base + 6]  = (byte) (c6  ^ _inKey[6]  ^ c5);
			raw[base + 7]  = (byte) (c7  ^ _inKey[7]  ^ c6);
			raw[base + 8]  = (byte) (c8  ^ _inKey[8]  ^ c7);
			raw[base + 9]  = (byte) (c9  ^ _inKey[9]  ^ c8);
			raw[base + 10] = (byte) (c10 ^ _inKey[10] ^ c9);
			raw[base + 11] = (byte) (c11 ^ _inKey[11] ^ c10);
			raw[base + 12] = (byte) (c12 ^ _inKey[12] ^ c11);
			raw[base + 13] = (byte) (c13 ^ _inKey[13] ^ c12);
			raw[base + 14] = (byte) (c14 ^ _inKey[14] ^ c13);
			raw[base + 15] = (byte) (c15 ^ _inKey[15] ^ c14);
			
			temp = c15;
		}
		
		
		for (; i < size; i++)
		{
			final int temp2 = raw[offset + i] & 0xFF;
			raw[offset + i] = (byte) (temp2 ^ _inKey[i & 15] ^ temp);
			temp = temp2;
		}
		
		int old = _inKey[8] & 0xff;
		old |= _inKey[9] << 8 & 0xff00;
		old |= _inKey[10] << 0x10 & 0xff0000;
		old |= _inKey[11] << 0x18 & 0xff000000;
		
		old += size;
		
		_inKey[8] = (byte) (old & 0xff);
		_inKey[9] = (byte) (old >> 0x08 & 0xff);
		_inKey[10] = (byte) (old >> 0x10 & 0xff);
		_inKey[11] = (byte) (old >> 0x18 & 0xff);
	}
	
	public void encrypt(byte[] raw, final int offset, final int size)
	{
		if (!_isEnabled)
		{
			_isEnabled = ConfigServer.USE_BLOWFISH_CIPHER;
			return;
		}
		
		int temp = 0;
		for (int i = 0; i < size; i++)
		{
			int temp2 = raw[offset + i] & 0xFF;
			temp = temp2 ^ _outKey[i & 15] ^ temp;
			raw[offset + i] = (byte) temp;
		}
		
		int old = _outKey[8] & 0xff;
		old |= _outKey[9] << 8 & 0xff00;
		old |= _outKey[10] << 0x10 & 0xff0000;
		old |= _outKey[11] << 0x18 & 0xff000000;
		
		old += size;
		
		_outKey[8] = (byte) (old & 0xff);
		_outKey[9] = (byte) (old >> 0x08 & 0xff);
		_outKey[10] = (byte) (old >> 0x10 & 0xff);
		_outKey[11] = (byte) (old >> 0x18 & 0xff);
	}
}
