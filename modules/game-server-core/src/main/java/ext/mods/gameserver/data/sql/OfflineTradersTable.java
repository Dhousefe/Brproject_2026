/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
* * L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
* * L2Brproject is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* General Public License for more details.
* * You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
* Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
* Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY, 
* SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
* as a contribution for the forum L2JBrasil.com
 */
package ext.mods.gameserver.data.sql;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import ext.mods.commons.logging.CLogger;

import ext.mods.gameserver.data.repository.OfflineTradeItem;
import ext.mods.gameserver.data.repository.OfflineTraderData;
import ext.mods.gameserver.data.service.OfflineTraderPersistenceService;
import ext.mods.gameserver.enums.ZoneId;
import ext.mods.gameserver.enums.actors.OperateType;
import ext.mods.gameserver.model.SellBuffHolder;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.records.ManufactureItem;
import ext.mods.gameserver.model.trade.TradeItem;
import ext.mods.gameserver.network.GameClient;
import ext.mods.config.ConfigOfflineShop;

public final class OfflineTradersTable
{
	private static final CLogger LOGGER = new CLogger(OfflineTradersTable.class.getName());
	
	public void store()
	{
		if (!ConfigOfflineShop.RESTORE_OFFLINERS || (!ConfigOfflineShop.OFFLINE_TRADE_ENABLE && !ConfigOfflineShop.OFFLINE_CRAFT_ENABLE))
			return;

		final List<OfflineTraderData> traders = new ArrayList<>();
		for (Player player : World.getInstance().getPlayers())
		{
			if (player.getOperateType() == OperateType.NONE || (player.getClient() != null && !player.getClient().isDetached()))
				continue;
			try
			{
				final OfflineTraderData trader = toData(player);
				if (trader != null)
					traders.add(trader);
			}
			catch (Exception e)
			{
				LOGGER.error("Error while preparing offline: " + player.getObjectId() + " ", e);
			}
		}

		try
		{
			OfflineTraderPersistenceService.replaceAll(traders);
			LOGGER.info("Offline stored.");
		}
		catch (Exception e)
		{
			LOGGER.error("Error while saving offline: ", e);
		}
	}
	
	public void saveOfflineTraders(Player player)
	{
		try
		{
			final OfflineTraderData trader = toData(player);
			OfflineTraderPersistenceService.replaceAll(trader == null ? List.of() : List.of(trader));
		}
		catch (Exception e)
		{
			LOGGER.error("error while saving offline traders.", e);
		}
	}

	private OfflineTraderData toData(Player player)
	{
		final List<OfflineTradeItem> items = new ArrayList<>();
		final int type = player.isSellingBuffs() ? OperateType.SELL_BUFFS.getId() : player.getOperateType().getId();
		final String title;

		switch (player.getOperateType())
		{
			case BUY:
				if (!ConfigOfflineShop.OFFLINE_TRADE_ENABLE)
					return null;
				title = player.getBuyList().getTitle();
				for (TradeItem item : player.getBuyList())
					items.add(new OfflineTradeItem(item.getItem().getItemId(), item.getQuantity(), item.getPrice(), item.getEnchant()));
				break;
			case SELL:
			case PACKAGE_SELL:
				if (!ConfigOfflineShop.OFFLINE_TRADE_ENABLE)
					return null;
				title = player.getSellList().getTitle();
				player.getSellList().updateItems(false);
				if (player.isSellingBuffs())
				{
					for (SellBuffHolder holder : player.getSellingBuffs())
						items.add(new OfflineTradeItem(holder.getSkillId(), holder.getSkillLvl(), holder.getPrice(), 0));
				}
				else
				{
					for (TradeItem item : player.getSellList())
						items.add(new OfflineTradeItem(item.getObjectId(), item.getQuantity(), item.getPrice(), item.getEnchant()));
				}
				break;
			case MANUFACTURE:
				if (!ConfigOfflineShop.OFFLINE_CRAFT_ENABLE)
					return null;
				title = player.getManufactureList().getStoreName();
				for (ManufactureItem item : player.getManufactureList())
					items.add(new OfflineTradeItem(item.recipeId(), 0, item.cost(), 0));
				break;
			default:
				return null;
		}

		return new OfflineTraderData(player.getObjectId(), player.getOfflineStartTime(), type, title, items);
	}
	
