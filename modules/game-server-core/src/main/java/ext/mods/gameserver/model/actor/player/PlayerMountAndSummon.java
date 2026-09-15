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

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.Summon;
import ext.mods.gameserver.model.actor.instance.Servitor;
import ext.mods.gameserver.model.actor.instance.Pet;
import ext.mods.gameserver.model.actor.instance.TamedBeast;
import ext.mods.gameserver.model.actor.template.PetTemplate;
import ext.mods.gameserver.model.records.PetDataEntry;

/**
 * Represents all mount, summon, and pet-related functionality for a Player.
 * <p>
 * This component OWNS the following state directly:
 * <ul>
 *   <li>{@code _summon} — current {@link Summon} (pet or servitor)</li>
 *   <li>{@code _tamedBeast} — current tamed beast (summonable friend from event)</li>
 *   <li>{@code _throneId} — object id of the throne the player is seated on (0 if none)</li>
 * </ul>
 * <p>
 * Mount state (strider/wyvern riding, flying, mount type/level/npcId, mount object id,
 * feed system, mount/pet lifecycle) is owned by {@link PlayerMount} — this component
 * only delegates to the owning Player for those operations.
 */
public class PlayerMountAndSummon
{
	private final Player _owner;

	// ========== State owned by this component ==========

	private Summon _summon;
	private TamedBeast _tamedBeast;
	private int _throneId;

	/**
	 * Constructor for PlayerMountAndSummon.
	 * @param owner The Player that owns this component.
	 */
	public PlayerMountAndSummon(Player owner)
	{
		_owner = owner;
	}

	// ========== Summon (pet / servitor) ==========

	/**
	 * @return the {@link Summon} of the {@link Player}, or null if none.
	 */
	public Summon getSummon()
	{
		return _summon;
	}

	/**
	 * Set the {@link Summon} of the {@link Player}.
	 * @param summon : The Summon to set.
	 */
	public void setSummon(Summon summon)
	{
		_summon = summon;
	}

	/**
	 * @return true if this {@link Player} has a {@link Pet}, false otherwise.
	 */
	public boolean hasPet()
	{
		return _summon instanceof Pet;
	}

	/**
	 * @return true if this {@link Player} has a {@link Servitor}, false otherwise.
	 */
	public boolean hasServitor()
	{
		return _summon instanceof Servitor;
	}

	// ========== Tamed Beast ==========

	/**
	 * @return the {@link TamedBeast} of this {@link Player}, null otherwise.
	 */
	public TamedBeast getTamedBeast()
	{
		return _tamedBeast;
	}

	/**
	 * Set the {@link TamedBeast} of this {@link Player}.
	 * @param tamedBeast : The TamedBeast to set.
	 */
	public void setTamedBeast(TamedBeast tamedBeast)
	{
		_tamedBeast = tamedBeast;
	}

	// ========== Mount lifecycle (delegates to Player -> PlayerMount) ==========

	/**
	 * Mount a {@link Summon}.
	 * @param pet The summon to mount.
	 * @return true if successful, false otherwise.
	 */
	public boolean mount(Summon pet)
	{
		return _owner.mount(pet);
	}

	/**
	 * Mount a pet by NPC ID and control item ID.
	 * @param npcId The NPC ID of the mount.
	 * @param controlItemId The control item ID.
	 * @return true if successful, false otherwise.
	 */
	public boolean mount(int npcId, int controlItemId)
	{
		return _owner.mount(npcId, controlItemId);
	}

	/**
	 * Test if the {@link Player} can mount the selected {@link Summon} or dismount if already mounted, and act accordingly.
	 * This method is used by both "Actions" panel "Mount/Dismount" button, and /mount /dismount usercommands.
	 * @param summon : The Summon to check.
	 */
	public void mountPlayer(Summon summon)
	{
		_owner.mountPlayer(summon);
	}

	/**
	 * Dismount the current mount.
	 */
	public void dismount()
	{
		_owner.dismount();
	}

	/**
	 * Store pet food for a given pet ID.
	 * @param petId The ID of the pet.
	 */
	public void storePetFood(int petId)
	{
		_owner.storePetFood(petId);
	}

	// Note: startFeed(int) and stopFeed() are protected methods in Player
	// and should be made public if this component needs to expose them.
	// They are delegated to Player._mount component.

