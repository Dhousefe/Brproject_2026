package ext.mods.gameserver.network.fermata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import ext.mods.gameserver.data.manager.FermataCfpManager;
import ext.mods.gameserver.model.fermata.ClientFingerprintIdentity;
import ext.mods.gameserver.network.GameClient;
import ext.mods.gameserver.network.clientpackets.fermata.ClientFingerprint;
import ext.mods.gameserver.network.serverpackets.fermata.ClientFingerprintAccepted;

class FermataCfpTest
{
	@Test
	void identityRecord_serializationRoundtrip()
	{
		final byte[] challengeId = new byte[16];
		Arrays.fill(challengeId, (byte) 0xAA);
		
		final byte[] identifier = new byte[32];
		for (int i = 0; i < 32; i++)
			identifier[i] = (byte) (i + 1);
		
		final ClientFingerprintIdentity identity = new ClientFingerprintIdentity(
			1, // revision
			ClientFingerprintIdentity.SOURCE_LOCAL_HARDWARE,
			1, // persistence
			2, // delivery
			identifier,
			challengeId,
			true // verifiedWithProof
		);
		
		assertTrue(identity.isValid());
		assertEquals(64, identity.toHexIdentifier().length());
		
		final byte[] record = identity.toRecordBytes();
		assertEquals(ClientFingerprintIdentity.RECORD_LENGTH, record.length);
		assertEquals(37, record.length);
		
		final ClientFingerprintIdentity restored = ClientFingerprintIdentity.fromRecordBytes(challengeId, record, true);
		assertNotNull(restored);
		assertEquals(1, restored.getRevision());
		assertEquals(ClientFingerprintIdentity.SOURCE_LOCAL_HARDWARE, restored.getSource());
		assertEquals(1, restored.getPersistence());
		assertEquals(2, restored.getDelivery());
		assertTrue(Arrays.equals(identifier, restored.getIdentifier()));
		assertTrue(restored.isVerifiedWithProof());
	}
	
	@Test
	void identityRecord_rejectsInvalidRevisionOrLength()
	{
		final byte[] challengeId = new byte[16];
		assertNull(ClientFingerprintIdentity.fromRecordBytes(challengeId, new byte[36], false));
		assertNull(ClientFingerprintIdentity.fromRecordBytes(challengeId, new byte[38], false));
		
		final byte[] invalidRevRecord = new byte[37];
		invalidRevRecord[0] = 0x02; // revision 2 (not supported)
		invalidRevRecord[1] = 0x00;
		assertNull(ClientFingerprintIdentity.fromRecordBytes(challengeId, invalidRevRecord, false));
	}
	
	@Test
	void cfpPayloadLengths_conformToFermataSpecification()
	{
		// Official specification lengths:
		// ClientFingerprint (0x0005): exactly 54 bytes
		assertEquals(54, ClientFingerprint.PAYLOAD_LENGTH);
		
		// ClientFingerprintAccepted (0x000A): exactly 89 bytes
		assertEquals(89, ClientFingerprintAccepted.EXPECTED_PAYLOAD_LENGTH);
		assertEquals(89, FermataCfpManager.ACCEPTED_PAYLOAD_LENGTH);
		
		// ClientFingerprintChallenge (0x0009): 140 to 585 bytes
		assertEquals(140, FermataCfpManager.CHALLENGE_PAYLOAD_LENGTH);
	}
	
	@Test
	void challengePayload_hasCorrectWireLayout()
	{
		final byte[] challengeId = new byte[16];
		Arrays.fill(challengeId, (byte) 0x11);
		
		final byte[] nonce = new byte[32];
		Arrays.fill(nonce, (byte) 0x22);
		
		final byte[] payload = FermataCfpManager.getInstance().buildChallengePayload(challengeId, nonce);
		assertEquals(140, payload.length);
		
		final ByteBuffer buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
		final int rev = buf.getShort() & 0xFFFF;
		assertEquals(1, rev);
		
		final byte[] readChallenge = new byte[16];
		buf.get(readChallenge);
		assertTrue(Arrays.equals(challengeId, readChallenge));
		
		final byte[] readNonce = new byte[32];
		buf.get(readNonce);
		assertTrue(Arrays.equals(nonce, readNonce));
	}
	
	@Test
	void acceptedPayload_hasCorrectWireLayout()
	{
		final byte[] challengeId = new byte[16];
		Arrays.fill(challengeId, (byte) 0x33);
		
		final byte[] identifier = new byte[32];
		Arrays.fill(identifier, (byte) 0x44);
		
		final ClientFingerprintIdentity identity = new ClientFingerprintIdentity(
			1, ClientFingerprintIdentity.SOURCE_LAUNCHER, 0, 0, identifier, challengeId, false
		);
		
		final byte[] token = new byte[32];
		Arrays.fill(token, (byte) 0x55);
		
		final byte[] accepted = FermataCfpManager.getInstance().buildAcceptedPayload(challengeId, identity, token);
		assertEquals(89, accepted.length);
		
		final ByteBuffer buf = ByteBuffer.wrap(accepted).order(ByteOrder.LITTLE_ENDIAN);
		final int rev = buf.getShort() & 0xFFFF;
		assertEquals(1, rev);
		
		final int status = buf.get() & 0xFF;
		assertEquals(0, status);
		
		final byte[] readChallenge = new byte[16];
		buf.get(readChallenge);
		assertTrue(Arrays.equals(challengeId, readChallenge));
	}
	
	@Test
	void gameClient_cfpLifecycleAndGating()
	{
		final GameClient client = new GameClient(null);
		
		// Legacy client has no pending challenge
		assertFalse(client.hasPendingCfpChallenge());
		assertFalse(client.isCfpExchangeCompleted());
		assertNull(client.getCfpIdentity());
		
		// When CFP challenge is issued
		final byte[] challenge = new byte[16];
		Arrays.fill(challenge, (byte) 0x77);
		client.setCfpChallenge(challenge, System.currentTimeMillis());
		
		assertTrue(client.hasPendingCfpChallenge());
		assertTrue(client.isFermataClient());
		assertFalse(client.isCfpExchangeCompleted());
		
		// When CFP completes
		final ClientFingerprintIdentity identity = new ClientFingerprintIdentity(
			1, 1, 0, 0, new byte[32], challenge, true
		);
		client.completeCfpExchange(identity);
		
		assertFalse(client.hasPendingCfpChallenge());
		assertTrue(client.isCfpExchangeCompleted());
		assertNotNull(client.getCfpIdentity());
		assertEquals(identity.toHexIdentifier(), client.getHWID());
		
		// Overwrites preliminary legacy HWID fallback deterministically
		client.setHWID("NoHWID-MAC");
		client.completeCfpExchange(identity);
		assertEquals(identity.toHexIdentifier(), client.getHWID());
		
		// Legacy guard flag validation
		assertFalse(client.hasLegacyGuard());
		client.setHasLegacyGuard(true);
		assertTrue(client.hasLegacyGuard());
		client.setHasLegacyGuard(false);
		assertFalse(client.hasLegacyGuard());
	}
}