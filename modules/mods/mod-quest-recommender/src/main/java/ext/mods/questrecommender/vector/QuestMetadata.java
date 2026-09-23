package ext.mods.questrecommender.vector;

import ext.mods.gameserver.enums.actors.ClassId;
import ext.mods.gameserver.enums.actors.ClassRace;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.location.Location;

/**
 * Metadados complementares da Quest indexada pelo motor vetorial.
 */
public final class QuestMetadata
{
	private final int _questId;
	private final String _name;
	private final int _minLevel;
	private final int _maxLevel;
	private final int _startNpcId;
	private final Location _startNpcLoc;
	private final boolean _repeatable;
	private final String _rewardDescription;
	private final QuestRewardItem[] _rewards;
	private final ClassRace[] _allowedRaces;
	private final ClassId[] _allowedClasses;

	public QuestMetadata(int questId, String name, int minLevel, int maxLevel, int startNpcId, Location startNpcLoc, boolean repeatable, String rewardDescription)
	{
		this(questId, name, minLevel, maxLevel, startNpcId, startNpcLoc, repeatable, rewardDescription, null, null, null);
	}

	public QuestMetadata(int questId, String name, int minLevel, int maxLevel, int startNpcId, Location startNpcLoc, boolean repeatable, String rewardDescription, ClassRace[] allowedRaces, ClassId[] allowedClasses)
	{
		this(questId, name, minLevel, maxLevel, startNpcId, startNpcLoc, repeatable, rewardDescription, null, allowedRaces, allowedClasses);
	}

	public QuestMetadata(int questId, String name, int minLevel, int maxLevel, int startNpcId, Location startNpcLoc, boolean repeatable, String rewardDescription, QuestRewardItem[] rewards, ClassRace[] allowedRaces, ClassId[] allowedClasses)
	{
		_questId = questId;
		_name = name;
		_minLevel = minLevel;
		_maxLevel = maxLevel;
		_startNpcId = startNpcId;
		_startNpcLoc = startNpcLoc;
		_repeatable = repeatable;
		_rewardDescription = rewardDescription;
		_rewards = rewards;
		_allowedRaces = allowedRaces;
		_allowedClasses = allowedClasses;
	}

	public int getQuestId()
	{
		return _questId;
	}

	public String getName()
	{
		return _name;
	}

	public int getMinLevel()
	{
		return _minLevel;
	}

	public int getMaxLevel()
	{
		return _maxLevel;
	}

	public int getStartNpcId()
	{
		return _startNpcId;
	}

	public Location getStartNpcLoc()
	{
		return _startNpcLoc;
	}

	public boolean isRepeatable()
	{
		return _repeatable;
	}

	public String getRewardDescription()
	{
		return _rewardDescription;
	}

	public QuestRewardItem[] getRewards()
	{
		return _rewards;
	}

	public ClassRace[] getAllowedRaces()
	{
		return _allowedRaces;
	}

	public ClassId[] getAllowedClasses()
	{
		return _allowedClasses;
	}

	/**
	 * Indica se a quest concede experiência (Exp/SP) como recompensa.
	 */
	public boolean hasExpReward()
	{
		if (_rewardDescription != null)
		{
			final String desc = _rewardDescription.toLowerCase();
			if (desc.contains("exp") || desc.contains("xp") || desc.contains("sp"))
				return true;
		}
		if (_repeatable)
			return false;

		return _questId <= 100 || (_questId >= 401 && _questId <= 423);
	}

	/**
	 * Verifica com zero alocação se o jogador é elegível para esta quest com base em sua raça e classe.
	 */
	public boolean isEligible(Player player)
	{
		if (player == null)
			return false;

		// Validação de Raça
		if (_allowedRaces != null && _allowedRaces.length > 0)
		{
			final ClassRace playerRace = player.getRace();
			boolean raceMatch = false;
			for (ClassRace r : _allowedRaces)
			{
				if (r == playerRace)
				{
					raceMatch = true;
					break;
				}
			}
			if (!raceMatch)
				return false;
		}

		// Validação de Classe
		if (_allowedClasses != null && _allowedClasses.length > 0)
		{
			final ClassId playerClassId = player.getClassId();
			boolean classMatch = false;
			for (ClassId cid : _allowedClasses)
			{
				if (cid != null && playerClassId.equalsOrIsChildOf(cid))
				{
					classMatch = true;
					break;
				}
			}
			if (!classMatch)
				return false;
		}

		return true;
	}
}

