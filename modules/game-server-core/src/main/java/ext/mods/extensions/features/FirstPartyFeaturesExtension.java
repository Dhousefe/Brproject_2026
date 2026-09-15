package ext.mods.extensions.features;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.Config;
import ext.mods.gameserver.communitybbs.custom.AuctionBBSManager;
import ext.mods.gameserver.custom.data.AuctionCurrencies;
import ext.mods.gameserver.custom.data.BalanceData;
import ext.mods.gameserver.custom.data.BossHpAnnounceData;
import ext.mods.gameserver.custom.data.DonateData;
import ext.mods.gameserver.custom.data.EnchantData;
import ext.mods.gameserver.custom.data.EquipGradeRestrictionData;
import ext.mods.gameserver.custom.data.EventsData;
import ext.mods.gameserver.custom.data.MissionData;
import ext.mods.gameserver.custom.data.PolymorphData;
import ext.mods.gameserver.custom.data.PvPData;
import ext.mods.gameserver.custom.data.RaidDropAnnounceData;
import ext.mods.gameserver.custom.data.RatesData;
import ext.mods.gameserver.data.manager.AntiFeedManager;
import ext.mods.gameserver.data.manager.CoupleManager;
import ext.mods.gameserver.data.manager.EventsDropManager;
import ext.mods.gameserver.data.manager.PcCafeManager;
import ext.mods.gameserver.data.manager.SellBuffsManager;
import ext.mods.gameserver.data.sql.OfflineTradersTable;
import ext.mods.gameserver.data.xml.StaticSpawnData;
import ext.mods.gameserver.handler.voicedcommandhandlers.Epic;
import ext.mods.gameserver.handler.voicedcommandhandlers.Raid;
import ext.mods.gameserver.listener.AgathionTeleportListener;
import ext.mods.gameserver.model.entity.autofarm.AutoFarmManager;
import ext.mods.gameserver.model.entity.autofarm.AutoFarmTask;
import ext.mods.gameserver.model.entity.events.capturetheflag.CTFManager;
import ext.mods.gameserver.model.entity.events.deathmatch.DMManager;
import ext.mods.gameserver.model.entity.events.lastman.LMManager;
import ext.mods.gameserver.model.entity.events.teamvsteam.TvTManager;
import ext.mods.extensions.listener.manager.PlayerListenerManager;
import ext.mods.gameserver.model.actor.player.PlayerAttachments;
import ext.mods.protection.hwid.hwid;
import ext.mods.quests.QuestData;
import ext.mods.config.ConfigOfflineShop;
import ext.mods.config.ConfigProject;
import ext.mods.config.ConfigProtection;

/**
 * Phase 3: platform / always-on first-party systems (not extractable packages).
 * Feature packages under {@code modules/mods/*} load via their own SPI extensions.
 */
public final class FirstPartyFeaturesExtension implements Extension
{
	public static final String ID = "first-party-features";
	
	@Override
	public String id()
	{
		return ID;
	}
	
	@Override
	public String version()
	{
		return "4.0.0";
	}
	
	@Override
	public void onEnable(ExtensionContext context)
	{
		context.info("FirstPartyFeatures: platform systems…");
		
		BalanceData.getInstance().init();
		RatesData.getInstance();
		if ((ConfigOfflineShop.OFFLINE_TRADE_ENABLE || ConfigOfflineShop.OFFLINE_CRAFT_ENABLE) && ConfigOfflineShop.RESTORE_OFFLINERS)
			OfflineTradersTable.getInstance().restore();
		
		CoupleManager.getInstance();
		EventsData.getInstance();
		EventsDropManager.getInstance();
		CTFManager.getInstance();
		DMManager.getInstance();
		LMManager.getInstance();
		TvTManager.getInstance();
		EnchantData.getInstance();
		AntiFeedManager.getInstance().registerEvent(AntiFeedManager.GAME_ID);
		PcCafeManager.getInstance();
		
		StaticSpawnData.getInstance();
		Raid.load();
		Epic.load();
		AuctionBBSManager.getInstance().load();
		AuctionCurrencies.getInstance();
		AutoFarmManager.getInstance();
		AutoFarmTask.getInstance();
		
		if (ConfigProject.SELLBUFF_ENABLED)
			SellBuffsManager.getInstance();
		
		DonateData.getInstance();
		MissionData.getInstance();
		PvPData.getInstance();
		PolymorphData.getInstance();
		RaidDropAnnounceData.getInstance();
		EquipGradeRestrictionData.getInstance();
		BossHpAnnounceData.getInstance();
		
		if (ConfigProtection.ALLOW_GUARD_SYSTEM)
			hwid.Init();
		else
			context.info("Hwid Manager is disabled.");
		
		QuestData.getInstance();
		
		try
		{
			PlayerListenerManager.getInstance().registerTeleportListener(new AgathionTeleportListener());
		}
		catch (Exception e)
		{
			context.warn("AgathionTeleportListener failed: " + e.getMessage());
		}
		
		// Phase 4: clear PlayerAttachments on logout / character delete (ADR 0002).
		try
		{
			PlayerAttachments.install();
		}
		catch (Exception e)
		{
			context.warn("PlayerAttachments.install failed: " + e.getMessage());
		}
		
		context.info("FirstPartyFeatures: platform enabled (feature mods load via SPI).");
	}
}
