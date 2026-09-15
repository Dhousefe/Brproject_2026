/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 */
package ext.mods.gameserver.handler.voicedcommandhandlers;


import java.util.List;

import ext.mods.gameserver.data.xml.ItemData;
import ext.mods.gameserver.handler.IVoicedCommandHandler;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.item.kind.Item;
import ext.mods.gameserver.network.serverpackets.NpcHtmlMessage;

import ext.mods.tour.TourData;
import ext.mods.tour.holder.TourPrizeHolder;
import ext.mods.tour.ranking.TournamentRankingManager;
import ext.mods.tour.ranking.holder.PlayerRankingData;

public class VoicedTournamentRank implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS =
	{
		"tournamentrank",
		"tournamentlistreward"
	};
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String target)
	{
		if (command.equalsIgnoreCase("tournamentrank"))
		{
			showRanking(player);
			return true;
		}
		else if (command.equalsIgnoreCase("tournamentlistreward"))
		{
			showTournamentReward(player);
			return true;
		}
		
		return false;
	}
	
	private void showTournamentReward(Player player)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("<html><body>");
		sb.append("<center><font color=\"LEVEL\">Tournament Rewards</font><br>");
		sb.append("<table width=270>");
		
		List<TourPrizeHolder> rewards = TourData.getInstance().getPrizes();
		if (rewards == null || rewards.isEmpty())
		{
			sb.append("<tr><td>No rewards configured.</td></tr>");
			sb.append("</table>");
			sb.append("<br><button value=\"Back\" action=\"bypass -h voiced_tour\" width=65 height=16 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
			sb.append("</center></body></html>");
			
			NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setHtml(sb.toString());
			player.sendPacket(html);
			return;
		}
		
		for (TourPrizeHolder prize : rewards)
		{
			int position = prize.getPosition();
			String reward = prize.getReward();
			
			String color;
			switch (position)
			{
				case 1:
					color = "FFD700";
					break;
				case 2:
					color = "C0C0C0";
					break;
				case 3:
					color = "CD7F32";
					break;
				default:
					color = "FFFFFF";
					break;
			}
			
			sb.append("<tr>");
			sb.append("<td colspan=\"2\"><font color=\"").append(color).append("\">#").append(position).append(" Place:</font></td>");
			sb.append("</tr>");
			
			String[] rewardsArray = reward.split(";");
			for (String rewardItem : rewardsArray)
			{
				String[] parts = rewardItem.split("-");
				int itemId = Integer.parseInt(parts[0]);
				int quantity = Integer.parseInt(parts[1]);
				
				String itemName = getItemName(itemId);
				
				sb.append("<tr>");
				sb.append("<td width=180><font color=\"FFFFFF\">").append(itemName).append("</font></td>");
				sb.append("<td width=90 align=right><font color=\"FFFFFF\">x").append(quantity).append("</font></td>");
				sb.append("</tr>");
			}
			
			sb.append("<tr><td colspan=\"2\"><img src=\"L2UI.SquareGray\" width=\"270\" height=\"1\"></td></tr>");
		}
		
		sb.append("</table>");
		sb.append("<br><button value=\"Back\" action=\"bypass -h voiced_tour\" width=65 height=16 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
		sb.append("</center></body></html>");
		
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
	
	private String getItemName(int itemId)
	{
		Item item = ItemData.getInstance().getTemplate(itemId);
		return item != null ? item.getName() : "Unknown Item";
	}
	
	private void showRanking(Player player)
	{
		List<PlayerRankingData> rankings = TournamentRankingManager.getInstance().getTopRankings();
		
		StringBuilder sb = new StringBuilder();
		sb.append("<html><body>");
		sb.append("<center><font color=\"LEVEL\">Tournament Ranking</font><br>");
		sb.append("<table width=270>");
		
		int position = 1;
		for (PlayerRankingData data : rankings)
		{
			if (position > 10)
				break;
			
			String color;
			switch (position)
			{
				case 1:
					color = "FFD700";
					break;
				case 2:
					color = "C0C0C0";
					break;
				case 3:
					color = "CD7F32";
					break;
				default:
					color = "FFFFFF";
					break;
			}
			
			sb.append("<tr>");
			sb.append("<td width=30><font color=\"").append(color).append("\">#").append(position).append("</font></td>");
			sb.append("<td width=150><font color=\"").append(color).append("\">").append(data.getPlayerName()).append("</font></td>");
			sb.append("<td width=60 align=right><font color=\"").append(color).append("\">").append(data.getPoints()).append(" pts</font></td>");
			sb.append("</tr>");
			
			position++;
		}
		
		sb.append("</table>");
		sb.append("<br><button value=\"Back\" action=\"bypass -h voiced_tour\" width=65 height=16 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
		sb.append("</center></body></html>");
		
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}
