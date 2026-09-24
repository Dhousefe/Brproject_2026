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
package ext.mods.gameserver.handler.itemhandlers;

import ext.mods.gameserver.data.manager.ItemAutoFarmTimeManager;
import ext.mods.gameserver.handler.IItemHandler;
import ext.mods.gameserver.model.actor.Playable;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.entity.autofarm.AutoFarmManager;
import ext.mods.gameserver.model.entity.autofarm.AutoFarmProfile;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.MagicSkillUse;

/**
 * Handler para itens que concedem tempo extra de AutoFarm ao personagem.
 * Configurado dinamicamente em items.properties através do prefixo:
 * ItemAutoFarmTime_<itemId>=<hours>
 */
public class ItemAutoFarmTime implements IItemHandler
{
	@Override
	public void useItem(Playable playable, ItemInstance item, boolean forceUse)
	{
		if (!(playable instanceof Player player))
			return;

		if (player.isInOlympiadMode())
		{
			player.sendPacket(SystemMessageId.THIS_ITEM_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT);
			return;
		}

		final ItemAutoFarmTimeManager manager = ItemAutoFarmTimeManager.getInstance();
		if (!manager.isConfigured(item.getItemId()))
		{
			player.sendMessage("Este item não está configurado para concessão de tempo de AutoFarm.");
			return;
		}

		final long addMs = manager.getAddedTimeMs(item.getItemId());
		if (addMs <= 0)
		{
			player.sendMessage("Configuração de tempo inválida para este item.");
			return;
		}

		if (!player.destroyItem(item, 1, false))
		{
			player.sendMessage("Não foi possível consumir o item.");
			return;
		}

		final AutoFarmProfile profile = AutoFarmManager.getInstance().getProfile(player);
		profile.addExtraTime(addMs);

		player.broadcastPacket(new MagicSkillUse(player, 2168, 1, 500, 0));

		final long remaining = profile.getRemainingTime();
		if (remaining == Long.MAX_VALUE)
		{
			player.sendMessage("Tempo de AutoFarm estendido com sucesso! Status: Premium (Sem Limite).");
		}
		else
		{
			final long hours = remaining / (3600 * 1000L);
			final long minutes = (remaining % (3600 * 1000L)) / (60 * 1000L);
			final long seconds = (remaining % (60 * 1000L)) / 1000L;
			player.sendMessage(String.format("Tempo de AutoFarm estendido! Tempo total disponível: %02d:%02d:%02d", hours, minutes, seconds));
		}
	}
}
