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
package ext.mods.gameserver.data.manager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import ext.mods.commons.data.xml.IXmlReader;
import ext.mods.commons.lang.StringUtil;
import ext.mods.Config;
import ext.mods.gameserver.data.SkillTable;
import ext.mods.gameserver.data.adapter.JdbcBufferSchemeStore;
import ext.mods.gameserver.data.repository.BufferSchemeStore;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Creature;
import ext.mods.gameserver.model.actor.Npc;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.records.BuffSkill;
import ext.mods.gameserver.skills.L2Skill;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import ext.mods.config.ConfigNpcs;
import ext.mods.config.ConfigProject;

/**
 * Loads and stores available {@link BuffSkill}s for the integrated scheme buffer.<br>
 * Loads and stores Players' buff schemes into _schemesTable (under a {@link String} name and a {@link List} of {@link Integer} skill ids).
 */
public class BufferManager implements IXmlReader
{
	private final Map<Integer, Map<String, ArrayList<L2Skill>>> _schemesTable = new ConcurrentHashMap<>();
	private final Map<L2Skill, BuffSkill> _availableBuffs = new LinkedHashMap<>();
	private final Map<BufferSchemeType, List<BuffSkill>> _availableSchemes = new HashMap<>();
	private final BufferSchemeStore _store;
	
	public enum BufferSchemeType
	{
		FIGHTER,
		MAGE
	}
	
	protected BufferManager()
	{
		this(new JdbcBufferSchemeStore());
	}

	BufferManager(BufferSchemeStore store)
	{
		_store = store;
		load();
	}
	
