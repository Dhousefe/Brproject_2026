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
 */
package ext.mods.gameserver.network.clientpackets;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import ext.mods.commons.logging.CLogger;
import ext.mods.config.ConfigProject;
import ext.mods.config.ConfigServer;
import ext.mods.gameserver.enums.SayType;
import ext.mods.gameserver.handler.ChatHandler;
import ext.mods.gameserver.handler.IChatHandler;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.ActionFailed;

/**
 * Validação centralizada e despacho para mensagens de chat padrão (Say2) e mensagens ricas do cliente Fermata (RequestItemLinkChat).
 * Garante que ambos os tipos de solicitação sigam rigorosamente as regras de segurança, níveis mínimos, filtros e logs do servidor.
 */
public final class ChatRequestSupport
{
	private static final CLogger LOGGER = new CLogger(ChatRequestSupport.class.getName());
	private static final Logger CHAT_LOG = Logger.getLogger("chat");
	
	private static final String[] WALKER_COMMAND_LIST =
	{
		"USESKILL", "USEITEM", "BUYITEM", "SELLITEM", "SAVEITEM", "LOADITEM", "MSG",
		"DELAY", "LABEL", "JMP", "CALL", "RETURN", "MOVETO", "NPCSEL", "NPCDLG",
		"DLGSEL", "CHARSTATUS", "POSOUTRANGE", "POSINRANGE", "GOHOME", "SAY", "EXIT",
		"PAUSE", "STRINDLG", "STRNOTINDLG", "CHANGEWAITTYPE", "FORCEATTACK", "ISMEMBER",
		"REQUESTJOINPARTY", "REQUESTOUTPARTY", "QUITPARTY", "MEMBERSTATUS", "CHARBUFFS",
		"ITEMCOUNT", "FOLLOWTELEPORT"
	};
	
	private ChatRequestSupport()
	{
	}
	
	public static void dispatch(Player player, String text, int channelId, String target)
	{
		if (player == null || channelId < 0 || channelId >= SayType.VALUES.length)
			return;
		
		if (text == null || text.isEmpty() || text.length() > 100)
			return;
		
		SayType type = SayType.VALUES[channelId];
		if (ConfigServer.L2WALKER_PROTECTION && type == SayType.TELL && checkBot(text))
			return;
		
		if (!player.isGM() && (type == SayType.ANNOUNCEMENT || type == SayType.CRITICAL_ANNOUNCE))
			return;
		
		if (player.isChatBanned() || (player.isInJail() && !player.isGM()))
		{
			player.sendPacket(SystemMessageId.CHATTING_PROHIBITED);
			return;
		}
		
		int requiredLevel = -1;
		int messageKey = 0;
		
		switch (type)
		{
			case ALL:
				requiredLevel = ConfigProject.CHAT_ALL_LEVEL;
				messageKey = 10113;
				break;
			case TELL:
				requiredLevel = ConfigProject.CHAT_TELL_LEVEL;
				messageKey = 10114;
				break;
			case SHOUT:
				requiredLevel = ConfigProject.CHAT_SHOUT_LEVEL;
				messageKey = 10115;
				break;
			case TRADE:
				requiredLevel = ConfigProject.CHAT_TRADE_LEVEL;
				messageKey = 10116;
				break;
			default:
				break;
		}
		
		if (requiredLevel != -1 && player.getStatus().getLevel() < requiredLevel)
		{
			player.sendMessage(player.getSysString(messageKey, requiredLevel));
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		String processedText = text;
		if (ConfigProject.USE_SAY_FILTER)
		{
			processedText = filterText(processedText);
		}
		
		if (type == SayType.PETITION_PLAYER && player.isGM())
			type = SayType.PETITION_GM;
		
		if (ConfigServer.LOG_CHAT)
		{
			final LogRecord logRecord = new LogRecord(Level.INFO, processedText);
			logRecord.setLoggerName("chat");
			logRecord.setParameters(new Object[]
			{
				type,
				(type == SayType.TELL) ? "[" + player.getName() + " to " + target + "]" : "[" + player.getName() + "]"
			});
			CHAT_LOG.log(logRecord);
		}
		
		final String routedText = processedText.replaceAll("\\\\n", "");
		final IChatHandler handler = ChatHandler.getInstance().getHandler(type);
		if (handler == null)
		{
			LOGGER.warn("{} tried to use unregistered chathandler type: {}.", player.getName(), type);
			return;
		}
		
		handler.handleChat(type, player, target, routedText);
	}
	
	private static boolean checkBot(String text)
	{
		for (String botCommand : WALKER_COMMAND_LIST)
		{
			if (text.startsWith(botCommand))
				return true;
		}
		return false;
	}
	
	private static String filterText(String text)
	{
		String filtered = text;
		for (String pattern : ConfigProject.FILTER_LIST)
		{
			filtered = filtered.replaceAll("(?i)" + pattern, ConfigProject.CHAT_FILTER_CHARS);
		}
		return filtered;
	}
}
