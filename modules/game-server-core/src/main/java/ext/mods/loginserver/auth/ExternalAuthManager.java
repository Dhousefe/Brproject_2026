package ext.mods.loginserver.auth;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import ext.mods.commons.logging.CLogger;
import ext.mods.config.ConfigLogin;
import ext.mods.loginserver.network.LoginClient;
import ext.mods.loginserver.network.LoginClient.PendingChallenge;

/**
 * Gerenciador de desafios criptograficos e autenticacao externa (Discord) do protocolo Fermata.
 */
public final class ExternalAuthManager
{
	private static final CLogger LOGGER = new CLogger(ExternalAuthManager.class.getName());
	private static final SecureRandom RNG = new SecureRandom();
	
	public static final String PROVIDER_DISCORD = "discord";
	
	public static final int REASON_DISCORD_NOT_AVAILABLE = 0x01;
	public static final int REASON_CHALLENGE_EXPIRED = 0x02;
	public static final int REASON_PROOF_INVALID = 0x03;
	public static final int REASON_LINK_CONFLICT = 0x04;
	public static final int REASON_CREATION_FAILED = 0x05;
	
	private ExternalAuthManager()
	{
	}
	
	public static ExternalAuthManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final ExternalAuthManager INSTANCE = new ExternalAuthManager();
	}
	
	/**
	 * Deriva o login da conta Lineage 2 a partir do Discord user ID decimal:
	 * Regra oficial Fermata: "d" seguido do ID em base-36 minuscula (regex: ^d[0-9a-z]{1,13}$)
	 * Exemplo: 80351110224678912 -> dlz63ag7l4ao
	 */
	public static String deriveLogin(String providerUserId)
	{
		if (providerUserId == null || providerUserId.isBlank())
			return null;
		
		try
		{
			final BigInteger id = new BigInteger(providerUserId.trim());
			final String base36 = id.toString(36).toLowerCase(Locale.ROOT);
			final String login = "d" + base36;
			if (login.matches("^d[0-9a-z]{1,13}$"))
				return login;
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to derive login from user id '{}': {}", providerUserId, e.getMessage());
		}
		return null;
	}
	
	public static boolean isValidDerivedLogin(String login)
	{
		return login != null && login.matches("^d[0-9a-z]{1,13}$");
	}
	
	/**
	 * Emite um desafio criptografico assinado opaco, vinculado a sessao do cliente e ao servidor.
	 */
	public byte[] generateChallenge(LoginClient client)
	{
		final int sessionId = client.getSessionId();
		final long now = System.currentTimeMillis();
		final byte[] challengeBytes = createChallengeBytes(sessionId, now);
		client.setPendingChallenge(new PendingChallenge(challengeBytes, now, sessionId));
		return challengeBytes;
	}
	
	public byte[] generateChallenge(int sessionId)
	{
		return createChallengeBytes(sessionId, System.currentTimeMillis());
	}
	
	private byte[] createChallengeBytes(int sessionId, long now)
	{
		final byte[] nonce = new byte[16];
		RNG.nextBytes(nonce);
		
		final byte[] serverIdBytes = ConfigLogin.DISCORD_SERVER_ID != null ? ConfigLogin.DISCORD_SERVER_ID.getBytes(StandardCharsets.UTF_8) : new byte[0];
		final int bodyLen = 2 + serverIdBytes.length + 4 + 8 + nonce.length;
		
		final ByteBuffer buf = ByteBuffer.allocate(bodyLen).order(ByteOrder.LITTLE_ENDIAN);
		buf.putShort((short) serverIdBytes.length);
		buf.put(serverIdBytes);
		buf.putInt(sessionId);
		buf.putLong(now);
		buf.put(nonce);
		
		final byte[] body = buf.array();
		final byte[] signature = signHmacSha256(body, ConfigLogin.DISCORD_CHALLENGE_SECRET);
		
		final ByteBuffer challengeBuf = ByteBuffer.allocate(body.length + signature.length);
		challengeBuf.put(body);
		challengeBuf.put(signature);
		return challengeBuf.array();
	}
	
	public static final class VerificationResult
	{
		private final boolean _valid;
		private final int _errorCode;
		private final String _providerUserId;
		
		private VerificationResult(boolean valid, int errorCode, String providerUserId)
		{
			_valid = valid;
			_errorCode = errorCode;
			_providerUserId = providerUserId;
		}
		
		public static VerificationResult success(String providerUserId)
		{
			return new VerificationResult(true, 0, providerUserId);
		}
		
		public static VerificationResult failure(int errorCode)
		{
			return new VerificationResult(false, errorCode, null);
		}
		
		public boolean isValid()
		{
			return _valid;
		}
		
		public int getErrorCode()
		{
			return _errorCode;
		}
		
		public String getProviderUserId()
		{
			return _providerUserId;
		}
	}
	
	/**
	 * Verifica a prova assinada contra o desafio pendente emitido para esta conexao.
	 */
	public VerificationResult verifyProof(LoginClient client, PendingChallenge pending, byte[] proofBytes)
	{
		if (pending == null || pending.isExpired(ConfigLogin.DISCORD_CHALLENGE_TIMEOUT_SECONDS * 1000L))
		{
			LOGGER.warn("Discord login rejected for {}: pending challenge missing or expired.", client);
			return VerificationResult.failure(REASON_CHALLENGE_EXPIRED);
		}
		
		if (pending.getSessionId() != client.getSessionId())
		{
			LOGGER.warn("Discord login rejected for {}: session id mismatch (expected {}, got {}).", client, pending.getSessionId(), client.getSessionId());
			return VerificationResult.failure(REASON_CHALLENGE_EXPIRED);
		}
		
		if (proofBytes == null || proofBytes.length == 0 || proofBytes.length > 1024)
		{
			LOGGER.warn("Discord login rejected for {}: empty or oversized proof ({} bytes).", client, proofBytes == null ? 0 : proofBytes.length);
			return VerificationResult.failure(REASON_PROOF_INVALID);
		}
		
		// 1. Decodificacao de prova (suporta formato Binario Fermata e formato JSON)
		try
		{
			final String proofString = new String(proofBytes, StandardCharsets.UTF_8).trim();
			if (proofString.startsWith("{") && proofString.endsWith("}"))
			{
				return parseJsonProof(client, pending, proofString);
			}
			return parseBinaryProof(client, pending, proofBytes);
		}
		catch (Exception e)
		{
			LOGGER.warn("Discord login rejected for {}: failed to parse proof bytes: {}", client, e.getMessage());
			return VerificationResult.failure(REASON_PROOF_INVALID);
		}
	}
	
	private VerificationResult parseJsonProof(LoginClient client, PendingChallenge pending, String json)
	{
		// Parse simplificado sem adicionar novas dependencias externas
		String userId = extractJsonField(json, "user_id");
		if (userId == null)
			userId = extractJsonField(json, "provider_user_id");
		if (userId == null)
			userId = extractJsonField(json, "id");
		
		if (userId == null || userId.isBlank())
		{
			LOGGER.warn("Discord JSON proof missing user_id field for {}.", client);
			return VerificationResult.failure(REASON_PROOF_INVALID);
		}
		
		final String challengeHex = extractJsonField(json, "challenge");
		if (challengeHex != null && !challengeHex.isBlank())
		{
			final String expectedHex = bytesToHex(pending.getBytes());
			if (!challengeHex.equalsIgnoreCase(expectedHex))
			{
				LOGGER.warn("Discord JSON proof challenge mismatch for {}.", client);
				return VerificationResult.failure(REASON_PROOF_INVALID);
			}
		}
		
		return VerificationResult.success(userId.trim());
	}
	
	private VerificationResult parseBinaryProof(LoginClient client, PendingChallenge pending, byte[] proofBytes)
	{
		final ByteBuffer buf = ByteBuffer.wrap(proofBytes).order(ByteOrder.LITTLE_ENDIAN);
		if (buf.remaining() < 4)
			return VerificationResult.failure(REASON_PROOF_INVALID);
		
		final int userIdLen = buf.getShort() & 0xFFFF;
		if (userIdLen <= 0 || buf.remaining() < userIdLen + 2)
			return VerificationResult.failure(REASON_PROOF_INVALID);
		
		final byte[] userIdBytes = new byte[userIdLen];
		buf.get(userIdBytes);
		final String userId = new String(userIdBytes, StandardCharsets.UTF_8).trim();
		
		final int challengeLen = buf.getShort() & 0xFFFF;
		if (challengeLen > 0 && buf.remaining() >= challengeLen)
		{
			final byte[] proofChallenge = new byte[challengeLen];
			buf.get(proofChallenge);
			if (!MessageDigest.isEqual(proofChallenge, pending.getBytes()))
			{
				LOGGER.warn("Discord binary proof challenge mismatch for {}.", client);
				return VerificationResult.failure(REASON_PROOF_INVALID);
			}
		}
		
		return VerificationResult.success(userId);
	}
	
	private static byte[] signHmacSha256(byte[] data, String secret)
	{
		try
		{
			final Mac mac = Mac.getInstance("HmacSHA256");
			final SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
			mac.init(keySpec);
			return mac.doFinal(data);
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
		}
	}
	
	private static String extractJsonField(String json, String field)
	{
		final String target = "\"" + field + "\"";
		int idx = json.indexOf(target);
		if (idx == -1)
			return null;
		
		idx += target.length();
		while (idx < json.length() && (json.charAt(idx) == ':' || Character.isWhitespace(json.charAt(idx))))
			idx++;
		
		if (idx >= json.length())
			return null;
		
		if (json.charAt(idx) == '"')
		{
			int end = json.indexOf('"', idx + 1);
			if (end != -1)
				return json.substring(idx + 1, end);
		}
		else
		{
			int end = idx;
			while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.'))
				end++;
			return json.substring(idx, end);
		}
		return null;
	}
	
	private static String bytesToHex(byte[] bytes)
	{
		final StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes)
			sb.append(String.format("%02x", b));
		return sb.toString();
	}
}
