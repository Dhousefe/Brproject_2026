package ext.mods.gameserver.network.disruptor;

import com.lmax.disruptor.EventHandler;
import ext.mods.commons.logging.CLogger;
import ext.mods.commons.mmocore.ReceivablePacket;
import ext.mods.gameserver.network.GameClient;

/**
 * Consumidor de eventos do Disruptor.
 * Executa pacotes de forma serial garantindo o Single-Writer Principle para cada partição,
 * limpando o ciclo de vida do evento no bloco finally com event.clear().
 */
public final class PacketConsumer implements EventHandler<PacketEvent>
{
	private static final CLogger LOGGER = new CLogger(PacketConsumer.class.getName());
	private final int _partitionId;
	
	public PacketConsumer(int partitionId)
	{
		_partitionId = partitionId;
	}
	
	public int getPartitionId()
	{
		return _partitionId;
	}
	
	@Override
	public void onEvent(PacketEvent event, long sequence, boolean endOfBatch) throws Exception
	{
		final GameClient client = event.getClient();
		final ReceivablePacket<GameClient> packet = event.getPacket();
		
		try
		{
			if (client == null || packet == null)
			{
				return;
			}
			
			// Se o cliente desconectou ou perdeu a conexão ativa, descarta com segurança
			if (client.getNettyConnection() != null && !client.getNettyConnection().isConnected())
			{
				return;
			}
			
			// Executa o pacote preservando a ordem FIFO absoluta de chegada na partição
			packet.run();
		}
		catch (Throwable t)
		{
			LOGGER.error("Error executing packet [{}] for client [{}] on partition [{}]: {}",
				packet != null ? packet.getClass().getSimpleName() : "Unknown",
				client,
				_partitionId,
				t.getMessage(),
				t);
		}
		finally
		{
			// Limpeza estrita e obrigatória: anula referências e libera qualquer buffer retido
			event.clear();
		}
	}
}
