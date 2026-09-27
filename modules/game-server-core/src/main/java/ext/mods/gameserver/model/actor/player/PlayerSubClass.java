package ext.mods.gameserver.model.actor.player;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ext.mods.gameserver.data.repository.SubclassRecord;
import ext.mods.gameserver.data.service.SubclassPersistenceService;
import ext.mods.gameserver.data.xml.PlayerData;
import ext.mods.gameserver.model.actor.Creature;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.container.player.SubClass;
import ext.mods.gameserver.model.actor.instance.Servitor;
import ext.mods.gameserver.model.holder.skillnode.GeneralSkillNode;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.network.serverpackets.EtcStatusUpdate;
import ext.mods.gameserver.network.serverpackets.ShortCutInit;
import ext.mods.gameserver.network.serverpackets.SkillCoolTime;
import ext.mods.gameserver.network.serverpackets.SocialAction;
import ext.mods.gameserver.scripting.QuestState;
import ext.mods.gameserver.skills.L2Skill;

/**
 * Subclass map, class index and lock for a {@link Player} (att-ver-3.0 onda 2 / A2).
 * {@link #setActiveClass(int)} remains orchestration-heavy and lives here to keep Player thin.
 */
public final class PlayerSubClass
{
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerSubClass.class);
	
	private static final Comparator<GeneralSkillNode> COMPARE_SKILLS_BY_LVL = Comparator.comparing(GeneralSkillNode::getValue);
	
	private final Player _owner;
	private final Map<Integer, SubClass> _subClasses = new ConcurrentSkipListMap<>();
	private final ReentrantLock _lock = new ReentrantLock();
	private int _classIndex;
	
	public PlayerSubClass(Player owner)
	{
		_owner = owner;
	}
	
	public Map<Integer, SubClass> getSubClasses()
	{
		return _subClasses;
	}
	
	public int getClassIndex()
	{
		return _classIndex;
	}
	
	public void setClassIndex(int classIndex)
	{
		_classIndex = classIndex;
	}
	
	public boolean isSubClassActive()
	{
		return _classIndex > 0;
	}
	
	public boolean isLocked()
	{
		return _lock.isLocked();
	}
	
	public boolean tryLock()
	{
		return _lock.tryLock();
	}
	
	public void unlock()
	{
		_lock.unlock();
	}
	
	public SubClass get(int classIndex)
	{
		return _subClasses.get(classIndex);
	}
	
	/**
	 * Restores sub-class data for the player from character_subclasses.
	 * @return true if successful.
	 */
	public static boolean restoreSubClassData(Player player)
	{
		try
		{
			for (SubclassRecord record : SubclassPersistenceService.load(player.getObjectId()))
			{
				final SubClass subClass = new SubClass(record.classId(), record.classIndex(), record.exp(), record.sp(), (byte) record.level());
				player.getSubClasses().put(subClass.getClassIndex(), subClass);
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't restore subclasses for {}.", player.getName(), e);
			return false;
		}
		
		return true;
	}
	
	/** Batch-update all subclass exp/sp/level/class_id rows. */
	public void storeCharSub()
	{
		if (_subClasses.isEmpty())
			return;
		
		try
		{
			final var records = _subClasses.values().stream().map(subClass -> new SubclassRecord(subClass.getClassId(), subClass.getClassIndex(), subClass.getExp(), subClass.getSp(), subClass.getLevel())).toList();
			SubclassPersistenceService.update(_owner.getObjectId(), records);
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't store subclass data.", e);
		}
	}
	
	/**
	 * Add a subclass slot (max 3). Does not change active class index.
	 */
	public boolean addSubClass(int classId, int classIndex)
	{
		if (!_lock.tryLock())
			return false;
		
		try
		{
			return addSubClassUnlocked(classId, classIndex);
		}
		finally
		{
			_lock.unlock();
		}
	}
	
	/** Caller must hold {@link #_lock}. */
	private boolean addSubClassUnlocked(int classId, int classIndex)
	{
		if (_subClasses.size() == 3 || classIndex == 0 || _subClasses.containsKey(classIndex))
			return false;
		
		final SubClass subclass = new SubClass(classId, classIndex);
		
		try
		{
			SubclassPersistenceService.add(_owner.getObjectId(), new SubclassRecord(subclass.getClassId(), subclass.getClassIndex(), subclass.getExp(), subclass.getSp(), subclass.getLevel()));
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't add subclass for {}.", _owner.getName(), e);
			return false;
		}
		
		_subClasses.put(subclass.getClassIndex(), subclass);
		
		final var template = PlayerData.getInstance().getTemplate(classId);
		if (template != null)
		{
			template.getSkills().stream().filter(s -> s.getMinLvl() <= 40).collect(Collectors.groupingBy(s -> s.getId(), Collectors.maxBy(COMPARE_SKILLS_BY_LVL))).forEach((i, s) ->
			{
				if (s.isPresent() && s.get().getSkill() != null)
					_owner.getSkillsDb().storeSkill(s.get().getSkill(), classIndex);
			});
		}
		
		return true;
	}
	
	/**
	 * Wipe subclass data for classIndex and recreate with newClassId.
	 * Holds the subclass lock across wipe and re-add (no unlock gap).
	 * Wipe deletes run on one connection under {@code setAutoCommit(false)} + commit.
	 */
	public boolean modifySubClass(int classIndex, int newClassId)
	{
		if (!_lock.tryLock())
			return false;
		
		try
		{
			try
			{
				SubclassPersistenceService.wipe(_owner.getObjectId(), classIndex);
			}
			catch (Exception e)
			{
				LOGGER.error("Couldn't modify subclass for {} to class index {}.", _owner.getName(), classIndex, e);
				return false;
			}
			
			_subClasses.remove(classIndex);
			return addSubClassUnlocked(newClassId, classIndex);
		}
		finally
		{
			_lock.unlock();
		}
	}
	
	/**
	 * Switch active class index (0 = base). Full inventory/skills/henna orchestration.
	 */
	public boolean setActiveClass(int classIndex)
	{
		if (!_lock.tryLock())
			return false;
		
		SubClass subclass = null;
		if (classIndex != 0)
		{
			subclass = _subClasses.get(classIndex);
			if (subclass == null)
			{
				_lock.unlock();
				return false;
			}
		}
		
		try
		{
			_owner.getInventory().forEachItem(i -> i.isAugmented() && i.isEquipped(), i -> i.getAugmentation().removeBonus(_owner));
			
			_owner.getCast().stop();
			
			_owner.forEachKnownType(Creature.class, creature -> creature.getFusionSkill() != null && creature.getFusionSkill().getTarget() == _owner, creature -> creature.getCast().stop());
			
			_owner.store();
			
			_owner.clearChargesForClassChange();
			
			_owner.setClassTemplate((subclass == null) ? _owner.getBaseClass() : subclass.getClassId());
			
			_classIndex = classIndex;
			
			if (_owner.getParty() != null)
				_owner.getParty().recalculateLevel();
			
			if (_owner.getSummon() instanceof Servitor)
				_owner.getSummon().unSummon(_owner);
			
			for (final L2Skill skill : _owner.getSkills().values())
				_owner.removeSkill(skill.getId(), false);
			
			_owner.stopAllEffectsExceptThoseThatLastThroughDeath();
			_owner.getCubicList().stopCubics(true);
			
			if (isSubClassActive())
				_owner.getRecipeBook().clear();
			else
				_owner.getRecipeBook().restore();
			
			_owner.getHennaList().restore();
			
			_owner.getSkillsDb().restoreSkills();
			_owner.giveSkills();
			_owner.regiveTemporarySkills();
			
			if (!_owner.hasSkill(L2Skill.SKILL_SUMMON_CP))
			{
				_owner.getDisabledSkills().clear();
				_owner.getReuseTimeStamp().clear();
			}
			
			_owner.updateEffectIcons();
			_owner.sendPacket(new EtcStatusUpdate(_owner));
			
			final QuestState st = _owner.getQuestList().getQuestState("Q422_RepentYourSins");
			if (st != null)
				st.exitQuest(true);
			
			int max = _owner.getStatus().getMaxHp();
			if (_owner.getStatus().getHp() > max)
				_owner.getStatus().setHp(max);
			
			max = _owner.getStatus().getMaxMp();
			if (_owner.getStatus().getMp() > max)
				_owner.getStatus().setMp(max);
			
			max = _owner.getStatus().getMaxCp();
			if (_owner.getStatus().getCp() > max)
				_owner.getStatus().setCp(max);
			
			_owner.refreshWeightPenalty();
			_owner.refreshExpertisePenalty();
			_owner.refreshHennaList();
			_owner.broadcastUserInfo();
			
			_owner.setExpBeforeDeath(0);
			
			_owner.disableAutoShotsAll();
			
			final ItemInstance item = _owner.getActiveWeaponInstance();
			if (item != null)
				item.unChargeAllShots();
			
			_owner.getShortcutList().restore();
			_owner.sendPacket(new ShortCutInit(_owner));
			
			_owner.broadcastPacket(new SocialAction(_owner, 15));
			_owner.sendPacket(new SkillCoolTime(_owner));
			return true;
		}
		finally
		{
			_lock.unlock();
		}
	}
}
