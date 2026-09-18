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
 * Codificador de saída assíncrono para o pipeline Netty com Zero-Allocation.
 * Utiliza FastThreadLocal para reutilizar o ByteBuffer temporário sem gerar GC overhead.
 */
public final class NettyGameEncoder extends MessageToByteEncoder<SendablePacket<GameClient>>
{
	private static final CLogger LOGGER = new CLogger(NettyGameEncoder.class.getName());
	private static final int HEADER_SIZE = 2;
	private static final int MAX_BUFFER_SIZE = 65536;
	
	private static final FastThreadLocal<ByteBuffer> BUFFER_CACHE = new FastThreadLocal<>()
	{
		@Override
		protected ByteBuffer initialValue()
		{
			return ByteBuffer.allocate(MAX_BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
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
		
		// Reutiliza o ByteBuffer thread-local (Zero-GC)
		final ByteBuffer tempBuffer = BUFFER_CACHE.get();
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
			LOGGER.info("[NETTY S->C] Sent packet: {} (opcode=0x{}, payload={}B, frame={}B, state={}) to {}",
				sp.getClass().getSimpleName(), Integer.toHexString(opcode), dataSize, totalSize, client.getState(), ctx.channel().remoteAddress());
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
}
