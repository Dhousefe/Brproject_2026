package ext.mods.gameserver.network.netty;

import ext.mods.commons.logging.CLogger;
import ext.mods.config.ConfigServer;
import ext.mods.gameserver.network.GamePacketHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import ext.mods.security.fail2ban.Fail2BanInitializer;
import ext.mods.security.fail2ban.core.BanManager;
import ext.mods.security.fail2ban.netty.Fail2BanChannelHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteOrder;

/**
 * Servidor de rede reativo Netty para conexões de jogadores do GameServer.
 * Suporta Epoll nativo no Linux e NIO assíncrono em outros sistemas operacionais.
 */
public final class NettyGameServer
{
	private static final CLogger LOGGER = new CLogger(NettyGameServer.class.getName());
	private static final NettyGameServer INSTANCE = new NettyGameServer();
	
	public static NettyGameServer getInstance()
	{
		return INSTANCE;
	}
	
	private EventLoopGroup _bossGroup;
	private EventLoopGroup _workerGroup;
	private Channel _serverChannel;
	
	private NettyGameServer()
	{
	}
	
	public synchronized void start(InetAddress bindAddress, int port) throws Exception
	{
		final boolean useEpoll = Epoll.isAvailable();
		final int bossThreads = ConfigServer.NETTY_BOSS_THREADS > 0 ? ConfigServer.NETTY_BOSS_THREADS : 1;
		final int workerThreads = ConfigServer.NETTY_WORKER_THREADS;
		
		LOGGER.debug("Starting Netty Game Transport on {}:{} (Transport: {}, Boss Threads: {}, Worker Threads: {})...",
			bindAddress == null ? "*" : bindAddress.getHostAddress(),
			port,
			useEpoll ? "Native Epoll (Linux)" : "NIO Standard",
			bossThreads,
			workerThreads == 0 ? "Auto (CPU*2)" : workerThreads
		);
		
		_bossGroup = useEpoll ? new EpollEventLoopGroup(bossThreads) : new NioEventLoopGroup(bossThreads);
		_workerGroup = useEpoll ? new EpollEventLoopGroup(workerThreads) : new NioEventLoopGroup(workerThreads);
		
		final Class<? extends ServerChannel> channelClass = useEpoll ? EpollServerSocketChannel.class : NioServerSocketChannel.class;
		final GamePacketHandler packetHandler = new GamePacketHandler();
		final Fail2BanChannelHandler fail2banHandler;
		if (ConfigServer.ENABLE_FAIL2BAN)
		{
			final BanManager banManager = Fail2BanInitializer.getBanManager();
			fail2banHandler = (banManager != null) ? new Fail2BanChannelHandler(banManager) : null;
		}
		else
		{
			fail2banHandler = null;
		}
		
		final ServerBootstrap bootstrap = new ServerBootstrap();
		bootstrap.group(_bossGroup, _workerGroup)
			.channel(channelClass)
			.option(ChannelOption.SO_BACKLOG, 1024)
			.option(ChannelOption.SO_REUSEADDR, true)
			.childOption(ChannelOption.SO_REUSEADDR, true)
			.childOption(ChannelOption.TCP_NODELAY, true)
			.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
			.childHandler(new ChannelInitializer<SocketChannel>()
			{
				@Override
				protected void initChannel(SocketChannel ch)
				{
					final ChannelPipeline p = ch.pipeline();
					
					// 0. Fail2Ban: Inspecao antecipada de IP e modo panico (se habilitado)
					if (fail2banHandler != null)
					{
						p.addLast("fail2ban", fail2banHandler);
					}
					
					// 1. Framing: Enquadramento de frames de 2 bytes Little-Endian (header do protocolo L2)
					p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
						ByteOrder.LITTLE_ENDIAN,
						65535, // maxFrameLength (64KB)
						0,     // lengthFieldOffset
						2,     // lengthFieldLength
						-2,    // lengthAdjustment (subtrai 2 bytes do cabeçalho)
						2,     // initialBytesToStrip (consome os 2 bytes do cabeçalho)
						true   // failFast
					));
					
					// 2. Codificador de saída assíncrono e sequencial
					p.addLast("encoder", new NettyGameEncoder());
					
					// 3. Manipulador de pacotes do GameServer
					p.addLast("gameHandler", new NettyGameHandler(packetHandler));
				}
			});
		
		final InetSocketAddress socketAddress = bindAddress == null ? new InetSocketAddress(port) : new InetSocketAddress(bindAddress, port);
		final ChannelFuture future = bootstrap.bind(socketAddress).sync();
		_serverChannel = future.channel();
		
		LOGGER.info("✅ Netty Game Transport listening successfully on {}.", socketAddress);
	}
	
	public synchronized void shutdown()
	{
		LOGGER.info("Shutting down Netty Game Transport...");
		if (_serverChannel != null)
		{
			try
			{
				_serverChannel.close().syncUninterruptibly();
			}
			catch (Exception e)
			{
			}
			_serverChannel = null;
		}
		
		if (_workerGroup != null)
		{
			_workerGroup.shutdownGracefully();
			_workerGroup = null;
		}
		
		if (_bossGroup != null)
		{
			_bossGroup.shutdownGracefully();
			_bossGroup = null;
		}
		
		ext.mods.gameserver.network.disruptor.DisruptorPacketRouter.getInstance().shutdown();
		
		LOGGER.info("Netty Game Transport stopped.");
	}
}
