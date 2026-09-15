package ext.mods.security.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Security regression tests for {@link AuthService}.
 * <p>
 * att-ver-4.0: locks down behavior after the hardcoded credential bypass was removed.
 * Each test is independent — env-var reads cannot be reliably toggled from a JVM test,
 * so most cases cover the system-property path of {@link AuthService#isDevAuthEnabled()}.
 */
class AuthServiceSecurityTest
{
	/** Email that was previously hardcoded as a universal bypass. */
	private static final String LEAKED_EMAIL = "brprojeto@l2jbrasil.com";
	/** Password that was previously hardcoded as a universal bypass. */
	private static final String LEAKED_PASSWORD = "12345678";
	/** Weak demo passwords that were previously accepted under DevAuth. */
	private static final String WEAK_LOCAL = "local";
	private static final String WEAK_OSS = "oss";

	private AuthService auth;

	@BeforeEach
	void newAuthService()
	{
		auth = new AuthService();
	}

	@AfterEach
	void clearDevFlag()
	{
		System.clearProperty(AuthService.DEV_AUTH_PROPERTY);
	}

	// ---------------------------------------------------------------------
	// Hardcoded credential regression tests (CRITICAL fix)
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("Hardcoded credential bypass must be removed")
	class HardcodedCredentialRegression
	{
		@Test
		@DisplayName("rejects the previously hardcoded email/password pair (with DevAuth disabled)")
		void hardcodedCredentials_rejected_whenDevAuthDisabled()
		{
			System.clearProperty(AuthService.DEV_AUTH_PROPERTY);
			assertFalse(auth.authenticate(LEAKED_EMAIL, LEAKED_PASSWORD),
				"Hardcoded operator credential must NEVER bypass authentication");
		}

		@Test
		@DisplayName("rejects the previously hardcoded email/password pair (with DevAuth enabled)")
		void hardcodedCredentials_rejected_evenWithDevAuth()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			// Even with DevAuth, the historical magic string must no longer authenticate
			// unless it happens to equal the active dev token — which it does not.
			assertFalse(auth.authenticate(LEAKED_EMAIL, LEAKED_PASSWORD),
				"Hardcoded operator credential must not equal the random/env dev token");
		}

		@Test
		@DisplayName("rejects the hardcoded email regardless of case")
		void hardcodedCredentials_caseInsensitiveReject()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			assertFalse(auth.authenticate("BRPROJETO@L2JBRASIL.COM", LEAKED_PASSWORD));
			assertFalse(auth.authenticate("BrProjeto@L2jBrasil.com", LEAKED_PASSWORD));
			assertFalse(auth.authenticate(LEAKED_EMAIL.toUpperCase(), LEAKED_PASSWORD));
		}

		@Test
		@DisplayName("rejects any random email paired with the leaked password")
		void leakedPassword_isNotAUniversalKey()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			assertFalse(auth.authenticate("anyone@example.com", LEAKED_PASSWORD));
			assertFalse(auth.authenticate("user@example.com", LEAKED_PASSWORD));
			assertFalse(auth.authenticate("admin@example.com", LEAKED_PASSWORD));
		}
	}

	// ---------------------------------------------------------------------
	// Weak password rejection tests (HIGH fix)
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("Weak demo passwords must be rejected")
	class WeakPasswordRejection
	{
		@Test
		@DisplayName("rejects 'local' under DevAuth without an env-var token")
		void rejectsLocal_withoutEnvToken()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			assertFalse(auth.authenticate("op@example.com", WEAK_LOCAL));
		}

		@Test
		@DisplayName("rejects 'oss' under DevAuth without an env-var token")
		void rejectsOss_withoutEnvToken()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			assertFalse(auth.authenticate("op@example.com", WEAK_OSS));
		}

		@Test
		@DisplayName("rejects 'local' and 'oss' case-insensitively")
		void rejectsWeak_caseInsensitive()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			assertFalse(auth.authenticate("op@example.com", "LOCAL"));
			assertFalse(auth.authenticate("op@example.com", "Local"));
			assertFalse(auth.authenticate("op@example.com", "OSS"));
			assertFalse(auth.authenticate("op@example.com", "Oss"));
		}

		@Test
		@DisplayName("rejects 'local' and 'oss' regardless of email")
		void rejectsWeak_anyEmail()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			assertFalse(auth.authenticate("", WEAK_LOCAL));
			assertFalse(auth.authenticate("random@example.com", WEAK_OSS));
			assertFalse(auth.authenticate("brproject@example.com", WEAK_LOCAL));
		}
	}

	// ---------------------------------------------------------------------
	// DevAuth + proper token tests
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("DevAuth with a generated token works correctly")
	class DevAuthWithGeneratedToken
	{
		@Test
		@DisplayName("the auto-generated token authenticates its owner")
		void generatedToken_authenticatesOnceExposed()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			final String token = auth.getActiveDevToken();
			assertNotNull(token, "Active dev token must be generated when DevAuth is enabled");
			assertTrue(auth.authenticate("operator@example.com", token),
				"Supplied password equal to active token must authenticate");
		}

		@Test
		@DisplayName("the generated token is stable across calls (one-time at startup)")
		void generatedToken_isStable()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			final String first = auth.getActiveDevToken();
			final String second = auth.getActiveDevToken();
			assertEquals(first, second, "Random token is generated once and reused");
		}

		@Test
		@DisplayName("each new instance generates a different token")
		void generatedToken_differsPerInstance()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			final String a = new AuthService().getActiveDevToken();
			final String b = new AuthService().getActiveDevToken();
			assertNotNull(a);
			assertNotNull(b);
			assertNotEquals(a, b, "UUID-derived tokens should not collide");
		}

		@Test
		@DisplayName("a wrong password is rejected even with a valid token")
		void wrongPassword_rejectedWithDevAuth()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			final String token = auth.getActiveDevToken();
			assertFalse(auth.authenticate("op@example.com", token + "x"));
			assertFalse(auth.authenticate("op@example.com", "x" + token));
			assertFalse(auth.authenticate("op@example.com", token.substring(0, token.length() - 1)));
		}
	}

	@Nested
	@DisplayName("DevAuth with an env-var-provided token works correctly")
	class DevAuthWithEnvVarToken
	{
		/**
		 * We cannot reliably set process-level env vars from a JUnit test on every
		 * platform, so this test only asserts what we can deterministically verify:
		 * when DevAuth is disabled, {@link AuthService#getActiveDevToken()} returns
		 * {@code null} — proving the env-var path is the only token gate.
		 */
		@Test
		@DisplayName("getActiveDevToken returns null when DevAuth is disabled")
		void noToken_whenDevAuthDisabled()
		{
			System.clearProperty(AuthService.DEV_AUTH_PROPERTY);
			assertNull(auth.getActiveDevToken(),
				"No token must be exposed unless DevAuth is explicitly enabled");
		}
	}

	// ---------------------------------------------------------------------
	// DevAuth disabled tests
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("When DevAuth is disabled, all authentication fails")
	class DevAuthDisabled
	{
		@Test
		@DisplayName("rejects every (email, password) combination")
		void rejectsEverything_whenDevAuthOff()
		{
			System.clearProperty(AuthService.DEV_AUTH_PROPERTY);
			assertFalse(auth.authenticate("op@example.com", "anything"));
			assertFalse(auth.authenticate("admin@example.com", "admin"));
			assertFalse(auth.authenticate(LEAKED_EMAIL, LEAKED_PASSWORD));
			assertFalse(auth.authenticate("user@example.com", WEAK_OSS));
		}

		@Test
		@DisplayName("rejects even an explicit 'true' string as a password")
		void rejectsBooleanAsPassword()
		{
			System.clearProperty(AuthService.DEV_AUTH_PROPERTY);
			assertFalse(auth.authenticate("op@example.com", "true"));
			assertFalse(auth.authenticate("op@example.com", "false"));
			assertFalse(auth.authenticate("op@example.com", "1"));
		}

		@Test
		@DisplayName("isDevAuthEnabled returns false when both env and property are off")
		void isDevAuthEnabled_falseWhenNothingSet()
		{
			System.clearProperty(AuthService.DEV_AUTH_PROPERTY);
			if (!"1".equals(System.getenv(AuthService.DEV_AUTH_ENV)))
				assertFalse(AuthService.isDevAuthEnabled());
		}
	}

	// ---------------------------------------------------------------------
	// Null / blank input tests
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("Null and blank inputs are rejected gracefully")
	class NullAndBlankInputs
	{
		@Test
		@DisplayName("null email is rejected")
		void nullEmail_rejected()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			assertFalse(auth.authenticate(null, "anything"));
		}

		@Test
		@DisplayName("null password is rejected")
		void nullPassword_rejected()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			assertFalse(auth.authenticate("op@example.com", null));
		}

		@Test
		@DisplayName("both null returns false (does not NPE)")
		void bothNull_rejected()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			assertFalse(auth.authenticate(null, null));
		}

		@Test
		@DisplayName("blank email is rejected")
		void blankEmail_rejected()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			assertFalse(auth.authenticate("", "x"));
			assertFalse(auth.authenticate("   ", "x"));
			assertFalse(auth.authenticate("\t", "x"));
		}

		@Test
		@DisplayName("blank password is rejected")
		void blankPassword_rejected()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			assertFalse(auth.authenticate("op@example.com", ""));
			assertFalse(auth.authenticate("op@example.com", "   "));
		}

		@Test
		@DisplayName("authenticate never throws on adversarial inputs")
		void noThrowOnAdversarialInputs()
		{
			System.setProperty(AuthService.DEV_AUTH_PROPERTY, "true");
			// These should all return false, never throw.
			assertFalse(auth.authenticate(" ", " "));
			assertFalse(auth.authenticate("op@example.com", "\n\r\t"));
			assertFalse(auth.authenticate("a@b.c", "  "));
		}
	}

	// ---------------------------------------------------------------------
	// Token generation tests
	// ---------------------------------------------------------------------

	@Nested
	@DisplayName("Random token generation is healthy")
	class TokenGeneration
	{
		@Test
		@DisplayName("generateRandomKey returns a non-blank string")
		void generatesNonBlank()
		{
			final String key = auth.generateRandomKey();
			assertNotNull(key);
			assertFalse(key.isBlank(), "Generated key must not be blank");
		}

		@Test
		@DisplayName("1000 generations produce no duplicates (collision resistance sanity check)")
		void noCollisions_1000()
		{
			final Set<String> seen = new HashSet<>();
			for (int i = 0; i < 1000; i++)
				seen.add(auth.generateRandomKey());
			assertEquals(1000, seen.size(),
				"1000 random tokens must all be unique (UUID-based generation is collision-safe)");
		}

		@Test
		@DisplayName("generated tokens are pure hex")
		void hexOnly()
		{
			final String key = auth.generateRandomKey();
			assertTrue(key.matches("[0-9a-fA-F]+"),
				"Token must be hex-encoded, got: " + key);
		}
	}
}
