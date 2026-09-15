package ext.mods.security.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest
{
	private final AuthService auth = new AuthService();

	@AfterEach
	void clearDevFlag()
	{
		System.clearProperty("brproject.devAuth");
	}

	@Test
	void withoutDevAuth_rejected()
	{
		System.clearProperty("brproject.devAuth");
		// Without DevAuth, every (email, password) must be rejected.
		assertFalse(auth.authenticate("op@example.com", "local"));
		assertFalse(auth.authenticate("someone@example.com", "12345678"));
		assertFalse(auth.authenticate("op@example.com", "oss"));
	}

	@Test
	void hardcodedOperatorCredential_isRejected()
	{
		// att-ver-4.0: the legacy bypass brprojeto@l2jbrasil.com / 12345678 must never
		// authenticate again, even when DevAuth is enabled (the random / env-var token
		// will not equal "12345678").
		System.setProperty("brproject.devAuth", "true");
		assertFalse(auth.authenticate("brprojeto@l2jbrasil.com", "12345678"));
	}

	@Test
	void hardcodedOperatorCredential_caseInsensitiveEmail_isRejected()
	{
		System.setProperty("brproject.devAuth", "true");
		assertFalse(auth.authenticate("BRPROJETO@L2JBRASIL.COM", "12345678"));
		assertFalse(auth.authenticate("BrProjeto@L2jBrasil.com", "12345678"));
	}

	@Test
	void wrongPassword_rejectedEvenWithDevAuth()
	{
		System.setProperty("brproject.devAuth", "true");
		final String token = auth.getActiveDevToken();
		assertNotNull(token);
		assertFalse(auth.authenticate("operator@example.com", "wrong-password"));
		assertFalse(auth.authenticate("operator@example.com", token + "x"));
	}

	@Test
	void devAuthToken_authenticates()
	{
		// When DevAuth is enabled, the active token (env-var value or generated
		// random) is the only valid password.
		System.setProperty("brproject.devAuth", "true");
		final String token = auth.getActiveDevToken();
		assertNotNull(token);
		assertTrue(auth.authenticate("operator@example.com", token));
	}

	@Test
	void isDevAuthEnabled_trueWhenPropertySet_falseWhenCleared()
	{
		// Env BRPROJECT_DEV_AUTH cannot be set reliably from unit tests (process-level);
		// property path covers the configurable toggle used in CI / launcher.
		System.clearProperty("brproject.devAuth");
		System.setProperty("brproject.devAuth", "true");
		assertTrue(AuthService.isDevAuthEnabled());

		System.setProperty("brproject.devAuth", "false");
		// When env is unset, false property means disabled; if env is "1", remains enabled.
		if (!"1".equals(System.getenv("BRPROJECT_DEV_AUTH")))
			assertFalse(AuthService.isDevAuthEnabled());

		System.clearProperty("brproject.devAuth");
		if (!"1".equals(System.getenv("BRPROJECT_DEV_AUTH")))
			assertFalse(AuthService.isDevAuthEnabled());
	}

	@Test
	void weakPasswords_rejectedEvenWithDevAuth()
	{
		// att-ver-4.0: 'local' and 'oss' are no longer accepted as universal DevAuth
		// passwords. Only the active dev token is valid.
		System.setProperty("brproject.devAuth", "true");
		assertFalse(auth.authenticate("op@example.com", "local"));
		assertFalse(auth.authenticate("op@example.com", "oss"));
	}

	@Test
	void blank_rejected()
	{
		System.setProperty("brproject.devAuth", "true");
		assertFalse(auth.authenticate("", "x"));
		assertFalse(auth.authenticate("x", ""));
		assertFalse(auth.authenticate(null, "local"));
		assertFalse(auth.authenticate(null, null));
	}
}
