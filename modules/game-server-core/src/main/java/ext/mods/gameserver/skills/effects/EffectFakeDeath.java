/*
 * Copyright (c) 2024-2026 L2 NewEra contributors.
 * Licensed under the GNU General Public License v3.
 */
package ext.mods.gameserver.skills.effects;

import ext.mods.gameserver.enums.skills.EffectType;
import ext.mods.gameserver.model.actor.Creature;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.skills.AbstractEffect;
import ext.mods.gameserver.skills.L2Skill;

/** Connects skill 60 to the player's existing fake-death lifecycle. */
public final class EffectFakeDeath extends AbstractEffect
{
	public EffectFakeDeath(EffectTemplate template, L2Skill skill, Creature effected, Creature effector)
	{
		super(template, skill, effected, effector);
	}

	@Override
	public EffectType getEffectType()
	{
		return EffectType.FAKE_DEATH;
	}

	@Override
	public boolean onStart()
	{
		if (!(getEffected() instanceof Player player))
			return false;

		player.startFakeDeath();
		return true;
	}

	@Override
	public boolean onActionTime()
	{
		return getEffected() instanceof Player player && player.isFakeDeath();
	}

	@Override
	public void onExit()
	{
		if (getEffected() instanceof Player player && player.isFakeDeath())
			player.stopFakeDeath(false);
	}
}
