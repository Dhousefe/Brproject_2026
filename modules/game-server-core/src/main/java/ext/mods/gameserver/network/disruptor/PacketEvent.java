package ext.mods.gameserver.network.disruptor;

import ext.mods.commons.mmocore.ReceivablePacket;
import ext.mods.gameserver.network.GameClient;
import io.netty.buffer.ByteBuf;

/**
 * Evento pré-alocado no RingBuffer do LMAX Disruptor para transporte e execução de pacotes de rede.
 * Possui limpeza estrita de ciclo de vida (.clear()) para prevenir contaminação de sessões entre ciclos.
 */
public final class PacketEvent
{
	private GameClient _client;
	private ReceivablePacket<GameClient> _packet;
	private ByteBuf _rawBuffer;
	private long _sequenceId;
	
	// Cache-line padding para prevenir False Sharing em arquiteturas de alto rendimento
	public long p1, p2, p3, p4, p5, p6, p7;
	
	public void set(GameClient client, ReceivablePacket<GameClient> packet, ByteBuf rawBuffer, long sequenceId)
	{
		_client = client;
		_packet = packet;
		_rawBuffer = rawBuffer;
		_sequenceId = sequenceId;
	}
	
	public GameClient getClient()
	{
		return _client;
	}
	
	public ReceivablePacket<GameClient> getPacket()
	{
		return _packet;
	}
	
	public ByteBuf getRawBuffer()
	{
		return _rawBuffer;
	}
	
	public long getSequenceId()
	{
		return _sequenceId;
	}
	
	/**
	 * Limpeza obrigatória após o consumo do evento pelo Disruptor.
	 * Garante que referências de jogadores anteriores não fiquem órfãs no RingBuffer
	 * e libera buffers retidos do Netty sem memory leaks.
	 */
	public void clear()
	{
		_client = null;
		_packet = null;
		if (_rawBuffer != null)
		{
			try
			{
				if (_rawBuffer.refCnt() > 0)
				{
					_rawBuffer.release();
				}
			}
			catch (Exception ignored)
			{
			}
			_rawBuffer = null;
		}
		_sequenceId = 0L;
	}
}
