package ext.mods.extensions.hooks;

import ext.mods.gameserver.model.actor.Creature;

/**
 * Optional heal adjustment pipeline for extracted mods (e.g. BossZerg).
 * Core combat code must call {@link #adjust(Creature, Creature, double)} instead of
 * hard-depending on mod classes.
 */
public final class HealHooks
{
	@FunctionalInterface
	public interface HealAdjuster
	{
		double adjust(Creature effector, Creature effected, double amount);
	}
	
	private static final HealAdjuster IDENTITY = (effector, effected, amount) -> amount;
	
	private static volatile HealAdjuster adjuster = IDENTITY;
	
	private HealHooks()
	{
	}
	
	public static void setAdjuster(HealAdjuster value)
	{
		adjuster = value != null ? value : IDENTITY;
	}
	
	public static void clear()
	{
		adjuster = IDENTITY;
	}
	
	public static boolean hasCustomAdjuster()
	{
		return adjuster != IDENTITY;
	}
	
	public static double adjust(Creature effector, Creature effected, double amount)
	{
		try
		{
			return adjuster.adjust(effector, effected, amount);
		}
		catch (Throwable t)
		{
			return amount;
		}
	}
}
