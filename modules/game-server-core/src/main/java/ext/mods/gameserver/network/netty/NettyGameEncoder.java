package ext.mods.gameserver.network.netty;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.mmocore.SendablePacket;
import ext.mods.config.ConfigServer;
import ext.mods.gameserver.network.GameClient;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.concurrent.FastThreadLocal;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Codificador de saída assíncrono para o pipeline Netty com Zero-Allocation e Mechanical Sympathy.
 * Utiliza FastThreadLocal com pool reentrante de buffers por profundidade de chamada,
 * impedindo corrupção de índices e sobreposição de memória caso ocorram serializações aninhadas.
 */
public final class NettyGameEncoder extends MessageToByteEncoder<SendablePacket<GameClient>>
{
	private static final CLogger LOGGER = new CLogger(NettyGameEncoder.class.getName());
	private static final int HEADER_SIZE = 2;
	private static final int MAX_BUFFER_SIZE = 65536;
	private static final int MAX_REENTRANT_DEPTH = 4;
	
	private static final FastThreadLocal<ByteBuffer[]> REENTRANT_BUFFER_POOL = new FastThreadLocal<>()
	{
		@Override
		protected ByteBuffer[] initialValue()
		{
			final ByteBuffer[] pool = new ByteBuffer[MAX_REENTRANT_DEPTH];
			for (int i = 0; i < MAX_REENTRANT_DEPTH; i++)
			{
				pool[i] = ByteBuffer.allocate(MAX_BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
			}
			return pool;
		}
	};
	
	private static final FastThreadLocal<int[]> CALL_DEPTH = new FastThreadLocal<>()
	{
		@Override
		protected int[] initialValue()
		{
			return new int[] { 0 };
		}
	};
	
	@Override
	protected void encode(ChannelHandlerContext ctx, SendablePacket<GameClient> sp, ByteBuf out) throws Exception
	{
		assert ctx.executor().inEventLoop() : "NettyGameEncoder must strictly execute within the channel EventLoop thread";

		final GameClient client = ctx.channel().attr(NettyGameHandler.CLIENT_KEY).get();
		if (client == null)
		{
			return;
		}
		
		final int[] depthRef = CALL_DEPTH.get();
		final int currentDepth = depthRef[0]++;
		final ByteBuffer tempBuffer;

		if (currentDepth < MAX_REENTRANT_DEPTH)
		{
			tempBuffer = REENTRANT_BUFFER_POOL.get()[currentDepth];
		}
		else
		{
			LOGGER.warn("[NETTY S->C] Reentrancy depth limit exceeded ({}) for client {}. Using dynamic fallback buffer.",
				currentDepth, ctx.channel().remoteAddress());
			tempBuffer = ByteBuffer.allocate(MAX_BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
		}

		try
		{
			tempBuffer.clear();
			tempBuffer.position(HEADER_SIZE);
			
			// Escreve os dados do pacote
			sp.writePacket(client, tempBuffer);
			
			if (sp instanceof ext.mods.gameserver.network.serverpackets.L2GameServerPacket && ((ext.mods.gameserver.network.serverpackets.L2GameServerPacket) sp).hasFailed())
			{
				return;
			}
			
			final int dataSize = tempBuffer.position() - HEADER_SIZE;
			if (dataSize <= 0)
			{
				return;
			}

			final int opcode = tempBuffer.get(HEADER_SIZE) & 0xFF;
			final int totalSize = dataSize + HEADER_SIZE;

			// Registra no RingBuffer de auditoria forense do cliente
			client.getAuditTrail().recordOutbound(sp.getClass().getSimpleName(), opcode, dataSize, totalSize, client.getState());

			if (ConfigServer.DEBUG_NET)
			{
				LOGGER.info("[NETTY S->C] Sent packet: {} (opcode=0x{}, payload={}B, frame={}B, depth={}, state={}) to {}",
					sp.getClass().getSimpleName(), Integer.toHexString(opcode), dataSize, totalSize, currentDepth, client.getState(), ctx.channel().remoteAddress());
			}
			
			// Aplica cifragem Blowfish/XOR no payload (apenas sobre os dados, sem o header)
			tempBuffer.position(HEADER_SIZE);
			client.encrypt(tempBuffer, dataSize);
			
			// Grava o tamanho total no cabeçalho de 2 bytes (Little-Endian)
			tempBuffer.position(0);
			tempBuffer.putShort((short) totalSize);
			
			// Escreve o pacote final no ByteBuf de saída do Netty
			out.writeBytes(tempBuffer.array(), 0, totalSize);
		}
		finally
		{
			depthRef[0]--;
		}
	}
}
