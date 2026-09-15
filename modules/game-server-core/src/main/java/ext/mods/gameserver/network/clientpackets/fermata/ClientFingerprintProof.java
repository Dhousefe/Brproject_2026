package ext.mods.gameserver.network.clientpackets.fermata;

import ext.mods.gameserver.data.manager.FermataCfpManager;
import ext.mods.gameserver.model.fermata.ClientFingerprintIdentity;
import ext.mods.gameserver.network.GameClient;
import ext.mods.gameserver.network.GameClient.GameClientState;
import ext.mods.gameserver.network.clientpackets.L2GameClientPacket;

/**
 * Pacote ClientFingerprintProof (C->S 0xFD 0x0007) da extensão de protocolo Fermata.
 * Transmite a prova criptográfica vinculada à chave privada do cliente e ao ID de desafio.
 * Payload: 150 a 16534 bytes.
 */
public final class ClientFingerprintProof extends L2GameClientPacket
{
	private byte[] _challengeId;
	private byte[] _proofBytes;
	
	@Override
	protected void readImpl()
	{
		final int remaining = _buf.remaining();
		if (remaining < 150 || remaining > 16534)
		{
			getClient().closeNow();
			return;
		}
		
		_challengeId = new byte[ClientFingerprintIdentity.CHALLENGE_ID_LENGTH];
		readB(_challengeId);
		
		_proofBytes = new byte[remaining - ClientFingerprintIdentity.CHALLENGE_ID_LENGTH];
		readB(_proofBytes);
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
		
		FermataCfpManager.getInstance().handleProof(client, _challengeId, _proofBytes);
	}
	
	@Override
	protected boolean triggersOnActionRequest()
	{
		return false;
	}
}