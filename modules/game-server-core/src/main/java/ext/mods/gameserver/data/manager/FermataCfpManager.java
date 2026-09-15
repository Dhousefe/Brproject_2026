package ext.mods.gameserver.data.manager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;

import ext.mods.commons.logging.CLogger;
import ext.mods.config.ConfigProtection;
import ext.mods.gameserver.model.fermata.ClientFingerprintIdentity;
import ext.mods.gameserver.network.GameClient;
import ext.mods.gameserver.network.serverpackets.fermata.ClientFingerprintAccepted;
import ext.mods.gameserver.network.serverpackets.fermata.ClientFingerprintChallenge;

/**
 * Gerenciador da troca de identificação Client Fingerprint v1 (CFP) do protocolo Fermata.
 * Controla emissão de desafios de 16 bytes, validação de registros de 37 bytes,
 * síntese de identidade escopada à conexão e envio de confirmação de 89 bytes.
 */
public final class FermataCfpManager
{
	private static final CLogger LOGGER = new CLogger(FermataCfpManager.class.getName());
	private static final SecureRandom RNG = new SecureRandom();
	
	public static final int CHALLENGE_PAYLOAD_LENGTH = 140;
	public static final int ACCEPTED_PAYLOAD_LENGTH = 89;
	
	private FermataCfpManager()
	{
	}
	
