package ext.mods.loginserver.network.clientpackets;

import ext.mods.config.ConfigLogin;
import ext.mods.loginserver.auth.ExternalAuthManager;
import ext.mods.loginserver.network.LoginClient;
import ext.mods.loginserver.network.serverpackets.ExternalAuthChallenge;
import ext.mods.loginserver.network.serverpackets.ExternalAuthRejected;

/**
 * Cliente solicita desafio para autenticacao externa via Discord (0xFD 0x0000).
 * Estrutura de fio: [0xFD (C) | 0x0000 (H) | session_id (D)]
 */
public final class RequestExternalAuthChallenge extends L2LoginClientPacket
{
	private int _sessionId;
	
	@Override
	public boolean readImpl()
	{
		if (super._buf.remaining() >= 4)
		{
			_sessionId = readD();
			return true;
		}
		return false;
	}
	
	@Override
	public void run()
	{
		final LoginClient client = getClient();
		
		// Valida sessao da conexao
		if (_sessionId != client.getSessionId())
		{
			LOGGER.warn("RequestExternalAuthChallenge session id mismatch for {}: expected {}, received {}.", client, client.getSessionId(), _sessionId);
			client.close(ExternalAuthRejected.REASON_CHALLENGE_EXPIRED);
			return;
		}
		
		// Se o login por Discord estiver desligado, responde categoria 0x01
		if (!ConfigLogin.DISCORD_LOGIN_ENABLED)
		{
			client.sendPacket(ExternalAuthRejected.REASON_NOT_AVAILABLE);
			return;
		}
		
		// Emite o desafio opaco assinado
		final byte[] challenge = ExternalAuthManager.getInstance().generateChallenge(client);
		client.sendPacket(new ExternalAuthChallenge(challenge));
	}
}
