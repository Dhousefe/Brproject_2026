package ext.mods.gameserver.network.netty;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.mmocore.IPacketHandler;
import ext.mods.commons.mmocore.ReceivablePacket;
import ext.mods.gameserver.network.GameClient;
import ext.mods.gameserver.network.GamePacketHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Manipulador de pipeline Netty para o GameServer.
 * Responsável por decodificar pacotes, decifrar Blowfish e despachar eventos para o executor do GameClient.
 */
public final class NettyGameHandler extends ChannelInboundHandlerAdapter
{
	private static final CLogger LOGGER = new CLogger(NettyGameHandler.class.getName());
	public static final AttributeKey<GameClient> CLIENT_KEY = AttributeKey.valueOf("L2_GAME_CLIENT");
	
	private final IPacketHandler<GameClient> _packetHandler;
	
	public NettyGameHandler(IPacketHandler<GameClient> packetHandler)
	{
		_packetHandler = packetHandler;
	}
	
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception
	{
		final NettyGameConnection netCon = new NettyGameConnection(ctx.channel());
		final GameClient client = new GameClient(null);
		
		client.setNettyConnection(netCon);
		netCon.setClient(client);
		ctx.channel().attr(CLIENT_KEY).set(client);
		
		if (ext.mods.config.ConfigServer.DEBUG_NET)
		{
			LOGGER.info("[NETTY] Inbound TCP connection ACTIVE from {}", ctx.channel().remoteAddress());
		}
		super.channelActive(ctx);
	}
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
	{
		if (!(msg instanceof ByteBuf buf))
		{
			ctx.fireChannelRead(msg);
			return;
		}
		
		try
		{
			final GameClient client = ctx.channel().attr(CLIENT_KEY).get();
			if (client == null)
			{
				return;
			}
			
			final int readable = buf.readableBytes();
			if (readable <= 0)
			{
				return;
			}
			
			final ByteBuffer byteBuffer = ByteBuffer.allocate(readable).order(ByteOrder.LITTLE_ENDIAN);
			buf.readBytes(byteBuffer.array(), 0, readable);
			byteBuffer.position(0);
			byteBuffer.limit(readable);
			
			// Decripta Blowfish/XOR
			client.decrypt(byteBuffer, readable);
			
			final int opcode = byteBuffer.get(0) & 0xFF;
			if (ext.mods.config.ConfigServer.DEBUG_NET)
			{
				LOGGER.info("[NETTY] Frame received: {} bytes, opcode=0x{} (state={}) from {}",
					readable, Integer.toHexString(opcode), client.getState(), ctx.channel().remoteAddress());
			}
			
			// Identifica e instancia o ReceivablePacket
			final ReceivablePacket<GameClient> packet = _packetHandler.handlePacket(byteBuffer, client);
			if (packet != null)
			{
				if (packet.readPacket(client, byteBuffer))
				{
					// Retain do ByteBuf para o ciclo de vida estender-se até o consumo assíncrono pelo Disruptor
					buf.retain();
					final boolean dispatched = ext.mods.gameserver.network.disruptor.DisruptorPacketRouter.getInstance().dispatch(client, packet, buf);
					if (!dispatched)
					{
						LOGGER.warn("[NETTY] Disruptor dispatch failed for packet {} from {}", packet.getClass().getSimpleName(), ctx.channel().remoteAddress());
						buf.release();
					}
					else if (ext.mods.config.ConfigServer.DEBUG_NET)
					{
						LOGGER.debug("[NETTY] Packet {} dispatched to Disruptor for {}", packet.getClass().getSimpleName(), ctx.channel().remoteAddress());
					}
				}
				else if (ext.mods.config.ConfigServer.DEBUG_NET)
				{
					LOGGER.warn("[NETTY] Packet readPacket returned false for {} from {}", packet.getClass().getSimpleName(), ctx.channel().remoteAddress());
				}
			}
			else if (ext.mods.config.ConfigServer.DEBUG_NET)
			{
				LOGGER.warn("[NETTY] Unhandled or NULL packet for opcode 0x{} in state {} from {}",
					Integer.toHexString(opcode), client.getState(), ctx.channel().remoteAddress());
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Error processing inbound packet from {}: {}", ctx.channel().remoteAddress(), e.getMessage());
		}
		finally
		{
			buf.release();
		}
	}
	
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception
	{
		if (ext.mods.config.ConfigServer.DEBUG_NET)
		{
			LOGGER.info("[NETTY] Inbound TCP connection INACTIVE (disconnected) from {}", ctx.channel().remoteAddress());
		}
		final GameClient client = ctx.channel().attr(CLIENT_KEY).get();
		if (client != null)
		{
			client.onDisconnection();
		}
		
		super.channelInactive(ctx);
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
	{
		if (!(cause instanceof IOException))
		{
			final String errorMsg = (cause != null) ? cause.toString() : "Unknown exception";
			LOGGER.warn("Netty exception on channel {}: {}", ctx.channel().remoteAddress(), errorMsg, cause);
		}
		ctx.close();
	}
}