	@Override
	public void load()
	{
		parseDataFile("custom/mods/bufferSkills.xml");
		LOGGER.info("Loaded {} available buffs.", _availableBuffs.size());
		LOGGER.info("Loaded {} ready to use schemes.", _availableSchemes.size());
		
		try
		{
			for (BufferSchemeStore.SchemeRecord record : _store.loadSchemes())
			{
				final ArrayList<L2Skill> schemeList = new ArrayList<>();
				
				final String[] skills = record.skills().split(",");
				String levelsString = record.levels();
				String[] levels = null;
				if (levelsString != null && levelsString.length() != 0)
					levels = levelsString.split(",");
				for (int i = 0; i < skills.length; i++)
				{
					if (skills[i].isEmpty())
						break;
					
					final int skillId = Integer.parseInt(skills[i]);
					int skillLevel = (levels == null || levels.length == 0) ? SkillTable.getInstance().getMaxLevel(skillId) : Integer.parseInt(levels[i]);
					
					if (skillLevel == -1)
						skillLevel = SkillTable.getInstance().getMaxLevel(skillId);
					
					final L2Skill skill = SkillTable.getInstance().getInfo(skillId, skillLevel);
					if (_availableBuffs.containsKey(skill))
						schemeList.add(skill);
				}
				
				setScheme(record.playerId(), record.name(), schemeList);
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to load schemes data.", e);
		}
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		forEach(doc, "list", listNode ->
		{
			forEach(listNode, "category", categoryNode ->
			{
				final String category = parseString(categoryNode.getAttributes(), "type");
				forEach(categoryNode, "buff", buffNode ->
				{
					final NamedNodeMap attrs = buffNode.getAttributes();
					final int skillId = parseInteger(attrs, "id");
					final int skillLvl = parseInteger(attrs, "level", SkillTable.getInstance().getMaxLevel(skillId));
					final int price = parseInteger(attrs, "price", 0);
					final int time = parseInteger(attrs, "time", 0);
					final String desc = parseString(attrs, "desc", "");
					
					_availableBuffs.put(SkillTable.getInstance().getInfo(skillId, skillLvl), new BuffSkill(skillId, skillLvl, price, time, category, desc));
				});
			});
			
			forEach(listNode, "scheme", schemeNode ->
			{
				final String scheme = parseString(schemeNode.getAttributes(), "type").toUpperCase();
				final List<BuffSkill> skillHolder = new ArrayList<>();
				forEach(schemeNode, "buff", buffNode ->
				{
					final NamedNodeMap attrs = buffNode.getAttributes();
					final int skillId = parseInteger(attrs, "id");
					skillHolder.add(new BuffSkill(skillId, parseInteger(attrs, "level", SkillTable.getInstance().getMaxLevel(skillId)), parseInteger(attrs, "price", 0), parseInteger(attrs, "time", 0), scheme, ""));
				});
				
				_availableSchemes.put(BufferSchemeType.valueOf(scheme), skillHolder);
			});
		});
	}
	
	public void saveSchemes()
	{
		final List<BufferSchemeStore.SchemeRecord> schemes = new ArrayList<>();
		for (Map.Entry<Integer, Map<String, ArrayList<L2Skill>>> player : _schemesTable.entrySet())
		{
			for (Map.Entry<String, ArrayList<L2Skill>> scheme : player.getValue().entrySet())
			{
				final StringBuilder skills = new StringBuilder();
				final StringBuilder levels = new StringBuilder();
				for (L2Skill skill : scheme.getValue())
				{
					StringUtil.append(skills, skill.getId(), ",");
					StringUtil.append(levels, skill.getLevel(), ",");
				}
				if (skills.length() > 0)
					skills.setLength(skills.length() - 1);
				if (levels.length() > 0)
					levels.setLength(levels.length() - 1);
				schemes.add(new BufferSchemeStore.SchemeRecord(player.getKey(), scheme.getKey(), skills.toString(), levels.toString()));
			}
		}
		_store.replaceSchemes(schemes);
	}
	
	/**
	 * Add or retrieve the Player schemes {@link Map}, then add or update the given scheme based on the {@link String} name set as parameter.
	 * @param playerId : The Player objectId to check.
	 * @param schemeName : The {@link String} used as scheme name.
	 * @param list : The {@link ArrayList} of {@link Integer} used as skill ids.
	 */
	public void setScheme(int playerId, String schemeName, ArrayList<L2Skill> list)
	{
		final Map<String, ArrayList<L2Skill>> schemes = _schemesTable.computeIfAbsent(playerId, s -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
		if (schemes.size() >= ConfigNpcs.BUFFER_MAX_SCHEMES)
			return;
		
		schemes.put(schemeName, list);
	}
	
	/**
	 * @param playerId : The Player objectId to check.
	 * @return the {@link List} of schemes for a given Player.
	 */
	public Map<String, ArrayList<L2Skill>> getPlayerSchemes(int playerId)
	{
		return _schemesTable.get(playerId);
	}
	
	/**
	 * @param playerId : The Player objectId to check.
	 * @param schemeName : The scheme name to check.
	 * @return The {@link List} holding {@link L2Skill}s for the given scheme name and Player, or null (if scheme or Player isn't registered).
	 */
	public List<L2Skill> getScheme(int playerId, String schemeName)
	{
		final Player player = World.getInstance().getPlayer(playerId);
		final Map<String, ArrayList<L2Skill>> schemes = _schemesTable.get(playerId);
		if (schemes == null)
			return Collections.emptyList();
		
		final ArrayList<L2Skill> scheme = schemes.get(schemeName);
		if (scheme == null)
			return Collections.emptyList();
		
		if (player.getPremiumService() == 0)
		{
			int j = scheme.size();
			for (int i = 0; i < j; i++)
			{
				if (ConfigProject.PREMIUM_BUFFS_CATEGORY.contains(getAvailableBuff(scheme.get(i)).type()))
				{
					scheme.remove(i);
					i--;
					j--;
				}
			}
		}
		
		return scheme;
	}
	
	/**
	 * Apply all effects of a scheme (retrieved by its Player objectId and {@link String} name) upon a {@link Creature} target.
	 * @param npc : The {@link Npc} which apply effects.
	 * @param target : The {@link Creature} benefactor.
	 * @param playerId : The Player objectId to check.
	 * @param schemeName : The scheme name to check.
	 */
	public void applySchemeEffects(Npc npc, Creature target, int playerId, String schemeName)
	{
		for (L2Skill skill : getScheme(playerId, schemeName))
		{
			final BuffSkill holder = getAvailableBuff(skill);
			if (holder != null)
			{
				final L2Skill s = holder.getSkill();
				if (s != null)
				{
					if (s.isDebuff())
						target.stopAllEffectsDebuff();
					
					s.getEffectsNpc(npc, target);
				}
			}
		}
	}
	
	/**
	 * @param playerId : The Player objectId to check.
	 * @param schemeName : The scheme name to check.
	 * @param skill : The {@link L2Skill} id to check.
	 * @return True if the {@link L2Skill} is already registered on the scheme, or false otherwise.
	 */
	public boolean getSchemeContainsSkill(int playerId, String schemeName, L2Skill skill)
	{
		return getScheme(playerId, schemeName).contains(skill);
	}
	
	/**
	 * @param groupType : The {@link String} group type of skill ids to return.
	 * @return a {@link List} of skill ids based on the given {@link String} groupType.
	 */
	public List<L2Skill> getSkillsIdsByType(String groupType)
	{
		final List<L2Skill> skills = new ArrayList<>();
		for (BuffSkill holder : _availableBuffs.values())
		{
			if (holder.type().equalsIgnoreCase(groupType))
				skills.add(holder.getSkill());
		}
		return skills;
	}
	
	public List<L2Skill> getSchemeSkills(BufferSchemeType schemeType)
	{
		final List<L2Skill> skills = new ArrayList<>();
		_availableSchemes.get(schemeType).forEach(skill -> skills.add(skill.getSkill()));
		return skills;
	}
	
	/**
	 * @return a {@link List} of all available {@link String} buff types.
	 */
	public List<String> getSkillTypes()
	{
		final List<String> skillTypes = new ArrayList<>();
		for (BuffSkill holder : _availableBuffs.values())
		{
			if (!skillTypes.contains(holder.type()))
				skillTypes.add(holder.type());
		}
		return skillTypes;
	}
	
	public BuffSkill getAvailableBuff(L2Skill skill)
	{
		return _availableBuffs.get(skill);
	}
	
	public Map<L2Skill, BuffSkill> getAvailableBuffs()
	{
		return _availableBuffs;
	}
	
	public static BufferManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final BufferManager INSTANCE = new BufferManager();
	}
}
