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
package ext.mods.commons.mmocore;

import java.nio.ByteBuffer;

import ext.mods.commons.geometry.IntXYZ;

/**
 * Outbound packet writer with Mechanical Sympathy.
 * Binds the active serialization buffer to the executing thread, ensuring that multiple
 * threads encoding the exact same packet instance concurrently (e.g. mass broadcast or static singletons)
 * write to their own dedicated buffers without any lock contention, data race or null pointer risks.
 */
public abstract class SendablePacket<T extends MMOClient<?>> extends AbstractPacket<T>
{
	protected abstract void write();
	
	public void writePacket(final T client, final ByteBuffer buf)
	{
		bindThreadContext(client, buf);
		_client = client;
		_buf = buf;
		try
		{
			write();
		}
		finally
		{
			unbindThreadContext();
			_buf = null;
			_client = null;
		}
	}

	private ByteBuffer getActiveBuffer()
	{
		final ByteBuffer tb = currentThreadBuffer();
		return tb != null ? tb : _buf;
	}
	
	protected final void writeC(final int data)
	{
		getActiveBuffer().put((byte) data);
	}
	
	protected final void writeF(final double value)
	{
		getActiveBuffer().putDouble(value);
	}
	
	protected final void writeH(final int value)
	{
		getActiveBuffer().putShort((short) value);
	}
	
	protected final void writeD(final int value)
	{
		getActiveBuffer().putInt(value);
	}
	
	protected final void writeQ(final long value)
	{
		getActiveBuffer().putLong(value);
	}
	
	protected final void writeB(final byte[] data)
	{
		if (data != null && data.length > 0)
			getActiveBuffer().put(data);
	}
	
	protected final void writeS(final String text)
	{
		final ByteBuffer buf = getActiveBuffer();
		if (text != null && !text.isEmpty())
		{
			for (int i = 0; i < text.length(); i++)
				buf.putChar(text.charAt(i));
		}
		
		buf.putChar('\000');
	}
	
	protected final void writeLoc(final int x, final int y, final int z)
	{
		writeD(x);
		writeD(y);
		writeD(z);
	}
	
	protected final void writeLoc(final IntXYZ loc)
	{
		writeLoc(loc.getX(), loc.getY(), loc.getZ());
	}
	
	protected void writeEffect(final EffectView effect, final boolean toggle)
	{
		writeD(effect.id());
		writeH(effect.level());
		writeD((toggle) ? -1 : effect.duration() / 1000);
	}
}
