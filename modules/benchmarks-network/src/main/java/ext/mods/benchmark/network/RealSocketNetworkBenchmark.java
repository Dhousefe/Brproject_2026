package ext.mods.benchmark.network;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import ext.mods.commons.mmocore.SendablePacket;
import ext.mods.gameserver.network.GameClient;
import ext.mods.gameserver.network.netty.NettyGameConnection;
import ext.mods.gameserver.network.netty.NettyGameEncoder;
import ext.mods.gameserver.network.netty.NettyGameHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.openjdk.jmh.annotations.*;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Benchmark JMH End-to-End da camada de rede com Sockets TCP reais (Loopback 127.0.0.1).
 * Mede:
 * 1. Vazão real (PPS e MB/s) com atrasos reais de kernel (Syscalls send, recv, buffer tcp).
 * 2. Cifragem Blowfish/XOR real.
 * 3. Pacotes de tamanhos variados (MoveToLocation 28B, StatusUpdate 48B, UserInfo 260B).
 * 4. Broadcast massivo via LMAX Disruptor e Netty.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(0)
@Threads(4)
@State(Scope.Benchmark)
public class RealSocketNetworkBenchmark
{
	private static final int PORT = 17788;
	private static final int NUM_CLIENTS = 10;
	
	// Server Netty
	private EventLoopGroup _bossGroup;
	private EventLoopGroup _workerGroup;
	private Channel _serverChannel;
	
	// Client Netty (para drenar os sockets reais no kernel)
	private EventLoopGroup _clientGroup;
	private final List<Channel> _clientChannels = new ArrayList<>();
	private final LongAdder _clientReceivedBytes = new LongAdder();
	private final LongAdder _clientReceivedPackets = new LongAdder();
	
	// Conexões do servidor para cada cliente
	private final List<NettyGameConnection> _serverConnections = new ArrayList<>();
	
	// Disruptor para Broadcast
	public static final class BroadcastEvent
	{
		public SendablePacket<GameClient> packet;
	}
	
	private Disruptor<BroadcastEvent> _disruptor;
	private RingBuffer<BroadcastEvent> _ringBuffer;
	
	// -------------------------------------------------------------
	// Implementações de Pacotes Reais de Teste
	// -------------------------------------------------------------
	
	public static final class MockMoveToLocationPacket extends SendablePacket<GameClient>
	{
		private final int _objId;
		private final int _x, _y, _z, _dx, _dy, _dz;
		
		public MockMoveToLocationPacket(int objId, int x, int y, int z, int dx, int dy, int dz)
		{
			_objId = objId;
			_x = x; _y = y; _z = z;
			_dx = dx; _dy = dy; _dz = dz;
		}
		
		@Override
		protected void write()
		{
			writeC(0x01); // Opcode MoveToLocation
			writeD(_objId);
			writeD(_dx);
			writeD(_dy);
			writeD(_dz);
			writeD(_x);
			writeD(_y);
			writeD(_z);
		}
	}
	
	public static final class MockStatusUpdatePacket extends SendablePacket<GameClient>
	{
		private final int _objId;
		
		public MockStatusUpdatePacket(int objId)
		{
			_objId = objId;
		}
		
		@Override
		protected void write()
		{
			writeC(0x0e); // Opcode StatusUpdate
			writeD(_objId);
			writeD(4);    // 4 attributes (HP, MAX_HP, MP, MAX_MP)
			
			writeD(0x09); writeD(5000); // HP
			writeD(0x0a); writeD(5000); // MAX_HP
			writeD(0x0b); writeD(2000); // MP
			writeD(0x0c); writeD(2000); // MAX_MP
		}
	}
	
	public static final class MockUserInfoPacket extends SendablePacket<GameClient>
	{
		private final int _objId;
		
		public MockUserInfoPacket(int objId)
		{
			_objId = objId;
		}
		
		@Override
		protected void write()
		{
			writeC(0x04); // Opcode UserInfo
			writeD(100000); writeD(100000); writeD(-3000); writeD(0);
			writeD(_objId);
			writeS("PlayerTestName");
			writeD(1); writeD(0); writeD(1); writeD(80);
			writeQ(100000000L); writeF(1.0); writeF(1.0);
			writeD(500); writeD(500); writeD(500); writeD(500); writeD(500); writeD(500);
			writeD(5000); writeD(5000); writeD(2000); writeD(2000); writeD(1000000); writeD(0);
			// Simula slots de paperdoll (26 slots * 4 bytes)
			for (int i = 0; i < 26; i++)
			{
				writeD(1000 + i);
			}
		}
	}
	
