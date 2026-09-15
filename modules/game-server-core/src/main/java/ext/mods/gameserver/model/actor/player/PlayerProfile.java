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
package ext.mods.gameserver.model.actor.player;

import java.util.Locale;

import ext.mods.commons.cached.CachedData;
import ext.mods.commons.cached.CachedDataValueBoolean;
import ext.mods.commons.cached.CachedDataValueInt;
import ext.mods.commons.cached.CachedDataValueObject;
import ext.mods.commons.cached.CachedDataValueObject.Converter;

import ext.mods.config.ConfigLanguage;
import ext.mods.config.ConfigProject;

import ext.mods.gameserver.LoginServerThread;
import ext.mods.gameserver.data.sql.PlayerInfoTable;
import ext.mods.gameserver.data.xml.AdminData;
import ext.mods.gameserver.data.xml.SysString;
import ext.mods.gameserver.enums.actors.ClassId;
import ext.mods.gameserver.enums.actors.ClassRace;
import ext.mods.gameserver.model.AccessLevel;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.container.player.Appearance;
import ext.mods.gameserver.model.actor.template.PlayerTemplate;

/**
 * Manages Character Profile and Appearance data for a {@link Player}.
 * <p>
 * Includes: appearance, name/title colors, access level, base class/template,
 * newbie status, CachedData preferences (stopExp, tradeRefusal, autoLoot,
 * buffProtected, ACP settings), and locale.
 */
public final class PlayerProfile
{
	private final Player _owner;

	// CachedData and persistent preferences
	private final CachedData _cachedData;
	private final CachedDataValueInt _setNameColor;
	private final CachedDataValueInt _setTitleColor;
	private final CachedDataValueBoolean _stopExp;
	private final CachedDataValueBoolean _tradeRefusal;
	private final CachedDataValueBoolean _autoLoot;
	private final CachedDataValueBoolean _buffProtected;
	private final CachedDataValueObject<Locale> _locale;
	private final CachedDataValueInt _acpCp;
	private final CachedDataValueInt _acpHp;
	private final CachedDataValueInt _acpMp;

	// Access level
	private AccessLevel _accessLevel;

	// Original title backup
	private String _originalTitle;

	public PlayerProfile(Player owner)
	{
		_owner = owner;

		_cachedData = new CachedData(owner.getObjectId());
		_setNameColor = _cachedData.newInt("nameColor");
		_setTitleColor = _cachedData.newInt("titleColor");
		_stopExp = _cachedData.newBoolean("stopexp", ConfigProject.PROP_STOP_EXP);
		_tradeRefusal = _cachedData.newBoolean("traderefusal", ConfigProject.PROP_TRADE_REFUSAL);
		_autoLoot = _cachedData.newBoolean("autoloot", ConfigProject.PROP_AUTO_LOOT);
		_buffProtected = _cachedData.newBoolean("buffProtected", ConfigProject.PROP_BUFF_PROTECTED);
		_locale = _cachedData.newObject("locale", ConfigLanguage.DEFAULT_LOCALE.toLanguageTag(), new Converter<>()
		{
			@Override
			public Locale fromString(String value)
			{
				return Locale.forLanguageTag(value);
			}

			@Override
			public String toString(Locale locale)
			{
				return locale.toString().replace("_", "-");
			}
		});
		_acpCp = _cachedData.newInt("acpCp", 95);
		_acpHp = _cachedData.newInt("acpHp", 95);
		_acpMp = _cachedData.newInt("acpMp", 75);
	}

	// --- Appearance ---

	/**
	 * @return The {@link Appearance} of the owner {@link Player}.
	 */
	public Appearance getAppearance()
	{
		return _owner.getAppearance();
	}

	// --- CachedData ---

	/**
	 * @return The {@link CachedData} instance for this player's profile.
	 */
	public CachedData getCachedData()
	{
		return _cachedData;
	}

	// --- Name/Title Colors ---

	public int getNameColor()
	{
		return _setNameColor.get();
	}

	public int setNameColor(int value)
	{
		_setNameColor.set(value);
		return value;
	}

	public int getTitleColor()
	{
		return _setTitleColor.get();
	}

	public int setTitleColor(int value)
	{
		_setTitleColor.set(value);
		return value;
	}

	// --- Access Level ---

	/**
	 * @return The {@link AccessLevel} of this player.
	 */
	public AccessLevel getAccessLevel()
	{
		return _accessLevel;
	}

	/**
	 * Set the {@link AccessLevel} of the player.
	 * <ul>
	 * <li>If invalid, set the default user access level 0.</li>
	 * <li>If superior to 0, it means it's a special access.</li>
	 * </ul>
	 * @param level : The level to set.
	 */
	public void setAccessLevel(int level)
	{
		AccessLevel accessLevel = AdminData.getInstance().getAccessLevel(level);
		if (accessLevel == null)
		{
			accessLevel = AdminData.getInstance().getAccessLevel(0);
		}

		_accessLevel = accessLevel;

		if (level > 0)
			_owner.setTitle(accessLevel.getName());

		if (accessLevel.isGm())
		{
			if (!AdminData.getInstance().isRegisteredAsGM(_owner))
				AdminData.getInstance().addGm(_owner, false);
		}
		else
			AdminData.getInstance().deleteGm(_owner);

		getAppearance().setNameColor(accessLevel.getNameColor());
		getAppearance().setTitleColor(accessLevel.getTitleColor());
		_owner.broadcastUserInfo();

		PlayerInfoTable.getInstance().updatePlayerData(_owner, true);
	}

