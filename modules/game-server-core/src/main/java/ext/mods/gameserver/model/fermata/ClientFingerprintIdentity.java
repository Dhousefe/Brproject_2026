package ext.mods.gameserver.model.fermata;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Representa o registro de identidade de 37 bytes selecionado no protocolo Client Fingerprint v1 (CFP).
 * Formato de fio:
 * - revision: u16 (little-endian, fixo em 1)
 * - source: u8 (origem do identificador: hardware, navegador, launcher, conexao sintetica)
 * - persistence: u8
 * - delivery: u8
 * - identifier: 32 bytes (hash SHA-256 ou identificador unico)
 */
public final class ClientFingerprintIdentity
{
	public static final int RECORD_LENGTH = 37;
	public static final int IDENTIFIER_LENGTH = 32;
	public static final int CHALLENGE_ID_LENGTH = 16;
	
	public static final int REVISION_V1 = 1;
	
	// Fontes conhecidas de identidade CFP
	public static final int SOURCE_UNSPECIFIED = 0;
	public static final int SOURCE_LOCAL_HARDWARE = 1;
	public static final int SOURCE_BROWSER = 2;
	public static final int SOURCE_LAUNCHER = 3;
	public static final int SOURCE_CONNECTION_SYNTHETIC = 4;
	
	private final int _revision;
	private final int _source;
	private final int _persistence;
	private final int _delivery;
	private final byte[] _identifier;
	private final byte[] _challengeId;
	private final boolean _verifiedWithProof;
	private final long _creationTime;
	
	public ClientFingerprintIdentity(int revision, int source, int persistence, int delivery, byte[] identifier, byte[] challengeId, boolean verifiedWithProof)
	{
		_revision = revision;
		_source = source;
		_persistence = persistence;
		_delivery = delivery;
		_identifier = (identifier != null && identifier.length == IDENTIFIER_LENGTH) ? Arrays.copyOf(identifier, IDENTIFIER_LENGTH) : new byte[IDENTIFIER_LENGTH];
		_challengeId = (challengeId != null && challengeId.length == CHALLENGE_ID_LENGTH) ? Arrays.copyOf(challengeId, CHALLENGE_ID_LENGTH) : new byte[CHALLENGE_ID_LENGTH];
		_verifiedWithProof = verifiedWithProof;
		_creationTime = System.currentTimeMillis();
	}
	
	public static ClientFingerprintIdentity fromRecordBytes(byte[] challengeId, byte[] record, boolean verifiedWithProof)
	{
		if (record == null || record.length != RECORD_LENGTH)
			return null;
		
		final ByteBuffer buf = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN);
		final int revision = buf.getShort() & 0xFFFF;
		if (revision != REVISION_V1)
			return null;
		
		final int source = buf.get() & 0xFF;
		final int persistence = buf.get() & 0xFF;
		final int delivery = buf.get() & 0xFF;
		final byte[] identifier = new byte[IDENTIFIER_LENGTH];
		buf.get(identifier);
		
		return new ClientFingerprintIdentity(revision, source, persistence, delivery, identifier, challengeId, verifiedWithProof);
	}
	
	public byte[] toRecordBytes()
	{
		final ByteBuffer buf = ByteBuffer.allocate(RECORD_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
		buf.putShort((short) _revision);
		buf.put((byte) _source);
		buf.put((byte) _persistence);
		buf.put((byte) _delivery);
		buf.put(_identifier);
		return buf.array();
	}
	
	public int getRevision()
	{
		return _revision;
	}
	
	public int getSource()
	{
		return _source;
	}
	
	public int getPersistence()
	{
		return _persistence;
	}
	
	public int getDelivery()
	{
		return _delivery;
	}
	
	public byte[] getIdentifier()
	{
		return _identifier;
	}
	
	public byte[] getChallengeId()
	{
		return _challengeId;
	}
	
	public boolean isVerifiedWithProof()
	{
		return _verifiedWithProof;
	}
	
	public long getCreationTime()
	{
		return _creationTime;
	}
	
	public String toHexIdentifier()
	{
		return HexFormat.of().formatHex(_identifier);
	}
	
	public boolean isValid()
	{
		if (_revision != REVISION_V1)
			return false;
		
		// Rejeitar placeholder vazio (tudo zero) se não for identidade sintética permitida
		boolean allZero = true;
		for (byte b : _identifier)
		{
			if (b != 0)
			{
				allZero = false;
				break;
			}
		}
		return !allZero;
	}
	
	@Override
	public String toString()
	{
		return "ClientFingerprintIdentity[rev=" + _revision + ", src=" + _source + ", id=" + toHexIdentifier() + ", verified=" + _verifiedWithProof + "]";
	}
}