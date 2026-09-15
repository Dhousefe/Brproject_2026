package ext.mods.gameserver.network.clientpackets.fermata;

import java.nio.charset.StandardCharsets;

import ext.mods.gameserver.data.manager.FermataCfpManager;
import ext.mods.gameserver.network.GameClient;
import ext.mods.gameserver.network.GameClient.GameClientState;
import ext.mods.gameserver.network.clientpackets.L2GameClientPacket;

/**
 * Pacote ClientFingerprintHello (C->S 0xFD 0x0006) da extensão de protocolo Fermata.
 * Enviado opcionalmente uma vez no estado AUTHED imediatamente após autenticação
 * e antes de qualquer operação de personagem.
 * Payload: 6 a 261 bytes.
 */
public final class ClientFingerprintHello extends L2GameClientPacket
{
	private int _revision;
	private int _flags;
	private String _platform;
	
	@Override
	protected void readImpl()
	{
		if (_buf.remaining() < 6)
			return;
		
		_revision = readH();
		_flags = readH();
		final int platformLen = readH();
		
		if (platformLen > 0 && _buf.remaining() >= platformLen)
		{
			final byte[] platformBytes = new byte[platformLen];
			readB(platformBytes);
			_platform = new String(platformBytes, StandardCharsets.UTF_8).trim();
		}
		else
		{
			_platform = "unknown";
		}
	}
	
	@Override
	protected void runImpl()
	{
		final GameClient client = getClient();
		if (client == null)
			return;
		
		if (client.getState() != GameClientState.AUTHED)
		{
			client.closeNow();
			return;
		}
		
		FermataCfpManager.getInstance().handleHello(client, _revision, _flags, _platform);
	}
	
	@Override
	protected boolean triggersOnActionRequest()
	{
		return false;
	}
}