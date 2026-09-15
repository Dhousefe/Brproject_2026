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
package ext.mods.gameserver.handler.chathandlers;

import ext.mods.gameserver.enums.SayType;
import ext.mods.gameserver.handler.IChatHandler;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.pledge.Clan;
import ext.mods.gameserver.network.serverpackets.CreatureSay;
import ext.mods.gameapi.clan.ClanChatRing;

public class ChatClan implements IChatHandler
{
	private static final SayType[] COMMAND_IDS =
	{
		SayType.CLAN
	};
	
	@Override
	public void handleChat(SayType type, Player player, String target, String text)
	{
		final Clan clan = player.getClan();
		if (clan == null)
			return;
		
		String safeText = text.replaceAll("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF\\u00AD\\u034F\\u180E]", "");
		String role = "MEMBER";
		String displayName = player.getName();

		if (player.isClanLeader())
		{
			role = "LEADER";
			if (ext.mods.config.ConfigClans.ENABLE_CLAN_LEADER_CHAT_TAG && ext.mods.config.ConfigClans.CLAN_LEADER_CHAT_TAG != null && !ext.mods.config.ConfigClans.CLAN_LEADER_CHAT_TAG.isEmpty())
			{
				displayName = ext.mods.config.ConfigClans.CLAN_LEADER_CHAT_TAG + " " + player.getName();
			}
		}
		else if (clan.isSubPledgeLeader(player.getObjectId()))
		{
			role = "CAPTAIN";
			if (ext.mods.config.ConfigClans.ENABLE_CLAN_ROYAL_GUARD_CHAT_TAG && ext.mods.config.ConfigClans.CLAN_ROYAL_GUARD_CHAT_TAG != null && !ext.mods.config.ConfigClans.CLAN_ROYAL_GUARD_CHAT_TAG.isEmpty())
			{
				displayName = ext.mods.config.ConfigClans.CLAN_ROYAL_GUARD_CHAT_TAG + " " + player.getName();
			}
		}

		clan.broadcastToMembers(new CreatureSay(player.getObjectId(), type, displayName, safeText));

		// Inject into site ClanChatRing so web users see in-game messages
		try {
			ClanChatRing.INSTANCE.append(clan.getClanId(), player.getObjectId(), player.getName(), safeText, role);
		} catch (Exception ignored) {
			// ClanChatRing may not be initialized if game-api is disabled
		}
	}
	
	@Override
	public SayType[] getChatTypeList()
	{
		return COMMAND_IDS;
	}
}