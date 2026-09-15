package ext.mods.gameserver.model.actor.player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.commons.pool.ThreadPool;
import ext.mods.gameserver.data.SkillTable.FrequentSkill;
import ext.mods.gameserver.data.xml.NpcData;
import ext.mods.gameserver.enums.GaugeColor;
import ext.mods.gameserver.enums.RestartType;
import ext.mods.gameserver.enums.ZoneId;
import ext.mods.gameserver.enums.actors.MoveType;
import ext.mods.gameserver.geoengine.GeoEngine;
import ext.mods.gameserver.handler.IItemHandler;
import ext.mods.gameserver.handler.ItemHandler;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.Summon;
import ext.mods.gameserver.model.actor.instance.Pet;
import ext.mods.gameserver.model.actor.template.PetTemplate;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.model.records.PetDataEntry;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.Ride;
import ext.mods.gameserver.network.serverpackets.SetupGauge;
import ext.mods.gameserver.network.serverpackets.SkillList;
import ext.mods.gameserver.network.serverpackets.SystemMessage;

/**
 * Mount / strider-wyvern feed for a {@link Player} (att-ver-3.0 onda 7).
 */
public final class PlayerMount
{
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerMount.class);
	private static final String UPDATE_PET_FED = "UPDATE pets SET fed=? WHERE item_obj_id = ?";
	
	private final Player _owner;
	
	private volatile boolean _canFeed;
	private PetTemplate _petTemplate;
	private PetDataEntry _petData;
	private int _controlItemId;
	private int _curFeed;
	private Future<?> _mountFeedTask;
	private ScheduledFuture<?> _dismountTask;
	
	private int _mountType;
	private int _mountNpcId;
	private int _mountLevel;
	private int _mountObjectId;
	
	public PlayerMount(Player owner)
	{
		_owner = owner;
	}
	
	public boolean isMounted()
	{
		return _mountType > 0;
	}
	
	public boolean isRiding()
	{
		return _mountType == 1;
	}
	
	public boolean isFlying()
	{
		return _mountType == 2;
	}
	
	public int getMountType()
	{
		return _mountType;
	}
	
	public int getMountNpcId()
	{
		return _mountNpcId;
	}
	
	public int getMountLevel()
	{
		return _mountLevel;
	}
	
	public void setMountObjectId(int id)
	{
		_mountObjectId = id;
	}
	
	public int getMountObjectId()
	{
		return _mountObjectId;
	}
	
	public PetTemplate getPetTemplate()
	{
		return _petTemplate;
	}
	
	public PetDataEntry getPetDataEntry()
	{
		return _petData;
	}
	
	public int getCurrentFeed()
	{
		return _curFeed;
	}
	
	public void setCurrentFeed(int num)
	{
		_curFeed = Math.min(num, _petData.maxMeal());
		
		_owner.sendPacket(new SetupGauge(GaugeColor.GREEN, getCurrentFeed() * 10000 / getFeedConsume(), _petData.maxMeal() * 10000 / getFeedConsume()));
	}
	
	public boolean checkFoodState(double state)
	{
		return _canFeed && getCurrentFeed() < _petData.maxMeal() * state;
	}
	
	/**
	 * Change mount type flags / wyvern breath skill / skill list.
	 * @param npcId the npcId of the mount
	 * @param npcLevel The level of the mount
	 * @param mountType 0, 1 or 2 (dismount, strider or wyvern).
	 */
	public void setMount(int npcId, int npcLevel, int mountType)
	{
		switch (mountType)
		{
			case 0:
				if (_owner.isFlying())
					_owner.removeSkill(FrequentSkill.WYVERN_BREATH.getSkill().getId(), false);
			case 1:
				_owner.getMove().removeMoveType(MoveType.FLY);
				break;
			
			case 2:
				_owner.addSkill(FrequentSkill.WYVERN_BREATH.getSkill(), false);
				_owner.getMove().addMoveType(MoveType.FLY);
				break;
		}
		
		_mountNpcId = npcId;
		_mountType = mountType;
		_mountLevel = npcLevel;
		
		_owner.sendPacket(new SkillList(_owner));
	}
	
	public boolean mount(Summon pet)
	{
		if (!_owner.disarmWeapon(true))
			return false;
		
		_owner.forceRunStance();
		_owner.stopAllToggles();
		
		final Ride mount = new Ride(_owner.getObjectId(), Ride.ACTION_MOUNT, pet.getTemplate().getNpcId());
		setMount(pet.getNpcId(), pet.getStatus().getLevel(), mount.getMountType());
		
		_petTemplate = (PetTemplate) pet.getTemplate();
		_petData = _petTemplate.getPetDataEntry(pet.getStatus().getLevel());
		_mountObjectId = pet.getControlItemId();
		
		startFeed(pet.getNpcId());
		_owner.broadcastPacket(mount);
		
		_owner.broadcastUserInfo();
		
		pet.unSummon(_owner);
		return true;
	}
	
	public boolean mount(int npcId, int controlItemId)
	{
		if (!_owner.disarmWeapon(true))
			return false;
		
		_owner.forceRunStance();
		_owner.stopAllToggles();
		
		final Ride mount = new Ride(_owner.getObjectId(), Ride.ACTION_MOUNT, npcId);
		
		setMount(npcId, _owner.getStatus().getLevel(), mount.getMountType());
		
		_petTemplate = (PetTemplate) NpcData.getInstance().getTemplate(npcId);
		_petData = _petTemplate.getPetDataEntry(_owner.getStatus().getLevel());
		_mountObjectId = controlItemId;
		// Persist feed for item-based mounts (SummonItems path) as well as pet unsummon path.
		if (controlItemId != 0)
			_controlItemId = controlItemId;
		
		_owner.broadcastPacket(mount);
		
		_owner.broadcastUserInfo();
		
		startFeed(npcId);
		return true;
	}
	
	/**
	 * Mount selected summon or dismount if already mounted.
	 */
	public void mountPlayer(Summon summon)
	{
		if (summon instanceof Pet pet && pet.isMountable() && !isMounted() && !_owner.isBetrayed())
		{
			if (_owner.isDead())
			{
				_owner.sendPacket(SystemMessageId.STRIDER_CANT_BE_RIDDEN_WHILE_DEAD);
				return;
			}
			
			if (pet.isDead())
			{
				_owner.sendPacket(SystemMessageId.DEAD_STRIDER_CANT_BE_RIDDEN);
				return;
			}
			
			if (pet.isInCombat() || pet.isRooted())
			{
				_owner.sendPacket(SystemMessageId.STRIDER_IN_BATLLE_CANT_BE_RIDDEN);
				return;
			}
			
			if (_owner.isInCombat() || _owner.isCursedWeaponEquipped())
			{
				_owner.sendPacket(SystemMessageId.STRIDER_CANT_BE_RIDDEN_WHILE_IN_BATTLE);
				return;
			}
			
			if (_owner.isSitting())
			{
				_owner.sendPacket(SystemMessageId.STRIDER_CAN_BE_RIDDEN_ONLY_WHILE_STANDING);
				return;
			}
			
			if (_owner.isFishing())
			{
				_owner.sendPacket(SystemMessageId.CANNOT_DO_WHILE_FISHING_2);
				return;
			}
			
			if (!_owner.isInStrictRadius(pet, 200))
			{
				_owner.sendPacket(SystemMessageId.TOO_FAR_AWAY_FROM_STRIDER_TO_MOUNT);
				return;
			}
			
			if (pet.checkHungryState())
			{
				_owner.sendPacket(SystemMessageId.HUNGRY_STRIDER_NOT_MOUNT);
				return;
			}
			
			if (!pet.isDead() && !isMounted())
				mount(pet);
		}
		else if (isMounted())
		{
			if (getMountType() == 2)
			{
				if (_owner.isInsideZone(ZoneId.NO_LANDING))
				{
					_owner.sendPacket(SystemMessageId.NO_DISMOUNT_HERE);
					return;
				}
				
				if (Math.abs(_owner.getZ() - GeoEngine.getInstance().getHeight(_owner.getPosition())) > _owner.getTemplate().getSafeFallHeight(_owner.getAppearance().getSex()))
				{
					_owner.sendPacket(SystemMessageId.CANNOT_DISMOUNT_FROM_ELEVATION);
					return;
				}
			}
			
			if (checkFoodState(_petTemplate.getHungryLimit()))
			{
				_owner.sendPacket(SystemMessageId.HUNGRY_STRIDER_NOT_MOUNT);
				return;
			}
			
			dismount();
		}
	}
	
	public void dismount()
	{
		_owner.sendPacket(new SetupGauge(GaugeColor.GREEN, 0));
		
		final int petId = _mountNpcId;
		
		setMount(0, 0, 0);
		stopFeed();
		
		_owner.broadcastPacket(new Ride(_owner.getObjectId(), Ride.ACTION_DISMOUNT, 0));
		
		_petTemplate = null;
		_petData = null;
		_mountObjectId = 0;
		
		storePetFood(petId);
		
		_owner.broadcastUserInfo();
	}
	
	public void storePetFood(int petId)
	{
		if (_controlItemId != 0 && petId != 0)
		{
			try (Connection con = ConnectionPool.getConnection();
				PreparedStatement ps = con.prepareStatement(UPDATE_PET_FED))
			{
				ps.setInt(1, getCurrentFeed());
				ps.setInt(2, _controlItemId);
				ps.executeUpdate();
				
				_controlItemId = 0;
			}
			catch (Exception e)
			{
				LOGGER.error("Couldn't store pet food data for {}.", _controlItemId, e);
			}
		}
	}
	
	public synchronized void startFeed(int npcId)
	{
		stopFeed();
		_canFeed = npcId > 0;
		if (!isMounted())
			return;
		
		if (_owner.getSummon() != null)
		{
			setCurrentFeed(((Pet) _owner.getSummon()).getCurrentFed());
			_controlItemId = _owner.getSummon().getControlItemId();
			_owner.sendPacket(new SetupGauge(GaugeColor.GREEN, getCurrentFeed() * 10000 / getFeedConsume(), _petData.maxMeal() * 10000 / getFeedConsume()));
			if (!_owner.isDead())
				_mountFeedTask = ThreadPool.scheduleAtFixedRate(new FeedTask(), 10000, 10000);
		}
		else if (_canFeed)
		{
			setCurrentFeed(_petData.maxMeal());
			_owner.sendPacket(new SetupGauge(GaugeColor.GREEN, getCurrentFeed() * 10000 / getFeedConsume(), _petData.maxMeal() * 10000 / getFeedConsume()));
			if (!_owner.isDead())
				_mountFeedTask = ThreadPool.scheduleAtFixedRate(new FeedTask(), 10000, 10000);
		}
	}
	
	public synchronized void stopFeed()
	{
		if (_mountFeedTask != null)
		{
			_mountFeedTask.cancel(false);
			_mountFeedTask = null;
		}
	}
	
	public void enterOnNoLandingZone()
	{
		if (getMountType() == 2)
		{
			if (_dismountTask == null)
				_dismountTask = ThreadPool.schedule(this::dismount, 5000);
			
			ThreadPool.schedule(() -> _owner.teleportTo(RestartType.TOWN), 5000);
			_owner.sendPacket(SystemMessageId.AREA_CANNOT_BE_ENTERED_WHILE_MOUNTED_WYVERN);
		}
	}
	
	public void exitOnNoLandingZone()
	{
		if (getMountType() == 2 && _dismountTask != null)
		{
			_dismountTask.cancel(true);
			_dismountTask = null;
		}
	}
	
	private int getFeedConsume()
	{
		return (_owner.isInCombat()) ? _petData.mealInBattle() : _petData.mountMealInNormal();
	}
	
	private final class FeedTask implements Runnable
	{
		@Override
		public void run()
		{
			if (!isMounted())
			{
				stopFeed();
				return;
			}
			
			if (getCurrentFeed() > getFeedConsume())
				setCurrentFeed(getCurrentFeed() - getFeedConsume());
			else
			{
				final boolean wasFlying = isFlying();
				
				setCurrentFeed(0);
				stopFeed();
				dismount();
				_owner.sendPacket(SystemMessageId.OUT_OF_FEED_MOUNT_CANCELED);
				
				if (wasFlying)
					_owner.teleportTo(RestartType.TOWN);
				
				return;
			}
			
			ItemInstance food = _owner.getInventory().getItemByItemId(_petTemplate.getFood1());
			if (food == null)
				food = _owner.getInventory().getItemByItemId(_petTemplate.getFood2());
			
			if (food != null && checkFoodState(_petTemplate.getAutoFeedLimit()))
			{
				final IItemHandler handler = ItemHandler.getInstance().getHandler(food.getEtcItem());
				if (handler != null)
				{
					handler.useItem(_owner, food, false);
					_owner.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.PET_TOOK_S1_BECAUSE_HE_WAS_HUNGRY).addItemName(food));
				}
			}
		}
	}
}
