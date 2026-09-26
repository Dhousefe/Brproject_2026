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
package ext.mods.gameserver.scripting.script.event;

import ext.mods.gameserver.data.adapter.JdbcCustomEventStateStore;
import ext.mods.gameserver.data.repository.CustomEventStateStore;
import ext.mods.gameserver.scripting.Quest;

public abstract class Events extends Quest
{
	private final CustomEventStateStore _stateStore;
	
	public Events()
	{
		this(new JdbcCustomEventStateStore());
	}

	protected Events(CustomEventStateStore stateStore)
	{
		super(-1, "events");
		_stateStore = stateStore;
		
		restoreStatus(0);
	}
	
	public abstract boolean eventStart(int priority);
	
	public abstract boolean eventStop();
	
	public void eventStatusStart(int priority)
	{
		updateStatus(true);
	}
	
	public void eventStatusStop()
	{
		updateStatus(false);
	}
	
	private void restoreStatus(int priority)
	{
		if (_stateStore.isEnabled(getName()))
			eventStart(priority);
		else
			eventStop();
	}
	
	private void updateStatus(boolean newEvent)
	{
		_stateStore.setEnabled(getName(), newEvent);
	}
}
