package ext.mods.loginserver.network.clientpackets;

import ext.mods.loginserver.LoginController;
import ext.mods.loginserver.auth.ExternalAuthManager;
import ext.mods.loginserver.auth.ExternalAuthManager.VerificationResult;
import ext.mods.loginserver.data.sql.AccountTable;
import ext.mods.loginserver.data.sql.AccountTable.ExternalAuthResolution;
import ext.mods.loginserver.network.LoginClient;
import ext.mods.loginserver.network.LoginClient.PendingChallenge;
import ext.mods.loginserver.network.serverpackets.ExternalAuthRejected;

/**
 * Cliente envia a prova assinada do Discord vinculada ao desafio pendente (0xFD 0x0001).
 * Estrutura de fio: [0xFD (C) | 0x0001 (H) | session_id (D) | len (H) | proof (B[len])]
 */
public final class RequestExternalAuthLogin extends L2LoginClientPacket
{
	private int _sessionId;
	private byte[] _proof;
	
	@Override
	public boolean readImpl()
	{
		if (super._buf.remaining() >= 6)
		{
			_sessionId = readD();
			final int len = readH();
			if (len > 0 && len <= 1024 && super._buf.remaining() >= len)
			{
				_proof = new byte[len];
				readB(_proof);
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void run()
	{
		final LoginClient client = getClient();
		
		// 1. Valida session id
		if (_sessionId != client.getSessionId())
		{
			LOGGER.warn("RequestExternalAuthLogin session id mismatch for {}: expected {}, received {}.", client, client.getSessionId(), _sessionId);
			client.close(ExternalAuthRejected.REASON_CHALLENGE_EXPIRED);
			return;
		}
		
		// 2. Consome o desafio pendente imediatamente (anti-replay: nunca pode ser reutilizado)
		final PendingChallenge pending = client.consumePendingChallenge();
		if (pending == null)
		{
			LOGGER.warn("RequestExternalAuthLogin rejected for {}: no pending challenge found.", client);
			client.close(ExternalAuthRejected.REASON_CHALLENGE_EXPIRED);
			return;
		}
		
		// 3. Verifica a prova criptografica
		final VerificationResult vResult = ExternalAuthManager.getInstance().verifyProof(client, pending, _proof);
		if (!vResult.isValid())
		{
			client.close(new ExternalAuthRejected(vResult.getErrorCode()));
			return;
		}
		
		final String providerUserId = vResult.getProviderUserId();
		final String derivedLogin = ExternalAuthManager.deriveLogin(providerUserId);
		if (derivedLogin == null)
		{
			LOGGER.warn("Failed to derive valid login from Discord user ID '{}' for {}.", providerUserId, client);
			client.close(ExternalAuthRejected.REASON_PROOF_INVALID);
			return;
		}
		
		// 4. Resolve ou cria a conta no banco de dados com atomicidade
		final ExternalAuthResolution res = AccountTable.getInstance().resolveOrCreateExternalAccount(
			ExternalAuthManager.PROVIDER_DISCORD, providerUserId, derivedLogin);
		
		switch (res.getStatus())
		{
			case CONFLICT:
				client.close(ExternalAuthRejected.REASON_LINK_CONFLICT);
				return;
				
			case CREATION_FAILED:
				client.close(ExternalAuthRejected.REASON_CREATION_FAILED);
				return;
				
			case SUCCESS:
				LoginController.getInstance().finishExternalAuthLogin(client, res.getAccount());
				break;
		}
	}
}
