package ext.mods.gameserver.network.serverpackets.fermata;

import ext.mods.gameserver.network.serverpackets.L2GameServerPacket;

/**
 * Pacote ClientFingerprintChallenge (S->C 0xFD 0x0009) da extensão de protocolo Fermata.
 * Transmite o desafio criptográfico do servidor para a identificação CFP (140 a 585 bytes).
 */
public final class ClientFingerprintChallenge extends L2GameServerPacket
{
	private final byte[] _payload;
	
	public ClientFingerprintChallenge(byte[] payload)
	{
		_payload = payload;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFD);
		writeH(0x0009);
		if (_payload != null && _payload.length > 0)
			writeB(_payload);
	}
}