package ext.mods.gameserver.network.clientpackets.fermata;

import ext.mods.gameserver.data.manager.FermataCfpManager;
import ext.mods.gameserver.model.fermata.ClientFingerprintIdentity;
import ext.mods.gameserver.network.GameClient;
import ext.mods.gameserver.network.GameClient.GameClientState;
import ext.mods.gameserver.network.clientpackets.L2GameClientPacket;

/**
 * Pacote ClientFingerprint (C->S 0xFD 0x0005) da extensão de protocolo Fermata.
 * Envia o registro de identidade selecionado vinculado ao desafio ativo.
 * Payload: Exatamente 54 bytes (16 ID de desafio + 37 registro de identidade + 1 flag de prova).
 */
public final class ClientFingerprint extends L2GameClientPacket
{
	public static final int PAYLOAD_LENGTH = 54;
	
	private byte[] _challengeId;
	private byte[] _identityRecord;
	private boolean _proofPresent;
	
	@Override
	protected void readImpl()
	{
		if (_buf.remaining() != PAYLOAD_LENGTH)
		{
			getClient().closeNow();
			return;
		}
		
		_challengeId = new byte[ClientFingerprintIdentity.CHALLENGE_ID_LENGTH];
		readB(_challengeId);
		
		_identityRecord = new byte[ClientFingerprintIdentity.RECORD_LENGTH];
		readB(_identityRecord);
		
		_proofPresent = (readC() != 0);
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
		
		FermataCfpManager.getInstance().handleFingerprint(client, _challengeId, _identityRecord, _proofPresent);
	}
	
	@Override
	protected boolean triggersOnActionRequest()
	{
		return false;
	}
}