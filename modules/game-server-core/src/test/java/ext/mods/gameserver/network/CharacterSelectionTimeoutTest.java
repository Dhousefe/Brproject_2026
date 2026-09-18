package ext.mods.gameserver.network;

import ext.mods.config.ConfigLogin;
import ext.mods.config.ConfigProtection;
import ext.mods.gameserver.network.GameClient.GameClientState;
import ext.mods.loginserver.LoginController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CharacterSelectionTimeoutTest
{
	private int _savedCharTimeout;
	private int _savedLoginTimeout;

	@BeforeEach
	public void setUp()
	{
		_savedCharTimeout = ConfigProtection.CHARACTER_SELECTION_TIMEOUT_SECONDS;
		_savedLoginTimeout = ConfigLogin.LOGIN_TIMEOUT_SECONDS;
	}

	@AfterEach
	public void tearDown()
	{
		ConfigProtection.CHARACTER_SELECTION_TIMEOUT_SECONDS = _savedCharTimeout;
		ConfigLogin.LOGIN_TIMEOUT_SECONDS = _savedLoginTimeout;
	}

	@Test
	public void testCharSelectTimeoutTaskScheduledOnAuthedState()
	{
		ConfigProtection.CHARACTER_SELECTION_TIMEOUT_SECONDS = 300;
		final GameClient client = new GameClient(null);

		assertEquals(GameClientState.CONNECTED, client.getState());
		assertNull(client.getCharSelectTimeoutTask());

		// Muda para AUTHED -> deve agendar o timeout task
		client.setState(GameClientState.AUTHED);
		assertEquals(GameClientState.AUTHED, client.getState());
		assertNotNull(client.getCharSelectTimeoutTask());

		// Muda para ENTERING -> deve cancelar o timeout task
		client.setState(GameClientState.ENTERING);
		assertEquals(GameClientState.ENTERING, client.getState());
		assertNull(client.getCharSelectTimeoutTask());
	}

	@Test
	public void testCharSelectTimeoutDisabledWhenZero()
	{
		ConfigProtection.CHARACTER_SELECTION_TIMEOUT_SECONDS = 0;
		final GameClient client = new GameClient(null);

		client.setState(GameClientState.AUTHED);
		assertEquals(GameClientState.AUTHED, client.getState());
		assertNull(client.getCharSelectTimeoutTask());
	}

	@Test
	public void testCharSelectTimeoutCancelledOnCloseNow()
	{
		ConfigProtection.CHARACTER_SELECTION_TIMEOUT_SECONDS = 300;
		final GameClient client = new GameClient(null);

		client.setState(GameClientState.AUTHED);
		assertNotNull(client.getCharSelectTimeoutTask());

		client.closeNow();
		assertNull(client.getCharSelectTimeoutTask());
	}

	@Test
	public void testLoginControllerTimeoutSynchronizedWithCharSelectTimeout()
	{
		ConfigLogin.LOGIN_TIMEOUT_SECONDS = 180;
		ConfigProtection.CHARACTER_SELECTION_TIMEOUT_SECONDS = 350;

		// getLoginTimeout deve escolher o maior valor (350s * 1000 = 350000 ms)
		assertEquals(350000, LoginController.getLoginTimeout());

		ConfigLogin.LOGIN_TIMEOUT_SECONDS = 400;
		ConfigProtection.CHARACTER_SELECTION_TIMEOUT_SECONDS = 200;

		// getLoginTimeout deve escolher o maior valor (400s * 1000 = 400000 ms)
		assertEquals(400000, LoginController.getLoginTimeout());
	}
}
