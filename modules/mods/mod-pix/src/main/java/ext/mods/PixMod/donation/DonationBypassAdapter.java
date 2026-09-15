/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */
package ext.mods.PixMod.donation;

import ext.mods.Config;
import ext.mods.gameserver.model.actor.Player;

import ext.mods.PixMod.donationmanager.DonationManager;
import ext.mods.config.ConfigDonation;

/**
 * Liga bypasses ao {@link DonationManager} e as pastas {@code html/mods/donation/pt|en/}.
 * <ul>
 * <li>{@code bypass pix ...} → HTML em {@code pt/} (comando {@code .pix})</li>
 * <li>{@code bypass pay ...} → HTML em {@code en/} (comando {@code .pay})</li>
 * <li>{@code bypass donation ...} → mod oficial; idioma padrao {@code pt}</li>
 * </ul>
 */
public final class DonationBypassAdapter
{
	private DonationBypassAdapter()
	{
	}
	
	/**
	 * Tenta tratar bypass PIX / donation antes dos demais handlers.
	 * @param player jogador que clicou no HTML
	 * @param bypass texto do bypass (com ou sem {@code -h })
	 * @return true se o bypass foi consumido
	 */
	public static boolean tryHandle(Player player, String bypass)
	{
		if (!ConfigDonation.ENABLE_PIX_MOD || !ConfigDonation.DONATION_ENABLED || player == null || bypass == null)
			return false;
		
		String b = bypass.trim();
		if (b.startsWith("-h "))
			b = b.substring(3).trim();
		
		if (b.startsWith("pix"))
		{
			player.getMemos().set(DonationManager.MEMO_DONATION_HTML_LOCALE, "pt");
			DonationManager.getInstance().handleBypass(player, normalizeLegacyTail(extractTail(b, "pix")));
			return true;
		}
		
		if (b.startsWith("pay"))
		{
			player.getMemos().set(DonationManager.MEMO_DONATION_HTML_LOCALE, "en");
			DonationManager.getInstance().handleBypass(player, normalizeLegacyTail(extractTail(b, "pay")));
			return true;
		}
		
		if (b.startsWith("donation"))
		{
			if (player.getMemos().get(DonationManager.MEMO_DONATION_HTML_LOCALE) == null)
				player.getMemos().set(DonationManager.MEMO_DONATION_HTML_LOCALE, "pt");
			
			DonationManager.getInstance().handleBypass(player, normalizeDonationTail(extractTail(b, "donation")));
			return true;
		}
		
		return false;
	}
	
	private static String extractTail(String full, String prefix)
	{
		if (full.length() == prefix.length())
			return "";
		if (full.length() > prefix.length() && full.charAt(prefix.length()) == ' ')
			return full.substring(prefix.length() + 1).trim();
		return full.substring(prefix.length()).trim();
	}
	
	private static String normalizeDonationTail(String tail)
	{
		return (tail == null || tail.isEmpty()) ? "index" : tail;
	}
	
	/** HTML legado usa {@code htm index.htm}; o mod novo usa {@code index}. */
	private static String normalizeLegacyTail(String tail)
	{
		if (tail == null || tail.isEmpty())
			return "index";
		
		final String t = tail.trim();
		if ("htm index.htm".equals(t))
			return "index";
		if (t.startsWith("htm ") && t.length() > 4)
			return t.substring(4).trim();
		return tail;
	}
}
