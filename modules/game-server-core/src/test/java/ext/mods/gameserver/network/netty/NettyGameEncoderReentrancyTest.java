package ext.mods.gameserver.network.netty;

import ext.mods.gameserver.network.GameClient;
import ext.mods.gameserver.network.serverpackets.L2GameServerPacket;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de unidade verificando a imunidade do NettyGameEncoder contra sobreposicao de buffers e reentrancia.
 */
public class NettyGameEncoderReentrancyTest
{
	private static class SimpleTestPacket extends L2GameServerPacket
	{
		private final int _opcode;
		private final int _extraPayloadSize;

		public SimpleTestPacket(int opcode, int extraPayloadSize)
		{
			_opcode = opcode;
			_extraPayloadSize = extraPayloadSize;
		}

		@Override
		protected void writeImpl()
		{
			writeC(_opcode);
			for (int i = 0; i < _extraPayloadSize; i++)
			{
				writeC((i + 1) & 0xFF);
			}
		}
	}

	private static class ReentrantTriggerPacket extends L2GameServerPacket
	{
		private final EmbeddedChannel _channel;
		private final L2GameServerPacket _nestedPacket;
		private final int _opcode;

		public ReentrantTriggerPacket(EmbeddedChannel channel, L2GameServerPacket nestedPacket, int opcode)
		{
			_channel = channel;
			_nestedPacket = nestedPacket;
			_opcode = opcode;
		}

		@Override
		protected void writeImpl()
		{
			writeC(_opcode);
			writeD(0x12345678);
			
			// Simula uma reentrancia durante a serializacao
			if (_nestedPacket != null && _channel != null)
			{
				_channel.writeOutbound(_nestedPacket);
			}

			writeD(0x78563412);
		}
	}

	private static EmbeddedChannel createChannelWithClient()
	{
		final EmbeddedChannel channel = new EmbeddedChannel(new NettyGameEncoder());
		final NettyGameConnection netCon = new NettyGameConnection(channel);
		final GameClient client = new GameClient(null);
		client.setNettyConnection(netCon);
		netCon.setClient(client);
		channel.attr(NettyGameHandler.CLIENT_KEY).set(client);
		return channel;
	}

	@Test
	@DisplayName("NettyGameEncoder deve codificar pacotes simples com enquadramento Little-Endian e opcode intactos")
	public void testSimplePacketEncoding()
	{
		final EmbeddedChannel channel = createChannelWithClient();
		
		final SimpleTestPacket packet = new SimpleTestPacket(0x04, 100);
		assertTrue(channel.writeOutbound(packet));

		final io.netty.buffer.ByteBuf out = channel.readOutbound();
		assertNotNull(out);
		try
		{
			assertEquals(103, out.readableBytes()); // 2 bytes header + 1 opcode + 100 payload
			final int frameLength = out.readUnsignedShortLE();
			assertEquals(103, frameLength);
			final int opcode = out.readUnsignedByte();
			assertEquals(0x04, opcode);
		}
		finally
		{
			out.release();
		}
	}

	@Test
	@DisplayName("NettyGameEncoder deve isolar chamadas reentrantes sem corromper o opcode ou deslocar bytes do primeiro pacote")
	public void testReentrantEncodingDoesNotCorruptBuffers()
	{
		final EmbeddedChannel channel = createChannelWithClient();
		
		final SimpleTestPacket nestedPacket = new SimpleTestPacket(0x58, 50); // Opcode 0x58 (SkillList), 50B
		final ReentrantTriggerPacket outerPacket = new ReentrantTriggerPacket(channel, nestedPacket, 0x04); // Opcode 0x04 (UserInfo)

		assertTrue(channel.writeOutbound(outerPacket));

		// Deve haver 2 frames de saida
		final io.netty.buffer.ByteBuf firstOut = channel.readOutbound();
		final io.netty.buffer.ByteBuf secondOut = channel.readOutbound();

		assertNotNull(firstOut);
		assertNotNull(secondOut);

		try
		{
			int len1 = firstOut.readUnsignedShortLE();
			int op1 = firstOut.readUnsignedByte();

			int len2 = secondOut.readUnsignedShortLE();
			int op2 = secondOut.readUnsignedByte();

			boolean hasOuter = (op1 == 0x04 && len1 == 11) || (op2 == 0x04 && len2 == 11);
			boolean hasNested = (op1 == 0x58 && len1 == 53) || (op2 == 0x58 && len2 == 53);

			assertTrue(hasOuter, "Outer packet (opcode 0x04, 11B) deve ter preservado seu opcode e tamanho intactos");
			assertTrue(hasNested, "Nested packet (opcode 0x58, 53B) deve ter preservado seu opcode e tamanho intactos");
		}
		finally
		{
			firstOut.release();
			secondOut.release();
		}
	}
}