	@Setup(Level.Trial)
	public void setup() throws Exception
	{
		_bossGroup = new NioEventLoopGroup(1);
		_workerGroup = new NioEventLoopGroup(4);
		_clientGroup = new NioEventLoopGroup(4);
		
		// 1. Inicia o Servidor Netty TCP Real
		final ServerBootstrap serverBootstrap = new ServerBootstrap();
		serverBootstrap.group(_bossGroup, _workerGroup)
			.channel(NioServerSocketChannel.class)
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
					p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
						ByteOrder.LITTLE_ENDIAN, 65535, 0, 2, -2, 2, true
					));
					p.addLast("encoder", new NettyGameEncoder());
					p.addLast("serverHandler", new ChannelInboundHandlerAdapter()
					{
						@Override
						public void channelActive(ChannelHandlerContext ctx)
						{
							final NettyGameConnection con = new NettyGameConnection(ctx.channel());
							final GameClient client = new GameClient(null);
							client.setNettyConnection(con);
							con.setClient(client);
							client.enableCrypt(); // Ativa cifragem Blowfish
							ctx.channel().attr(NettyGameHandler.CLIENT_KEY).set(client);
							
							synchronized (_serverConnections)
							{
								_serverConnections.add(con);
							}
						}
					});
				}
			});
		
		_serverChannel = serverBootstrap.bind(new InetSocketAddress("127.0.0.1", PORT)).sync().channel();
		
		// 2. Conecta os Clientes TCP Reais (para gerar atraso e I/O de Kernel real)
		final Bootstrap clientBootstrap = new Bootstrap();
		clientBootstrap.group(_clientGroup)
			.channel(NioSocketChannel.class)
			.option(ChannelOption.TCP_NODELAY, true)
			.handler(new ChannelInitializer<SocketChannel>()
			{
				@Override
				protected void initChannel(SocketChannel ch)
				{
					ch.pipeline().addLast("clientReceiver", new ChannelInboundHandlerAdapter()
					{
						@Override
						public void channelRead(ChannelHandlerContext ctx, Object msg)
						{
							if (msg instanceof ByteBuf buf)
							{
								_clientReceivedBytes.add(buf.readableBytes());
								_clientReceivedPackets.increment();
								buf.release(); // Drena o socket
							}
						}
					});
				}
			});
		
		for (int i = 0; i < NUM_CLIENTS; i++)
		{
			final Channel ch = clientBootstrap.connect("127.0.0.1", PORT).sync().channel();
			_clientChannels.add(ch);
		}
		
		// Aguarda estabelecimento das conexões no servidor
		while (_serverConnections.size() < NUM_CLIENTS)
		{
			Thread.sleep(10);
		}
		
		// 3. Setup do LMAX Disruptor para Broadcast
		_disruptor = new Disruptor<>(
			BroadcastEvent::new,
			32768,
			DaemonThreadFactory.INSTANCE,
			ProducerType.MULTI,
			new BlockingWaitStrategy()
		);
		
		_disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
			final int size = _serverConnections.size();
			for (int i = 0; i < size; i++)
			{
				_serverConnections.get(i).sendPacket(event.packet);
			}
		});
		
		_ringBuffer = _disruptor.start();
	}
	
	@TearDown(Level.Trial)
	public void tearDown()
	{
		if (_disruptor != null)
		{
			_disruptor.shutdown();
		}
		for (Channel ch : _clientChannels)
		{
			ch.close();
		}
		if (_serverChannel != null)
		{
			_serverChannel.close();
		}
		if (_workerGroup != null)
		{
			_workerGroup.shutdownGracefully();
		}
		if (_bossGroup != null)
		{
			_bossGroup.shutdownGracefully();
		}
		if (_clientGroup != null)
		{
			_clientGroup.shutdownGracefully();
		}
	}
	
	@Benchmark
	public void benchmarkNettyRealSocket_MoveToLocation()
	{
		final int index = (int) (Thread.currentThread().threadId() % NUM_CLIENTS);
		final NettyGameConnection con = _serverConnections.get(index);
		if (con.getChannel().isWritable())
		{
			con.sendPacket(new MockMoveToLocationPacket(1001, 10000, 20000, -3500, 10500, 20500, -3500));
		}
	}
	
	@Benchmark
	public void benchmarkNettyRealSocket_StatusUpdate()
	{
		final int index = (int) (Thread.currentThread().threadId() % NUM_CLIENTS);
		final NettyGameConnection con = _serverConnections.get(index);
		if (con.getChannel().isWritable())
		{
			con.sendPacket(new MockStatusUpdatePacket(1001));
		}
	}
	
	@Benchmark
	public void benchmarkNettyRealSocket_UserInfo()
	{
		final int index = (int) (Thread.currentThread().threadId() % NUM_CLIENTS);
		final NettyGameConnection con = _serverConnections.get(index);
		if (con.getChannel().isWritable())
		{
			con.sendPacket(new MockUserInfoPacket(1001));
		}
	}
	
	@Benchmark
	public void benchmarkNettyDisruptorRealSocket_Broadcast()
	{
		final long sequence = _ringBuffer.next();
		try
		{
			final BroadcastEvent event = _ringBuffer.get(sequence);
			event.packet = new MockMoveToLocationPacket(1001, 10000, 20000, -3500, 10500, 20500, -3500);
		}
		finally
		{
			_ringBuffer.publish(sequence);
		}
	}
}
