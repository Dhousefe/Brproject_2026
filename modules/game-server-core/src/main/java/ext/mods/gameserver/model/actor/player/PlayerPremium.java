package ext.mods.gameserver.model.actor.player;

import java.sql.SQLException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ext.mods.config.ConfigProject;
import ext.mods.gameserver.data.repository.PremiumStore;
import ext.mods.gameserver.data.service.PremiumPersistenceService;
import ext.mods.gameserver.model.actor.Player;

/**
 * account_premium JDBC for a {@link Player} (att-ver-3.0 onda 3).
 */
public final class PlayerPremium
{
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerPremium.class);
	
	private final Player _owner;
	
	public PlayerPremium(Player owner)
	{
		_owner = owner;
	}
	
	public void createPSdb()
	{
		if (!ConfigProject.USE_PREMIUM_SERVICE)
			return;
		
		try
		{
			PremiumPersistenceService.upsert(_owner.getAccountNamePlayer(), 0, 0);
		}
		catch (Exception e)
		{
			LOGGER.warn("PremiumService: Could not insert char data: {}", e.toString());
		}
	}
	
	public static void psTimeOver(String account)
	{
		if (!ConfigProject.USE_PREMIUM_SERVICE)
			return;
		
		try
		{
			PremiumPersistenceService.expire(account);
		}
		catch (SQLException e)
		{
			LOGGER.warn("PremiumService: Could not increase data: {}", e.toString());
		}
	}
	
	public long getPremServiceData()
	{
		if (!ConfigProject.USE_PREMIUM_SERVICE)
			return 0;
		
		try
		{
			return PremiumPersistenceService.find(_owner.getAccountName()).map(PremiumStore.PremiumRecord::endDate).orElse(0L);
		}
		catch (SQLException e)
		{
			LOGGER.warn("PremiumService: Could not restore prem service data: {}", e.toString());
		}
		
		return 0;
	}
	
	public void restorePremServiceData(Player player, String account)
	{
		if (!ConfigProject.USE_PREMIUM_SERVICE)
		{
			player.getPremium().createPSdb();
			player.setPremiumService(0);
			return;
		}
		
		try
		{
			final Optional<PremiumStore.PremiumRecord> data = PremiumPersistenceService.find(account);
			if (data.isPresent())
			{
				if (data.get().endDate() <= System.currentTimeMillis())
				{
					psTimeOver(account);
					player.setPremiumService(0);

					player.getMemos().unset("name_color");
					player.getMemos().unset("title_color");

					player.getAppearance().setNameColor(0xFFFFFF);
					player.getAppearance().setTitleColor(0xFFFF77);
					player.broadcastUserInfo();
				}
				else
					player.setPremiumService(data.get().premiumService());
			}
			else
			{
				player.getPremium().createPSdb();
				player.setPremiumService(0);
				player.getMemos().unset("name_color");
				player.getMemos().unset("title_color");

				player.getAppearance().setNameColor(0xFFFFFF);
				player.getAppearance().setTitleColor(0xFFFF77);
				player.broadcastUserInfo();
			}
		}
		catch (SQLException e)
		{
			LOGGER.warn("PremiumService: Could not restore data for: {}", account, e);
		}
	}
}
