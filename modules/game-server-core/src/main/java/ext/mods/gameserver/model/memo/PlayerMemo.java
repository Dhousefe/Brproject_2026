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
package ext.mods.gameserver.model.memo;

import ext.mods.commons.data.MemoSet;
import ext.mods.commons.logging.CLogger;
import ext.mods.gameserver.data.repository.MemoRecord;
import ext.mods.gameserver.data.service.PlayerAuxiliaryPersistenceService;

/**
 * An implementation of {@link MemoSet} used for Player. There is a restore/save system.
 */
public class PlayerMemo extends MemoSet
{
	private static final long serialVersionUID = 1L;
	
	private static final CLogger LOGGER = new CLogger(PlayerMemo.class.getName());
	
	private final int _objectId;
	
	public PlayerMemo(int objectId)
	{
		_objectId = objectId;
		
		try
		{
			for (MemoRecord memo : PlayerAuxiliaryPersistenceService.loadMemos(_objectId))
			{
				put(memo.key(), memo.value());
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't restore memos for player id {}.", e, _objectId);
		}
	}
	
	@Override
	protected void onSet(String key, String value)
	{
		try
		{
			PlayerAuxiliaryPersistenceService.saveMemo(_objectId, new MemoRecord(key, value));
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't set {} memo for player id {}.", e, key, _objectId);
		}
	}
	
	@Override
	protected void onUnset(String key)
	{
		try
		{
			PlayerAuxiliaryPersistenceService.deleteMemo(_objectId, key);
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't unset {} memo for player id {}.", e, key, _objectId);
		}
	}
}