	public void restore()
	{
		if (!ConfigOfflineShop.RESTORE_OFFLINERS || (!ConfigOfflineShop.OFFLINE_TRADE_ENABLE && !ConfigOfflineShop.OFFLINE_CRAFT_ENABLE))
			return;

		int count = 0;
		try
		{
			for (OfflineTraderData trader : OfflineTraderPersistenceService.loadAll())
			{
				if (ConfigOfflineShop.OFFLINE_MAX_DAYS > 0 && isExpired(trader.time()))
					continue;

				final OperateType originalType = getType(trader.type());
				final boolean isSellBuff = originalType == OperateType.SELL_BUFFS;
				final OperateType type = isSellBuff ? OperateType.PACKAGE_SELL : originalType;
				if (type == null || type == OperateType.NONE)
					continue;

				final Player player = Player.restore(trader.characterId(), true);
				if (player == null)
					continue;

				try
				{
					final GameClient client = new GameClient(null);
					client.spawnOffline(player);
					player.setOfflineStartTime(trader.time());
					player.sitDown();
					if (isSellBuff)
						player.setSellingBuffs(true);

					switch (type)
					{
						case BUY:
							for (OfflineTradeItem item : trader.items())
								player.getBuyList().addItemByItemId(item.item(), (int) item.count(), (int) item.price(), (int) item.enchant());
							player.getBuyList().setTitle(trader.title());
							break;
						case SELL:
						case PACKAGE_SELL:
							if (player.isSellingBuffs())
							{
								for (OfflineTradeItem item : trader.items())
									player.getSellingBuffs().add(new SellBuffHolder(item.item(), (int) item.count(), (int) item.price()));
							}
							else
							{
								for (OfflineTradeItem item : trader.items())
									player.getSellList().addItem(item.item(), (int) item.count(), (int) item.price());
							}
							player.getSellList().setTitle(trader.title());
							player.getSellList().setPackaged(type == OperateType.PACKAGE_SELL);
							break;
						case MANUFACTURE:
							for (OfflineTradeItem item : trader.items())
								player.getManufactureList().add(new ManufactureItem(item.item(), (int) item.price()));
							player.getManufactureList().setStoreName(trader.title());
							break;
						default:
							break;
					}

					if (ConfigOfflineShop.OFFLINE_SLEEP_EFFECT)
					{
						player.startAbnormalEffect(Integer.decode("0x80"));
						player.broadcastUserInfo();
					}
					player.setOperateType(type);
					player.restoreEffects();
					player.broadcastUserInfo();
					player.broadcastTitleInfo();
					count++;
				}
				catch (Exception e)
				{
					LOGGER.warn("Error loading offline {}({}).", e, player.getName(), player.getObjectId());
					player.logout(true);
				}
			}

			LOGGER.info("Loaded " + count + " offline.");
			OfflineTraderPersistenceService.clear();
		}
		catch (Exception e)
		{
			LOGGER.warn("Error while loading offline: ", e);
		}
	}
	
	protected OperateType getType(int id)
	{
		for (final OperateType type : OperateType.values())
			if (type.getId() == id)
				return type;
			
		LOGGER.warn("Wrong OperateType id '{}' not found.", id);
		return null;
	}
	
	protected boolean isExpired(long time)
	{
		final Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(time);
		cal.add(Calendar.DAY_OF_YEAR, ConfigOfflineShop.OFFLINE_MAX_DAYS);
		return (cal.getTimeInMillis() <= System.currentTimeMillis());
	}
	
	public static boolean offlineMode(final Player player)
	{
		if (player.isInOlympiadMode() /*|| player.isFestivalParticipant()*/ || player.isInJail() || player.getBoatInfo().getBoat() != null)
			return false;
		
		if (ConfigOfflineShop.OFFLINE_MODE_IN_PEACE_ZONE && !player.isInsideZone(ZoneId.PEACE))
			return false;
		
		switch (player.getOperateType())
		{
			case SELL:
			case PACKAGE_SELL:
			case BUY:
				return ConfigOfflineShop.OFFLINE_TRADE_ENABLE;
			case MANUFACTURE:
				return ConfigOfflineShop.OFFLINE_CRAFT_ENABLE;
		}
		
		return false;
	}
	
	public static final OfflineTradersTable getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static final class SingletonHolder
	{
		protected static final OfflineTradersTable INSTANCE = new OfflineTradersTable();
	}
}