	/**
	 * @return the {@link PetTemplate} of the current pet, or null if none.
	 */
	public PetTemplate getPetTemplate()
	{
		return _owner.getPetTemplate();
	}

	/**
	 * @return the {@link PetDataEntry} of the current pet, or null if none.
	 */
	public PetDataEntry getPetDataEntry()
	{
		return _owner.getPetDataEntry();
	}

	/**
	 * @return the current feed level.
	 */
	public int getCurrentFeed()
	{
		return _owner.getCurrentFeed();
	}

	/**
	 * Set the current feed level.
	 * @param num The feed level to set.
	 */
	public void setCurrentFeed(int num)
	{
		_owner.setCurrentFeed(num);
	}

	/**
	 * Check if the food state has reached the specified threshold.
	 * @param state : The state to check (can be autofeed, hungry or unsummon).
	 * @return true if the limit is reached, false otherwise or if there is no need to feed.
	 */
	public boolean checkFoodState(double state)
	{
		return _owner.checkFoodState(state);
	}

	/**
	 * Set the uptime for the player session.
	 * @param time The uptime in milliseconds.
	 */
	public void setUptime(long time)
	{
		_owner.setUptime(time);
	}

	/**
	 * @return the uptime for the player session.
	 */
	public long getUptime()
	{
		return _owner.getUptime();
	}

	// ========== Mount state (delegates to Player -> PlayerMount) ==========

	/**
	 * @return true if the Player is mounted, false otherwise.
	 */
	public boolean isMounted()
	{
		return _owner.isMounted();
	}

	// ========== Throne / seated state (OWNED by this component) ==========

	/**
	 * @return true if the Player is seated on a throne, false otherwise.
	 */
	public boolean isSeated()
	{
		return _throneId > 0;
	}

	/**
	 * @return the throne ID if seated, 0 otherwise.
	 */
	public int getThroneId()
	{
		return _throneId;
	}

	/**
	 * Set the throne ID.
	 * @param id The throne ID to set.
	 */
	public void setThroneId(int id)
	{
		_throneId = id;
	}

	/**
	 * @return true if the Player is riding a mount (strider), false otherwise.
	 */
	public boolean isRiding()
	{
		return _owner.isRiding();
	}

	/**
	 * @return true if the Player is flying (wyvern), false otherwise.
	 */
	public boolean isFlying()
	{
		return _owner.isFlying();
	}

	/**
	 * @return the type of Pet mounted (0 : none, 1 : Strider, 2 : Wyvern).
	 */
	public int getMountType()
	{
		return _owner.getMountType();
	}

	/**
	 * @return the NPC ID of the current mount.
	 */
	public int getMountNpcId()
	{
		return _owner.getMountNpcId();
	}

	/**
	 * @return the level of the current mount.
	 */
	public int getMountLevel()
	{
		return _owner.getMountLevel();
	}

	/**
	 * Update mount information by NPC ID and level.
	 * This method allows to:
	 * - change isRiding/isFlying flags
	 * - gift player with Wyvern Breath skill if mount is a wyvern
	 * - send the skillList (faded icons update)
	 * @param npcId the npcId of the mount
	 * @param npcLevel The level of the mount
	 * @param mountType 0, 1 or 2 (dismount, strider or wyvern).
	 */
	public void setMount(int npcId, int npcLevel, int mountType)
	{
		_owner.setMount(npcId, npcLevel, mountType);
	}

	/**
	 * Set the object ID of the mount.
	 * @param id The mount object ID.
	 */
	public void setMountObjectId(int id)
	{
		_owner.setMountObjectId(id);
	}

	/**
	 * @return the object ID of the current mount.
	 */
	public int getMountObjectId()
	{
		return _owner.getMountObjectId();
	}

	/**
	 * Called when the Player enters a "No Landing" zone.
	 * If a player is mounted on a Wyvern, it launches a dismount task after 5 seconds, and a warning message.
	 */
	public void enterOnNoLandingZone()
	{
		_owner.enterOnNoLandingZone();
	}

	/**
	 * Called when the Player exits a "No Landing" zone.
	 * If a player is mounted on a Wyvern, it cancels the dismount task, if existing.
	 */
	public void exitOnNoLandingZone()
	{
		_owner.exitOnNoLandingZone();
	}

	/**
	 * Unsummon all types of summons: pets, cubics, normal summons and trained beasts.
	 */
	public void dropAllSummons()
	{
		_owner.dropAllSummons();
	}
}