package ext.mods.gameserver.model.actor.player;

import java.util.ArrayList;
import java.util.List;

import ext.mods.gameserver.model.actor.Player;

/**
 * HTML bypass validation lists for a {@link Player}.
 */
public final class PlayerBypass
{
	private final Player _owner;
	private final List<String> _validBypass = new ArrayList<>();
	private final List<String> _validBypass2 = new ArrayList<>();
	
	public PlayerBypass(Player owner)
	{
		_owner = owner;
	}
	
	public synchronized void addBypass(String bypass)
	{
		if (bypass == null)
			return;
		
		_validBypass.add(bypass);
	}
	
	public synchronized void addBypass2(String bypass)
	{
		if (bypass == null)
			return;
		
		_validBypass2.add(bypass);
	}
	
	public synchronized boolean validateBypass(String cmd)
	{
		for (final String bp : _validBypass)
		{
			if (bp == null)
				continue;
			
			if (bp.equals(cmd))
				return true;
		}
		
		for (final String bp : _validBypass2)
		{
			if (bp == null)
				continue;
			
			if (cmd.startsWith(bp))
				return true;
		}
		
		return false;
	}
	
	public synchronized void clearBypass()
	{
		_validBypass.clear();
		_validBypass2.clear();
	}

	private String _lastBypassCommand = "";
	private long _lastBypassTime = 0L;
	private int _bypassCountInSecond = 0;
	private long _bypassSecondStartTick = 0L;

	/**
	 * By Dhousefe
	 * Verifica e atualiza o debounce para requisições de bypass com Mechanical Sympathy.
	 * 1. Teto global anti-DOS: Se ultrapassar maxPerSec dentro do mesmo segundo, retorna false.
	 * 2. Deduplicação: Se o comando for idêntico ao anterior dentro da janela windowMs, retorna false (drop silencioso).
	 * 3. Se for um comando novo ou fora da janela, atualiza o estado e retorna true.
	 * @param cmd O comando do bypass solicitado.
	 * @param windowMs Janela de debounce em milissegundos para comandos idênticos.
	 * @param maxPerSec Teto máximo de bypasses aceitos por segundo por jogador.
	 * @return true se o bypass deve ser processado, false se deve sofrer drop silencioso.
	 */
	public synchronized boolean checkAndSetBypassDebounce(String cmd, long windowMs, int maxPerSec)
	{
		final long now = System.currentTimeMillis();

		
		if (maxPerSec > 0)
		{
			if (now - _bypassSecondStartTick >= 1000L)
			{
				_bypassSecondStartTick = now;
				_bypassCountInSecond = 1;
			}
			else
			{
				_bypassCountInSecond++;
				if (_bypassCountInSecond > maxPerSec)
				{
					return false;
				}
			}
		}

		
		if (windowMs > 0 && cmd != null && cmd.equals(_lastBypassCommand))
		{
			if (now - _lastBypassTime < windowMs)
			{
				return false;
			}
		}

		_lastBypassCommand = (cmd != null) ? cmd : "";
		_lastBypassTime = now;
		return true;
	}
}