	public static FermataCfpManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final FermataCfpManager INSTANCE = new FermataCfpManager();
	}
	
	/**
	 * Inicia a troca CFP caso habilitado nas configurações.
	 * Emite o desafio de 140 bytes e registra o estado no GameClient.
	 */
	public void handleHello(GameClient client, int revision, int clientFlags, String platform)
	{
		if (client == null)
			return;
		
		if (!ConfigProtection.FERMATA_CFP_ENABLED)
		{
			if (ConfigProtection.ENABLE_CONSOLE_LOG)
				LOGGER.info("CFP Hello received from {} but CFP is disabled on server. Silent ignore.", client);
			return;
		}
		
		if (revision != ClientFingerprintIdentity.REVISION_V1)
		{
			LOGGER.warn("CFP Hello from {} with unsupported revision {}. Closing connection.", client, revision);
			client.closeNow();
			return;
		}
		
		final byte[] challengeId = new byte[ClientFingerprintIdentity.CHALLENGE_ID_LENGTH];
		RNG.nextBytes(challengeId);
		
		final byte[] serverNonce = new byte[32];
		RNG.nextBytes(serverNonce);
		
		client.setCfpChallenge(challengeId, System.currentTimeMillis());
		
		final byte[] challengePayload = buildChallengePayload(challengeId, serverNonce);
		client.sendPacket(new ClientFingerprintChallenge(challengePayload));
	}
	
	/**
	 * Constrói o corpo do pacote ClientFingerprintChallenge (140 bytes):
	 * - revision: u16 (2 bytes) = 1
	 * - challenge_id: 16 bytes
	 * - server_nonce: 32 bytes
	 * - policy_flags: u32 (4 bytes)
	 * - padding/reserved: 86 bytes
	 */
	public byte[] buildChallengePayload(byte[] challengeId, byte[] serverNonce)
	{
		final ByteBuffer buf = ByteBuffer.allocate(CHALLENGE_PAYLOAD_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
		buf.putShort((short) ClientFingerprintIdentity.REVISION_V1);
		buf.put(challengeId);
		buf.put(serverNonce);
		
		int policyFlags = 0;
		if (ConfigProtection.FERMATA_CFP_REQUIRE_PROOF)
			policyFlags |= 1; // Bit 0: requer prova de chave privada
		if (ConfigProtection.FERMATA_CFP_ALLOW_CONNECTION_IDENTITY_FALLBACK)
			policyFlags |= 2; // Bit 1: permite identidade sintética de conexão
		
		buf.putInt(policyFlags);
		
		// Preenche restante até 140 bytes com zeros estruturados
		while (buf.hasRemaining())
			buf.put((byte) 0);
		
		return buf.array();
	}
	
	/**
	 * Processa o pacote ClientFingerprint (54 bytes):
	 * - challenge_id: 16 bytes
	 * - identity record: 37 bytes
	 * - proof_present: 1 byte
	 */
	public void handleFingerprint(GameClient client, byte[] challengeId, byte[] recordBytes, boolean proofPresent)
	{
		if (client == null)
			return;
		
		final byte[] pendingChallenge = client.getCfpPendingChallengeId();
		if (pendingChallenge == null || !Arrays.equals(pendingChallenge, challengeId))
		{
			LOGGER.warn("CFP challenge mismatch for {}. Expected {}, received {}.", client, Arrays.toString(pendingChallenge), Arrays.toString(challengeId));
			client.closeNow();
			return;
		}
		
		final long challengeTime = client.getCfpPendingChallengeTimestamp();
		final long elapsed = (System.currentTimeMillis() - challengeTime) / 1000L;
		if (elapsed > ConfigProtection.FERMATA_CFP_CHALLENGE_TIMEOUT_SECONDS)
		{
			LOGGER.warn("CFP challenge expired for {} (elapsed {}s).", client, elapsed);
			client.closeNow();
			return;
		}
		
		ClientFingerprintIdentity identity = ClientFingerprintIdentity.fromRecordBytes(challengeId, recordBytes, false);
		if (identity == null || !identity.isValid())
		{
			if (ConfigProtection.FERMATA_CFP_ALLOW_CONNECTION_IDENTITY_FALLBACK)
			{
				// O servidor fornece uma identidade nova, válida só para a conexão
				identity = createSyntheticConnectionIdentity(client, challengeId);
			}
			else
			{
				LOGGER.warn("CFP invalid identity record rejected for {}.", client);
				client.closeNow();
				return;
			}
		}
		
		if (proofPresent)
		{
			// Aguarda o pacote subsequente ClientFingerprintProof
			client.setPendingCfpIdentity(identity);
		}
		else
		{
			if (ConfigProtection.FERMATA_CFP_REQUIRE_PROOF)
			{
				LOGGER.warn("CFP requires proof, but client {} did not provide one.", client);
				client.closeNow();
				return;
			}
			
			completeExchange(client, identity);
		}
	}
	
	/**
	 * Processa o pacote ClientFingerprintProof (150 a 16534 bytes).
	 */
	public void handleProof(GameClient client, byte[] challengeId, byte[] proofBytes)
	{
		if (client == null)
			return;
		
		final byte[] pendingChallenge = client.getCfpPendingChallengeId();
		if (pendingChallenge == null || !Arrays.equals(pendingChallenge, challengeId))
		{
			LOGGER.warn("CFP proof challenge mismatch for {}.", client);
			client.closeNow();
			return;
		}
		
		final ClientFingerprintIdentity pendingIdentity = client.getPendingCfpIdentity();
		if (pendingIdentity == null)
		{
			LOGGER.warn("CFP proof received without prior identity for {}.", client);
			client.closeNow();
			return;
		}
		
		// Valida prova criptográfica básica de não-vazio e comprimento
		final boolean proofValid = proofBytes != null && proofBytes.length >= 64;
		if (!proofValid && ConfigProtection.FERMATA_CFP_REQUIRE_PROOF)
		{
			LOGGER.warn("CFP proof invalid or malformed for {}.", client);
			client.closeNow();
			return;
		}
		
		final ClientFingerprintIdentity verifiedIdentity = new ClientFingerprintIdentity(
			pendingIdentity.getRevision(),
			pendingIdentity.getSource(),
			pendingIdentity.getPersistence(),
			pendingIdentity.getDelivery(),
			pendingIdentity.getIdentifier(),
			challengeId,
			proofValid
		);
		
		completeExchange(client, verifiedIdentity);
	}
	
	private void completeExchange(GameClient client, ClientFingerprintIdentity identity)
	{
		client.completeCfpExchange(identity);
		
		final byte[] sessionToken = new byte[32];
		RNG.nextBytes(sessionToken);
		
		final byte[] acceptedPayload = buildAcceptedPayload(identity.getChallengeId(), identity, sessionToken);
		client.sendPacket(new ClientFingerprintAccepted(acceptedPayload));
		
		if (ConfigProtection.ENABLE_CONSOLE_LOG)
			LOGGER.info("CFP exchange completed successfully for {} with ID: {}", client, identity.toHexIdentifier());
	}
	
	/**
	 * Constrói o corpo do pacote ClientFingerprintAccepted (89 bytes):
	 * - revision: u16 (2 bytes) = 1
	 * - status: u8 (1 byte) = 0 (OK)
	 * - challenge_id: 16 bytes
	 * - accepted_identity: 37 bytes
	 * - session_identity_token: 32 bytes
	 * - flags: u8 (1 byte)
	 * Total: 2 + 1 + 16 + 37 + 32 + 1 = 89 bytes
	 */
	public byte[] buildAcceptedPayload(byte[] challengeId, ClientFingerprintIdentity identity, byte[] sessionToken)
	{
		final ByteBuffer buf = ByteBuffer.allocate(ACCEPTED_PAYLOAD_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
		buf.putShort((short) ClientFingerprintIdentity.REVISION_V1);
		buf.put((byte) 0); // status: 0 = OK
		buf.put(challengeId != null && challengeId.length == 16 ? challengeId : new byte[16]);
		buf.put(identity.toRecordBytes());
		buf.put(sessionToken != null && sessionToken.length == 32 ? sessionToken : new byte[32]);
		buf.put((byte) 0); // flags
		return buf.array();
	}
	
	private ClientFingerprintIdentity createSyntheticConnectionIdentity(GameClient client, byte[] challengeId)
	{
		final byte[] syntheticId = new byte[ClientFingerprintIdentity.IDENTIFIER_LENGTH];
		RNG.nextBytes(syntheticId);
		return new ClientFingerprintIdentity(
			ClientFingerprintIdentity.REVISION_V1,
			ClientFingerprintIdentity.SOURCE_CONNECTION_SYNTHETIC,
			0,
			0,
			syntheticId,
			challengeId,
			false
		);
	}
}