	/**
	 * @return True if the player is a GM.
	 */
	public boolean isGM()
	{
		return _accessLevel != null && _accessLevel.isGm();
	}

	// --- Base Class / Template ---

	/**
	 * @return The base class id.
	 */
	public int getBaseClass()
	{
		return _owner.getBaseClass();
	}

	/**
	 * Set the base class id.
	 * @param baseClass : The class id to set.
	 */
	public void setBaseClass(int baseClass)
	{
		_owner.setBaseClass(baseClass);
	}

	/**
	 * Set the base class from a {@link ClassId}.
	 * @param classId : The ClassId to set.
	 */
	public void setBaseClass(ClassId classId)
	{
		_owner.setBaseClass(classId);
	}

	/**
	 * @return The {@link PlayerTemplate} linked to the base class.
	 */
	public PlayerTemplate getBaseTemplate()
	{
		return _owner.getBaseTemplate();
	}

	/**
	 * @return The {@link ClassId} of the player.
	 */
	public ClassId getClassId()
	{
		return _owner.getClassId();
	}

	/**
	 * @return The {@link ClassRace} of the player. If subclass is active, uses base template.
	 */
	public ClassRace getRace()
	{
		return _owner.getRace();
	}

	/**
	 * A newbie is a player between level 6 and 25 which didn't yet acquire first occupation change.
	 * @param checkLowLevel : If true, check also low level requirement.
	 * @return True if this player can be considered a Newbie.
	 */
	public boolean isNewbie(boolean checkLowLevel)
	{
		return (checkLowLevel)
			? (getClassId().getLevel() <= 1 && _owner.getStatus().getLevel() >= 6 && _owner.getStatus().getLevel() <= 25)
			: (getClassId().getLevel() <= 1 && _owner.getStatus().getLevel() <= 25);
	}

	/**
	 * Apply a class template by id. Delegates to the owner's public method.
	 * @param classId : The class id to apply.
	 */
	public void setClassTemplate(int classId)
	{
		_owner.setClassTemplate(classId);
	}

	/**
	 * Send account access level change request to the login server.
	 * @param level : The level to set.
	 */
	public void setAccountAccesslevel(int level)
	{
		LoginServerThread.getInstance().sendAccessLevel(_owner.getAccountName(), level);
	}

	// --- Stop Exp ---

	public void setStopExp(boolean value)
	{
		_stopExp.set(value);
	}

	public boolean getStopExp()
	{
		return _stopExp.get();
	}

	// --- Trade Refusal ---

	public void setTradeRefusal(boolean value)
	{
		_tradeRefusal.set(value);
	}

	public boolean getTradeRefusal()
	{
		return _tradeRefusal.get();
	}

	// --- Auto Loot ---

	public void setAutoLoot(boolean value)
	{
		_autoLoot.set(value);
	}

	public boolean getAutoLoot()
	{
		return _autoLoot.get();
	}

	// --- Buff Protected ---

	public void setBuffProtected(boolean value)
	{
		_buffProtected.set(value);
	}

	public boolean isBuffProtected()
	{
		return _buffProtected.get();
	}

	// --- Locale ---

	public Locale getLocale()
	{
		// Defensive: until CachedData.load() runs, the CachedDataValueObject holds null. Fall back to the configured default.
		final Locale resolved = _locale.get();
		return resolved != null ? resolved : ConfigLanguage.DEFAULT_LOCALE;
	}

	public void setLocale(Locale locale)
	{
		// Defensive: CachedDataValueObject holds null until its CachedData.load() runs; never overwrite a valid Locale with null.
		if (locale == null)
			return;
		_locale.set(locale);
	}

	/**
	 * Retrieve a system string by id for the player's locale.
	 * @param id : The system string id.
	 * @param args : Optional format arguments.
	 * @return The formatted localized string.
	 */
	public String getSysString(int id, Object... args)
	{
		return SysString.getInstance().get(getLocale(), "" + id).formatted(args);
	}

	// --- ACP Settings ---

	public int getAcpCp()
	{
		return _acpCp.get();
	}

	public void setAcpCp(int value)
	{
		_acpCp.set(value);
	}

	public int getAcpHp()
	{
		return _acpHp.get();
	}

	public void setAcpHp(int value)
	{
		_acpHp.set(value);
	}

	public int getAcpMp()
	{
		return _acpMp.get();
	}

	public void setAcpMp(int value)
	{
		_acpMp.set(value);
	}

	// --- Original Title ---

	public String getOriginalTitle()
	{
		return _originalTitle;
	}

	public void setOriginalTitle(String title)
	{
		_originalTitle = title;
	}
}
