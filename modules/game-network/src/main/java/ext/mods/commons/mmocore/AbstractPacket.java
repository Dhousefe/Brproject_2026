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

/**
 * Base packet abstraction.
 * Employs thread-local context binding during serialization to ensure absolute
 * thread safety, zero data race and reentrancy when the same packet instance is
 * broadcast concurrently across multiple Netty EventLoop worker threads.
 */
public abstract class AbstractPacket<T extends MMOClient<?>>
{
	private static final ThreadLocal<ByteBuffer> CURRENT_BUFFER = new ThreadLocal<>();
	private static final ThreadLocal<MMOClient<?>> CURRENT_CLIENT = new ThreadLocal<>();

	protected ByteBuffer _buf;
	T _client;
	
	@SuppressWarnings("unchecked")
	public final T getClient()
	{
		final MMOClient<?> threadClient = CURRENT_CLIENT.get();
		if (threadClient != null)
		{
			return (T) threadClient;
		}
		return _client;
	}
	
	public void setByteBuffer(final ByteBuffer buf)
	{
		_buf = buf;
	}
	
	public void setClient(final T client)
	{
		_client = client;
	}
	
	public ByteBuffer getByteBuffer()
	{
		final ByteBuffer threadBuf = CURRENT_BUFFER.get();
		if (threadBuf != null)
		{
			return threadBuf;
		}
		return _buf;
	}

	static void bindThreadContext(final MMOClient<?> client, final ByteBuffer buf)
	{
		CURRENT_CLIENT.set(client);
		CURRENT_BUFFER.set(buf);
	}

	static void unbindThreadContext()
	{
		CURRENT_CLIENT.remove();
		CURRENT_BUFFER.remove();
	}

	static ByteBuffer currentThreadBuffer()
	{
		return CURRENT_BUFFER.get();
	}
}