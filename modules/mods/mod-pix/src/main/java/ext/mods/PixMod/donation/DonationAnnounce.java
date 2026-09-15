/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */
package ext.mods.PixMod.donation;

import ext.mods.Config;
import ext.mods.gameserver.enums.SayType;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.network.serverpackets.CreatureSay;
import ext.mods.config.ConfigDonation;

/**
 * Anuncio global quando o jogador recebe item via DonationManager / PIX.
 */
public final class DonationAnnounce
{
	private DonationAnnounce()
	{
	}
	
	/**
	 * Anuncia no servidor que o jogador concluiu uma doacao PIX.
	 * @param playerName nome do personagem
	 */
	public static void announceCompleted(String playerName)
	{
		if (!ConfigDonation.ANNOUNCE_DONATOR_ITEM_GLOBAL || playerName == null || playerName.isEmpty())
			return;
		
		final CreatureSay cs = new CreatureSay(SayType.ANNOUNCEMENT, "PIX", playerName + " concluiu uma doacao via PIX.");
		World.getInstance().getPlayers().forEach(p -> p.sendPacket(cs));
	}
}
