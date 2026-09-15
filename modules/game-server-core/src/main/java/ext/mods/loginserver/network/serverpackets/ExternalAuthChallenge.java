package ext.mods.loginserver.network.serverpackets;

/**
 * Resposta do LoginServer ao RequestExternalAuthChallenge (0xFD 0x0000).
 * Estrutura de fio: [0xFD (C) | 0x0000 (H) | len (H) | challenge (B[len])]
 */
public final class ExternalAuthChallenge extends L2LoginServerPacket
{
	private final byte[] _challenge;
	
	public ExternalAuthChallenge(byte[] challenge)
	{
		_challenge = challenge;
	}
	
	@Override
	protected void write()
	{
		writeC(0xFD);
		writeH(0x0000);
		if (_challenge != null && _challenge.length > 0)
		{
			writeH(_challenge.length);
			writeB(_challenge);
		}
		else
		{
			writeH(0);
		}
	}
}
