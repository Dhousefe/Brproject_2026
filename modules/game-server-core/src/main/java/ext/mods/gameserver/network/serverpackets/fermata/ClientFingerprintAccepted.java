package ext.mods.gameserver.network.serverpackets.fermata;

import ext.mods.gameserver.network.serverpackets.L2GameServerPacket;

/**
 * Pacote ClientFingerprintAccepted (S->C 0xFD 0x000A) da extensão de protocolo Fermata.
 * Confirma o aceite da identidade CFP para o ciclo de vida da conexão atual (exatamente 89 bytes).
 */
public final class ClientFingerprintAccepted extends L2GameServerPacket
{
	public static final int EXPECTED_PAYLOAD_LENGTH = 89;
	
	private final byte[] _payload;
	
	public ClientFingerprintAccepted(byte[] payload)
	{
		_payload = payload;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFD);
		writeH(0x000A);
		if (_payload != null && _payload.length > 0)
			writeB(_payload);
	}
}