/*
 * BrProject — Phase 6 OSS launcher auth (local operator only).
 */
package ext.mods.security.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import ext.mods.security.gui.LauncherApp;

/**
 * Optional Swing launcher credentials — not game LoginServer auth.
 * <p>
 * att-ver-4.0: removed hardcoded operator credential bypass; DevAuth now requires
 * an explicit env-var token ({@code BRPROJECT_DEV_TOKEN}) instead of weak passwords.
 * <ul>
 *   <li>If {@code BRPROJECT_DEV_AUTH=1} (or {@code -Dbrproject.devAuth=true}) AND
 *       {@code BRPROJECT_DEV_TOKEN} is set, authenticate matches when the supplied
 *       password equals the token (constant-time comparison).</li>
 *   <li>If DevAuth is enabled but {@code BRPROJECT_DEV_TOKEN} is unset, a random
 *       one-time token is generated on first call and printed to the console. The
 *       caller must read it back via {@link #getActiveDevToken()} or print it.</li>
 *   <li>Otherwise authentication always returns false — no universal bypass.</li>
 * </ul>
 */
public class AuthService
{
	/** System property / env-var name that gates the dev token path. */
	public static final String DEV_AUTH_PROPERTY = "brproject.devAuth";
	public static final String DEV_AUTH_ENV = "BRPROJECT_DEV_AUTH";
	public static final String DEV_TOKEN_ENV = "BRPROJECT_DEV_TOKEN";

	/** Length of the random dev token generated when no env-var is provided. */
	private static final int RANDOM_TOKEN_LENGTH = 32;

	private List<Map<String, Object>> allLicenses = List.of();

	/**
	 * Cached one-time dev token, lazily initialized the first time DevAuth is used
	 * without an env-var-provided token. {@code null} until first use.
	 */
	private final AtomicReference<String> activeDevToken = new AtomicReference<>();

	private String getKey()
	{
		return LauncherApp.getKey();
	}

	/** True when operator explicitly enables local demo launcher auth. */
	public static boolean isDevAuthEnabled()
	{
		if ("1".equals(System.getenv(DEV_AUTH_ENV)))
			return true;
		return Boolean.parseBoolean(System.getProperty(DEV_AUTH_PROPERTY, "false"));
	}

	/**
	 * Returns the env-var-provided dev token if present, otherwise the lazily
	 * generated random token. Returns {@code null} when DevAuth is disabled.
	 * <p>
	 * The first call that needs the token will lazily generate it and log it to
	 * the console so the operator can read it from the launch output.
	 */
	public String getActiveDevToken()
	{
		if (!isDevAuthEnabled())
			return null;
		final String envToken = System.getenv(DEV_TOKEN_ENV);
		if (envToken != null && !envToken.isBlank())
			return envToken;
		return activeDevToken.updateAndGet(prev -> prev != null ? prev : generateAndAnnounceToken());
	}

	/**
	 * Phase 6 OSS launcher login (not game-account auth).
	 * <p>
	 * Authentication is granted only when ALL of the following hold:
	 * <ol>
	 *   <li>Email and password are non-null and non-blank.</li>
	 *   <li>DevAuth is enabled via env-var or system property.</li>
	 *   <li>The supplied password equals the active dev token (env-var value or
	 *       the lazily generated one-time token) using constant-time comparison.</li>
	 * </ol>
	 * The previous hardcoded {@code brprojeto@l2jbrasil.com / 12345678} bypass
	 * and weak {@code local}/{@code oss} passwords have been removed.
	 */
	public boolean authenticate(String email, String senha)
	{
		if (email == null || senha == null || email.isBlank() || senha.isBlank())
			return false;
		if (!isDevAuthEnabled())
			return false;
		final String expected = getActiveDevToken();
		if (expected == null || expected.isEmpty())
			return false;
		return constantTimeEquals(expected, senha);
	}

	/**
	 * Constant-time string comparison so that timing differences do not leak
	 * the length / prefix of the dev token.
	 */
	private static boolean constantTimeEquals(String a, String b)
	{
		if (a == null || b == null)
			return false;
		final byte[] aa = a.getBytes();
		final byte[] bb = b.getBytes();
		if (aa.length != bb.length)
			return false;
		int diff = 0;
		for (int i = 0; i < aa.length; i++)
			diff |= aa[i] ^ bb[i];
		return diff == 0;
	}

	/** Generate a random hex token and print it to console (one-time at startup). */
	private String generateAndAnnounceToken()
	{
		final String token = generateRandomKey();
		// Use both streams — the launcher console may not be System.out depending
		// on how the user ran the app.
		System.out.println("[AuthService] BRPROJECT_DEV_TOKEN (random, one-time) = " + token);
		System.err.println("[AuthService] BRPROJECT_DEV_TOKEN (random, one-time) = " + token);
		return token;
	}

	public String generateRandomKey()
	{
		final String raw = UUID.randomUUID().toString().replace("-", "")
			+ UUID.randomUUID().toString().replace("-", "");
		return raw.substring(0, RANDOM_TOKEN_LENGTH);
	}

	public String getPublicIP()
	{
		return "127.0.0.1";
	}

	/** Cosmetic license list for dashboard UI (not enforced). */
	public void loadLicenses(String email)
	{
		if (email == null || email.isBlank())
		{
			allLicenses = List.of();
			return;
		}
		final Map<String, Object> license = new LinkedHashMap<>();
		license.put("license_key", getKey());
		license.put("expires_at", "OSS-open");
		license.put("server_ip", getPublicIP());
		license.put("active", true);
		allLicenses = List.of(Collections.unmodifiableMap(license));
	}

	public List<Map<String, Object>> getAllLicenses()
	{
		return allLicenses == null ? List.of() : allLicenses;
	}
}
