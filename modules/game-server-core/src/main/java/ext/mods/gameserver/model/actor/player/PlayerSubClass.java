package ext.mods.gameserver.model.actor.player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ext.mods.commons.pool.ConnectionPool;
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
	
	private static final String RESTORE_CHAR_SUBCLASSES = "SELECT class_id,exp,sp,level,class_index FROM character_subclasses WHERE char_obj_id=? ORDER BY class_index ASC";
	private static final String ADD_CHAR_SUBCLASS = "INSERT INTO character_subclasses (char_obj_id,class_id,exp,sp,level,class_index) VALUES (?,?,?,?,?,?)";
	private static final String UPDATE_CHAR_SUBCLASS = "UPDATE character_subclasses SET exp=?,sp=?,level=?,class_id=? WHERE char_obj_id=? AND class_index =?";
	private static final String DELETE_CHAR_SUBCLASS = "DELETE FROM character_subclasses WHERE char_obj_id=? AND class_index=?";
	
	private static final String DELETE_CHAR_HENNAS = "DELETE FROM character_hennas WHERE char_obj_id=? AND class_index=?";
	private static final String DELETE_CHAR_SHORTCUTS = "DELETE FROM character_shortcuts WHERE char_obj_id=? AND class_index=?";
	
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
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(RESTORE_CHAR_SUBCLASSES))
		{
			ps.setInt(1, player.getObjectId());
			
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final SubClass subClass = new SubClass(rs.getInt("class_id"), rs.getInt("class_index"), rs.getLong("exp"), rs.getInt("sp"), rs.getByte("level"));
					player.getSubClasses().put(subClass.getClassIndex(), subClass);
				}
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
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(UPDATE_CHAR_SUBCLASS))
		{
			for (final SubClass subClass : _subClasses.values())
			{
				ps.setLong(1, subClass.getExp());
				ps.setInt(2, subClass.getSp());
				ps.setInt(3, subClass.getLevel());
				ps.setInt(4, subClass.getClassId());
				ps.setInt(5, _owner.getObjectId());
				ps.setInt(6, subClass.getClassIndex());
				ps.addBatch();
			}
			ps.executeBatch();
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
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(ADD_CHAR_SUBCLASS))
		{
			ps.setInt(1, _owner.getObjectId());
			ps.setInt(2, subclass.getClassId());
			ps.setLong(3, subclass.getExp());
			ps.setInt(4, subclass.getSp());
			ps.setInt(5, subclass.getLevel());
			ps.setInt(6, subclass.getClassIndex());
			ps.execute();
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
			try (Connection con = ConnectionPool.getConnection())
			{
				final boolean previousAutoCommit = con.getAutoCommit();
				con.setAutoCommit(false);
				try
				{
					try (PreparedStatement ps = con.prepareStatement(DELETE_CHAR_HENNAS))
					{
						ps.setInt(1, _owner.getObjectId());
						ps.setInt(2, classIndex);
						ps.execute();
					}
					
					try (PreparedStatement ps = con.prepareStatement(DELETE_CHAR_SHORTCUTS))
					{
						ps.setInt(1, _owner.getObjectId());
						ps.setInt(2, classIndex);
						ps.execute();
					}
					
					// Same connection as other deletes (avoid multi-connection wipe).
					try (PreparedStatement ps = con.prepareStatement(PlayerSkillsDb.DELETE_SKILL_SAVE))
					{
						ps.setInt(1, _owner.getObjectId());
						ps.setInt(2, classIndex);
						ps.execute();
					}
					
					try (PreparedStatement ps = con.prepareStatement(PlayerSkillsDb.DELETE_CHAR_SKILLS))
					{
						ps.setInt(1, _owner.getObjectId());
						ps.setInt(2, classIndex);
						ps.execute();
					}
					
					try (PreparedStatement ps = con.prepareStatement(DELETE_CHAR_SUBCLASS))
					{
						ps.setInt(1, _owner.getObjectId());
						ps.setInt(2, classIndex);
						ps.execute();
					}
					
					con.commit();
				}
				catch (Exception e)
				{
					try
					{
						con.rollback();
					}
					catch (Exception re)
					{
						LOGGER.warn("Couldn't rollback subclass wipe for {}.", _owner.getName(), re);
					}
					throw e;
				}
				finally
				{
					try
					{
						con.setAutoCommit(previousAutoCommit);
					}
					catch (Exception ignored)
					{
					}
				}
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
