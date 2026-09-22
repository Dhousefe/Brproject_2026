package ext.mods.gameserver.network.netty;

import ext.mods.commons.mmocore.SendablePacket;
import ext.mods.gameserver.network.GameClient;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Conexão de rede reativa baseada em Netty para GameClient.
 * Delega o envio e enfileiramento de pacotes para o pipeline Netty de forma thread-safe.
 */
public final class NettyGameConnection
{
	private final Channel _channel;
	private final InetAddress _address;
	private final int _port;
	private GameClient _client;
	private volatile boolean _closed = false;
	
	public NettyGameConnection(Channel channel)
	{
		_channel = channel;
		if (channel.remoteAddress() instanceof InetSocketAddress remote)
		{
			_address = remote.getAddress();
			_port = remote.getPort();
		}
		else
		{
			_address = null;
			_port = 0;
		}
	}
	
	public void setClient(GameClient client)
	{
		_client = client;
	}
	
	public GameClient getClient()
	{
		return _client;
	}
	
	public Channel getChannel()
	{
		return _channel;
	}
	
	public InetAddress getAddress()
	{
		return _address;
	}
	
	public int getPort()
	{
		return _port;
	}
	
	private final java.util.concurrent.atomic.AtomicInteger _batchDepth = new java.util.concurrent.atomic.AtomicInteger(0);
	private final java.util.concurrent.atomic.AtomicInteger _batchPacketCount = new java.util.concurrent.atomic.AtomicInteger(0);
	private static final int MAX_BATCH_PACKETS_BEFORE_FLUSH = 64;
	
	public boolean isConnected()
	{
		return _channel != null && _channel.isActive() && !_closed;
	}
	
	public void startBatch()
	{
		_batchDepth.incrementAndGet();
	}
	
	public void endBatch()
	{
		if (_batchDepth.decrementAndGet() <= 0)
		{
			_batchDepth.set(0);
			_batchPacketCount.set(0);
			if (isConnected())
				_channel.flush();
		}
	}
	
	public void sendPacket(SendablePacket<GameClient> sp)
	{
		if (sp == null)
			return;
		
		sp.setClient(_client);
		
		if (!isConnected())
			return;
		
		if (_batchDepth.get() > 0)
		{
			_channel.write(sp);
			if (_batchPacketCount.incrementAndGet() >= MAX_BATCH_PACKETS_BEFORE_FLUSH)
			{
				_batchPacketCount.set(0);
				_channel.flush();
			}
		}
		else
		{
			_channel.writeAndFlush(sp);
		}
	}
	
	public void sendPackets(java.util.List<? extends SendablePacket<GameClient>> packets)
	{
		if (packets == null || packets.isEmpty() || !isConnected())
			return;
		
		final int size = packets.size();
		for (int i = 0; i < size; i++)
		{
			final SendablePacket<GameClient> sp = packets.get(i);
			if (sp == null)
				continue;
			sp.setClient(_client);
			if (i == size - 1)
				_channel.writeAndFlush(sp);
			else
				_channel.write(sp);
		}
	}
	
	public void close(SendablePacket<GameClient> sp)
	{
		_closed = true;
		if (sp != null)
			sp.setClient(_client);
		
		if (_channel == null || !_channel.isActive())
			return;
		
		if (sp != null)
		{
			_channel.writeAndFlush(sp).addListener(ChannelFutureListener.CLOSE);
			return;
		}
		
		_channel.close();
	}
	
	public void closeNow()
	{
		_closed = true;
		if (_channel != null && _channel.isActive())
		{
			_channel.close();
		}
	}
}
