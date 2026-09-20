package ext.mods.gameserver.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ext.mods.config.ConfigProtection;
import ext.mods.gameserver.model.fermata.ClientFingerprintIdentity;
import ext.mods.protection.hwid.crypt.FirstKey;

public class LegacyClientCompatibilityTest
{
	private boolean _savedAllowGuard;
	
	@BeforeEach
	public void setUp()
	{
		_savedAllowGuard = ConfigProtection.ALLOW_GUARD_SYSTEM;
		// Inicializa chaves do Guard para testes se ainda não carregadas
		if (ConfigProtection.GUARD_CLIENT_CRYPT_KEY == null)
		{
			byte[] dummyKey = new byte[32];
			ConfigProtection.GUARD_CLIENT_CRYPT_KEY = FirstKey.expandKey(dummyKey, 32);
			ConfigProtection.GUARD_CLIENT_CRYPT = FirstKey.expandKey(dummyKey, 32);
			ConfigProtection.GUARD_SERVER_CRYPT_KEY = FirstKey.expandKey(dummyKey, 32);
			ConfigProtection.GUARD_SERVER_CRYPT = FirstKey.expandKey(dummyKey, 32);
		}
	}
	
	@AfterEach
	public void tearDown()
	{
		ConfigProtection.ALLOW_GUARD_SYSTEM = _savedAllowGuard;
	}
	
	@Test
	public void testLegacyVanillaClientBlowfishAndHwid()
	{
		ConfigProtection.ALLOW_GUARD_SYSTEM = true;
		final GameClient client = new GameClient(null);
		
		assertFalse(client.hasLegacyGuard());
		assertFalse(client.isFermataClient());
		
		client.setAccountName("legacy_player");
		final String hwid = client.getHWID();
		assertNotNull(hwid);
		assertTrue(hwid.startsWith("LEGACY-"), "Cliente clássico vanilla deve possuir prefixo LEGACY-: " + hwid);
		assertFalse(hwid.startsWith("FERMATA-"));
		
		final byte[] key = client.enableCrypt();
		assertEquals(16, key.length);
	}
	
	@Test
	public void testLegacyGuardClientSKBoxEncryption()
	{
		ConfigProtection.ALLOW_GUARD_SYSTEM = true;
		final GameClient client = new GameClient(null);
		
		client.setHasLegacyGuard(true);
		assertTrue(client.hasLegacyGuard());
		
		final byte[] key = client.enableCrypt();
		assertNotNull(key);
		assertEquals(16, key.length);
		
		client.setAccountName("guard_player");
		client.setHWID("11223344556677889900AABBCCDDEEFF");
		assertEquals("11223344556677889900AABBCCDDEEFF", client.getHWID());
	}
	
	@Test
	public void testFermataClientBlowfishAndCfpIsolation()
	{
		ConfigProtection.ALLOW_GUARD_SYSTEM = true;
		final GameClient client = new GameClient(null);
		
		assertFalse(client.hasLegacyGuard());
		
		final byte[] challenge = new byte[16];
		Arrays.fill(challenge, (byte) 0x5A);
		client.setCfpChallenge(challenge, System.currentTimeMillis());
		
		assertTrue(client.isFermataClient());
		assertTrue(client.hasPendingCfpChallenge());
		assertFalse(client.hasLegacyGuard());
		
		final byte[] identifier = new byte[32];
		Arrays.fill(identifier, (byte) 0x99);
		final ClientFingerprintIdentity identity = new ClientFingerprintIdentity(
			1, ClientFingerprintIdentity.SOURCE_LOCAL_HARDWARE, 1, 2, identifier, challenge, true
		);
		client.completeCfpExchange(identity);
		
		assertFalse(client.hasPendingCfpChallenge());
		assertTrue(client.isCfpExchangeCompleted());
		assertEquals(identity.toHexIdentifier(), client.getHWID());
		assertTrue(client.getHWID().length() == 64);
		
		final byte[] key = client.enableCrypt();
		assertEquals(16, key.length);
	}
	
	@Test
	public void testSendProtocolVersion_fermataOrVanillaPacket_marksLegacyGuardFalse()
	{
		ConfigProtection.ALLOW_GUARD_SYSTEM = true;
		final GameClient client = new GameClient(null);
		
		// 4 bytes protocolVersion (746) + exatamente 260 bytes do cert/buffer NCSoft padrão
		final java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(264).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		buf.putInt(746);
		buf.put(new byte[260]);
		buf.flip();
		
		final ext.mods.gameserver.network.clientpackets.SendProtocolVersion packet = new ext.mods.gameserver.network.clientpackets.SendProtocolVersion();
		final boolean readOk = packet.readPacket(client, buf);
		assertTrue(readOk);
		
		// Cliente Fermata ou Vanilla envia exatamente 260 bytes restantes -> legacyGuard DEVE ser FALSE
		assertFalse(client.hasLegacyGuard(), "Cliente Fermata (260 bytes restantes) NUNCA deve ser marcado como legacyGuard!");
	}
	
	@Test
	public void testSendProtocolVersion_legacyGuardPacket_marksLegacyGuardTrue()
	{
		ConfigProtection.ALLOW_GUARD_SYSTEM = true;
		final GameClient client = new GameClient(null);
		
		// 4 bytes protocolVersion (746) + 260 bytes data + strings de HWID da guard russa (> 260 bytes restantes)
		final java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(512).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		buf.putInt(746);
		buf.put(new byte[260]);
		// String UTF-16LE terminada em \0\0
		for (char c : "HWID-HD-123".toCharArray()) buf.putChar(c);
		buf.putChar('\0');
		for (char c : "HWID-MAC-456".toCharArray()) buf.putChar(c);
		buf.putChar('\0');
		for (char c : "HWID-CPU-789".toCharArray()) buf.putChar(c);
		buf.putChar('\0');
		buf.flip();
		
		final ext.mods.gameserver.network.clientpackets.SendProtocolVersion packet = new ext.mods.gameserver.network.clientpackets.SendProtocolVersion();
		final boolean readOk = packet.readPacket(client, buf);
		assertTrue(readOk);
		
		// Cliente com Guard russa envia > 260 bytes restantes -> legacyGuard DEVE ser TRUE
		assertTrue(client.hasLegacyGuard(), "Cliente com Guard russa (> 260 bytes) DEVE ser marcado como legacyGuard!");
	}
}
