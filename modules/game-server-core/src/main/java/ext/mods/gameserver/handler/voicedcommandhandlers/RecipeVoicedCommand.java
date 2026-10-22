/*
* Copyleft 2024-2026 L2Brproject
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
package ext.mods.gameserver.handler.voicedcommandhandlers;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import ext.mods.commons.data.Pagination;
import ext.mods.commons.lang.StringUtil;
import ext.mods.commons.logging.CLogger;
import ext.mods.config.ConfigServer;
import ext.mods.gameserver.data.manager.RecipeDropManager;
import ext.mods.gameserver.data.manager.RecipeSearchEngine;
import ext.mods.gameserver.data.manager.RecipeSearchEngine.RecipeEntry;
import ext.mods.gameserver.data.xml.ItemData;
import ext.mods.gameserver.data.xml.NpcData;
import ext.mods.gameserver.data.xml.RecipeData;
import ext.mods.gameserver.enums.DropType;
import ext.mods.gameserver.enums.items.CrystalType;
import ext.mods.gameserver.handler.IVoicedCommandHandler;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.template.NpcTemplate;
import ext.mods.gameserver.model.holder.IntIntHolder;
import ext.mods.gameserver.model.item.DropCategory;
import ext.mods.gameserver.model.item.DropData;
import ext.mods.gameserver.model.item.kind.Item;
import ext.mods.gameserver.model.records.Recipe;
import ext.mods.gameserver.model.records.custom.RecipeDropHolder;
import ext.mods.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * Dhousefe - L2JBR 22/10/2026
 */
public class RecipeVoicedCommand implements IVoicedCommandHandler
{
	private static final CLogger LOGGER = new CLogger(RecipeVoicedCommand.class.getName());
	private static final DecimalFormat CHANCE_FORMAT = new DecimalFormat("0.00##");

	private static final String[] VOICED_COMMANDS = {
		"recipe",
		"recipes",
		"findrecipe",
		"recipe_loc",
		"recipe_mob_loc",
		"recipe_mat_loc",
		"recipe_clearloc",
		"recipe_info",
		"recipe_mat_info",
		"recipe_mob_all_drops"
	};

	private static final String[] GRADE_NAMES = {
		"NG", "D", "C", "B", "A", "S"
	};

	@Override
	public boolean useVoicedCommand(String command, Player player, String target)
	{
		if (!ConfigServer.FIND_RECIPE_ENABLED)
		{
			player.sendMessage("O sistema de busca de recipes (.recipe) esta desativado.");
			return false;
		}

		if (!ConfigServer.FIND_RECIPE_ALLOW_IN_COMBAT && (player.isInCombat() || player.getPvpFlag() != 0))
		{
			player.sendMessage("Voce nao pode consultar recipes durante combate.");
			return false;
		}

		try
		{
			final StringTokenizer st = new StringTokenizer(command, " ");
			final String baseCmd = st.nextToken();

			if ("recipe_loc".equalsIgnoreCase(baseCmd))
			{
				handleGoalLoc(player, st);
				return true;
			}
			else if ("recipe_mob_loc".equalsIgnoreCase(baseCmd))
			{
				handleSingleMobLoc(player, st);
				return true;
			}
			else if ("recipe_mat_loc".equalsIgnoreCase(baseCmd))
			{
				handleMaterialLoc(player, st);
				return true;
			}
			else if ("recipe_clearloc".equalsIgnoreCase(baseCmd))
			{
				handleClearLoc(player, st);
				return true;
			}
			else if ("recipe_info".equalsIgnoreCase(baseCmd))
			{
				handleRecipeInfo(player, st);
				return true;
			}
			else if ("recipe_mat_info".equalsIgnoreCase(baseCmd))
			{
				handleMaterialInfo(player, st);
				return true;
			}
			else if ("recipe_mob_all_drops".equalsIgnoreCase(baseCmd))
			{
				handleMobAllDrops(player, st);
				return true;
			}

			int page = 1;
			int grade = -1;
			int level = 0;
			String query = "";

			if (target != null && !target.trim().isEmpty())
			{
				String trimmed = target.trim();
				int parsedGrade = parseGradeShort(trimmed);
				if (parsedGrade != -2)
				{
					grade = parsedGrade;
				}
				else if (StringUtil.isDigit(trimmed) && trimmed.length() == 1)
				{
					level = Integer.parseInt(trimmed);
				}
				else
				{
					query = trimmed;
				}
			}
			else if (st.hasMoreTokens())
			{
				String firstToken = st.nextToken();
				if (StringUtil.isDigit(firstToken))
				{
					page = Integer.parseInt(firstToken);
					if (st.hasMoreTokens())
					{
						String token = st.nextToken();
						try { grade = Integer.parseInt(token); } catch (Exception ignored) {}
					}
					if (st.hasMoreTokens())
					{
						String token = st.nextToken();
						try { level = Integer.parseInt(token); } catch (Exception ignored) {}
					}
					if (st.hasMoreTokens())
					{
						StringBuilder sb = new StringBuilder();
						while (st.hasMoreTokens())
						{
							sb.append(st.nextToken());
							if (st.hasMoreTokens())
								sb.append(" ");
						}
						query = sb.toString().trim();
					}
				}
				else
				{
					StringBuilder sb = new StringBuilder(firstToken);
					while (st.hasMoreTokens())
					{
						sb.append(" ").append(st.nextToken());
					}
					query = sb.toString().trim();
				}
			}

			showRecipeList(player, page, grade, level, query);
		}
		catch (Exception e)
		{
			LOGGER.error("Error processing voiced command {}", command, e);
			showRecipeList(player, 1, -1, 0, "");
		}

		return true;
	}

	private static int parseGradeShort(String str)
	{
		if (str.equalsIgnoreCase("s")) return 5;
		if (str.equalsIgnoreCase("a")) return 4;
		if (str.equalsIgnoreCase("b")) return 3;
		if (str.equalsIgnoreCase("c")) return 2;
		if (str.equalsIgnoreCase("d")) return 1;
		if (str.equalsIgnoreCase("ng") || str.equalsIgnoreCase("none")) return 0;
		if (str.equalsIgnoreCase("all")) return -1;
		return -2;
	}

	private void showRecipeList(Player player, int page, int gradeFilter, int levelFilter, String query)
	{
		final List<RecipeEntry> results = RecipeSearchEngine.getInstance().search(gradeFilter, levelFilter, query);

		int pageLimit = Math.clamp(ConfigServer.FIND_RECIPE_PAGE_LIMIT, 5, 10);
		final Pagination<RecipeEntry> list = new Pagination<>(results.stream(), page, pageLimit);

		list.append("<html><body>");
		list.append("<center>");

		// Header & Search Box
		list.append("<table width=280 bgcolor=000000 cellpadding=2 cellspacing=0><tr>");
		list.append("<td width=150 align=center><edit var=\"srch\" width=140 height=15></td>");
		list.append("<td width=65 align=center><button value=Buscar action=\"bypass voiced_recipe 1 ", gradeFilter, " ", levelFilter, " $srch\" width=60 height=20 back=L2UI_CH3.smallbutton2_down fore=L2UI_CH3.smallbutton2></td>");
		list.append("<td width=65 align=center><button value=Limpar action=\"bypass voiced_recipe 1 -1 0\" width=60 height=20 back=L2UI_CH3.smallbutton2_down fore=L2UI_CH3.smallbutton2></td>");
		list.append("</tr></table>");

		// Grade Filters row: [ALL] [NG] [D] [C] [B] [A] [S]
		list.append("<img height=2>");
		list.append("<table width=280 bgcolor=181818 cellpadding=0 cellspacing=1><tr>");
		list.append("<td width=40 align=center>", (gradeFilter == -1 ? "<font color=LEVEL>[ALL]</font>" : "<a action=\"bypass voiced_recipe 1 -1 " + levelFilter + " " + query + "\">ALL</a>"), "</td>");
		for (int g = 0; g < GRADE_NAMES.length; g++)
		{
			String gName = GRADE_NAMES[g];
			list.append("<td width=40 align=center>", (gradeFilter == g ? "<font color=LEVEL>[" + gName + "]</font>" : "<a action=\"bypass voiced_recipe 1 " + g + " " + levelFilter + " " + query + "\">" + gName + "</a>"), "</td>");
		}
		list.append("</tr></table>");

		// Craft Skill Level Filters row: [All Lv] [1] [2] [3] [4] [5] [6] [7] [8] [9]
		list.append("<table width=280 bgcolor=101010 cellpadding=0 cellspacing=0><tr>");
		list.append("<td width=46 align=center>", (levelFilter == 0 ? "<font color=LEVEL>[AllLv]</font>" : "<a action=\"bypass voiced_recipe 1 " + gradeFilter + " 0 " + query + "\">AllLv</a>"), "</td>");
		for (int lvl = 1; lvl <= 9; lvl++)
		{
			list.append("<td width=26 align=center>", (levelFilter == lvl ? "<font color=LEVEL>" + lvl + "</font>" : "<a action=\"bypass voiced_recipe 1 " + gradeFilter + " " + lvl + " " + query + "\">" + lvl + "</a>"), "</td>");
		}
		list.append("</tr></table>");
		list.append("<img src=L2UI.SquareGray width=280 height=1>");

		if (list.isEmpty())
		{
			list.append("<br><br><font color=B09878>Nenhum recipe encontrado para o filtro selecionado.</font><br>");
		}
		else
		{
			for (RecipeEntry entry : list)
			{
				List<RecipeDropHolder> drops = RecipeDropManager.getInstance().getDropsForRecipe(entry.recipeItemId());
				int mobCount = drops.size();

				list.append(((list.indexOf(entry) % 2) == 0 ? "<table width=280 height=34 bgcolor=000000 cellpadding=1 cellspacing=0><tr>" : "<table width=280 height=34 cellpadding=1 cellspacing=0><tr>"));
				list.append("<td width=36 height=34 align=center><table bgcolor=FFFFFF cellpadding=4 cellspacing=-5><tr><td><button width=32 height=32 back=\"", entry.icon(), "\" fore=\"", entry.icon(), "\"></td></tr></table></td>");

				String trimmedName = StringUtil.trimAndDress(entry.productName(), 22);
				String gradeName = entry.grade() != CrystalType.NONE ? entry.grade().name() : "NG";

				list.append("<td width=170><a action=\"bypass voiced_recipe_info ", entry.recipeItemId(), " 1 ", page, " ", gradeFilter, " ", levelFilter, " ", query, "\">", trimmedName, "</a><br1>");
				list.append("<font color=\"B09878\">Grade: </font><font color=LEVEL>", gradeName, "</font> <font color=\"B09878\">Lv:</font><font color=BDB76B>", entry.craftLevel(), "</font> ");

				if (mobCount > 0)
					list.append("<font color=00FF00>(", mobCount, " mobs)</font>");
				else
					list.append("<font color=808080>(Sem drop)</font>");

				list.append("</td>");

				if (mobCount > 0)
				{
					list.append("<td width=36 align=center><button action=\"bypass voiced_recipe_loc ", entry.recipeItemId(), " ", page, " ", gradeFilter, " ", levelFilter, " ", query, "\" width=32 height=28 back=L2UI_CH3.mapbutton_zoomin2 fore=L2UI_CH3.mapbutton_zoomin1 value=></td>");
				}
				else
				{
					list.append("<td width=36 align=center></td>");
				}

				list.append("<td width=38 align=center><button action=\"bypass voiced_recipe_info ", entry.recipeItemId(), " 1 ", page, " ", gradeFilter, " ", levelFilter, " ", query, "\" width=34 height=22 back=L2UI_CH3.smallbutton2_down fore=L2UI_CH3.smallbutton2 value=Info></td>");
				list.append("</tr></table><img src=L2UI.SquareGray width=280 height=1>");
			}
		}

		list.generateSpace(24);

		String pageBypass = "bypass voiced_recipe %page% " + gradeFilter + " " + levelFilter + " " + query;
		list.generatePages(pageBypass);

		list.append("<table width=280 cellpadding=2><tr>");
		list.append("<td align=center><a action=\"bypass voiced_recipe_clearloc ", page, " ", gradeFilter, " ", levelFilter, " ", query, "\">[Limpar Marcadores do Mapa]</a></td>");
		list.append("</tr></table>");

		list.append("</center></body></html>");

		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(list.getContent());
		player.sendPacket(html);
	}

	private void handleGoalLoc(Player player, StringTokenizer st)
	{
		if (!st.hasMoreTokens())
			return;

		int recipeItemId = Integer.parseInt(st.nextToken());
		int returnPage = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 1;
		int returnGrade = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : -1;
		int returnLevel = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
		String returnQuery = "";
		if (st.hasMoreTokens())
		{
			StringBuilder sb = new StringBuilder();
			while (st.hasMoreTokens())
			{
				sb.append(st.nextToken());
				if (st.hasMoreTokens())
					sb.append(" ");
			}
			returnQuery = sb.toString().trim();
		}

		List<RecipeDropHolder> drops = RecipeDropManager.getInstance().getDropsForRecipe(recipeItemId);
		if (drops.isEmpty())
		{
			player.sendMessage("Nenhum monstro conhecido dropa ou spoila este recipe.");
			showRecipeList(player, returnPage, returnGrade, returnLevel, returnQuery);
			return;
		}

		player.getRadarList().removeAllMarkers();

		int markersAdded = 0;
		int maxMarkers = Math.max(1, ConfigServer.FIND_RECIPE_MAX_RADAR_MARKERS);

		for (RecipeDropHolder holder : drops)
		{
			if (!holder.hasValidLocation())
				continue;

			player.getRadarList().addMarker(holder.locX(), holder.locY(), holder.locZ());
			markersAdded++;

			String dropType = holder.isSpoil() ? "Spoil" : "Drop";
			player.sendMessage("Goal Loc: " + holder.npcName() + " (Lv " + holder.npcLevel() + ") - " + dropType + ": " + CHANCE_FORMAT.format(holder.chancePercent()) + "%");

			if (markersAdded >= maxMarkers)
				break;
		}

		if (markersAdded > 0)
		{
			player.sendMessage(markersAdded + " marcador(es) de Goal Loc adicionado(s) ao seu minimapa/radar.");
		}
		else
		{
			player.sendMessage("Os monstros que dropam esta receita nao possuem spawn fixo catalogado.");
		}

		showRecipeList(player, returnPage, returnGrade, returnLevel, returnQuery);
	}

	private void handleSingleMobLoc(Player player, StringTokenizer st)
	{
		if (!st.hasMoreTokens())
			return;

		int recipeItemId = Integer.parseInt(st.nextToken());
		int npcId = Integer.parseInt(st.nextToken());

		List<RecipeDropHolder> drops = RecipeDropManager.getInstance().getDropsForRecipe(recipeItemId);
		RecipeDropHolder found = null;
		for (RecipeDropHolder holder : drops)
		{
			if (holder.npcId() == npcId)
			{
				found = holder;
				break;
			}
		}

		if (found == null)
		{
			List<RecipeDropHolder> itemDrops = RecipeDropManager.getInstance().getDropsForItem(recipeItemId);
			for (RecipeDropHolder holder : itemDrops)
			{
				if (holder.npcId() == npcId)
				{
					found = holder;
					break;
				}
			}
		}

		if (found != null && found.hasValidLocation())
		{
			player.getRadarList().removeAllMarkers();
			player.getRadarList().addMarker(found.locX(), found.locY(), found.locZ());
			player.sendMessage("Goal Loc marcado: " + found.npcName() + " (Lv " + found.npcLevel() + ")");
		}
		else
		{
			player.sendMessage("Nao foi possivel obter as coordenadas deste monstro.");
		}
	}

	private void handleClearLoc(Player player, StringTokenizer st)
	{
		player.getRadarList().removeAllMarkers();
		player.sendMessage("Marcadores do minimapa/radar foram limpos.");

		int page = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 1;
		int grade = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : -1;
		int level = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
		String query = "";
		if (st.hasMoreTokens())
		{
			StringBuilder sb = new StringBuilder();
			while (st.hasMoreTokens())
			{
				sb.append(st.nextToken());
				if (st.hasMoreTokens())
					sb.append(" ");
			}
			query = sb.toString().trim();
		}

		showRecipeList(player, page, grade, level, query);
	}

	private void handleRecipeInfo(Player player, StringTokenizer st)
	{
		if (!st.hasMoreTokens()) return;

		int recipeItemId = Integer.parseInt(st.nextToken());
		int mobPage = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 1;
		int returnPage = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 1;
		int returnGrade = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : -1;
		int returnLevel = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
		String returnQuery = "";
		if (st.hasMoreTokens())
		{
			StringBuilder sb = new StringBuilder();
			while (st.hasMoreTokens())
			{
				sb.append(st.nextToken());
				if (st.hasMoreTokens()) sb.append(" ");
			}
			returnQuery = sb.toString().trim();
		}

		Recipe recipe = RecipeData.getInstance().getRecipeByItemId(recipeItemId);
		RecipeEntry entry = RecipeSearchEngine.getInstance().getEntryByRecipeItemId(recipeItemId);
		if (recipe == null || entry == null)
		{
			player.sendMessage("Detalhes da receita indisponiveis.");
			showRecipeList(player, returnPage, returnGrade, returnLevel, returnQuery);
			return;
		}

		final StringBuilder sb = new StringBuilder(4096);
		sb.append("<html><body><center>");

		// Header
		sb.append("<table width=280 bgcolor=000000 cellpadding=2 cellspacing=0><tr>");
		sb.append("<td width=36 align=center><table bgcolor=FFFFFF cellpadding=4 cellspacing=-5><tr><td><button width=32 height=32 back=\"").append(entry.icon()).append("\" fore=\"").append(entry.icon()).append("\"></td></tr></table></td>");
		sb.append("<td width=244><font color=LEVEL>").append(entry.productName()).append("</font><br1>");
		sb.append("<font color=B09878>Grade:</font> <font color=FFFFFF>").append(entry.grade().name()).append("</font> | <font color=B09878>Lv Craft:</font> <font color=FFFFFF>").append(entry.craftLevel()).append("</font> | <font color=B09878>Taxa:</font> <font color=00FF00>").append(entry.successRate()).append("%</font></td>");
		sb.append("</tr></table><img src=L2UI.SquareGray width=280 height=1>");

		// Materials Section
		sb.append("<table width=280 bgcolor=151515 cellpadding=1><tr><td><font color=B09878>Materiais (clique para ver drops/craft):</font></td></tr></table>");
		sb.append("<table width=280 cellpadding=1 cellspacing=0>");
		for (IntIntHolder mat : recipe.materials())
		{
			Item matTemplate = ItemData.getInstance().getTemplate(mat.getId());
			String matName = matTemplate != null ? matTemplate.getName() : ("Item #" + mat.getId());
			Recipe craftRecipe = RecipeDropManager.getInstance().getRecipeForProduct(mat.getId());
			String craftIndicator = craftRecipe != null ? " <font color=00FF00>[Craft]</font>" : "";

			sb.append("<tr><td width=200><a action=\"bypass voiced_recipe_mat_info ").append(mat.getId()).append(" 1 ").append(recipeItemId).append(" ").append(returnPage).append(" ").append(returnGrade).append(" ").append(returnLevel).append(" ").append(returnQuery).append("\">").append(StringUtil.trimAndDress(matName, 22)).append("</a>").append(craftIndicator).append("</td><td width=80 align=right><font color=BDB76B>x").append(mat.getValue()).append("</font></td></tr>");
		}
		sb.append("</table><img src=L2UI.SquareGray width=280 height=1>");

		// Drop / Spoil Monsters Section (Paginated)
		List<RecipeDropHolder> drops = RecipeDropManager.getInstance().getDropsForRecipe(recipeItemId);
		sb.append("<table width=280 bgcolor=151515 cellpadding=1><tr><td width=180><font color=B09878>Monstros que Dropam/Spoilam:</font></td>");
		if (!drops.isEmpty())
		{
			sb.append("<td width=100 align=right><a action=\"bypass voiced_recipe_loc ").append(recipeItemId).append(" ").append(returnPage).append(" ").append(returnGrade).append(" ").append(returnLevel).append(" ").append(returnQuery).append("\">").append("<font color=00FF00>[Marcar Todos]</font></a></td>");
		}
		sb.append("</tr></table>");

		if (drops.isEmpty())
		{
			sb.append("<br><font color=808080>Esta receita nao cai de nenhum monstro catalogado.</font><br>");
		}
		else
		{
			int pageSize = Math.clamp(ConfigServer.FIND_RECIPE_MOB_LIST_PAGE_LIMIT, 4, 8);
			int totalMobPages = (drops.size() + pageSize - 1) / pageSize;
			mobPage = Math.clamp(mobPage, 1, Math.max(1, totalMobPages));
			int fromIndex = (mobPage - 1) * pageSize;
			int toIndex = Math.min(fromIndex + pageSize, drops.size());

			sb.append("<table width=280 cellpadding=1 cellspacing=0>");
			for (int i = fromIndex; i < toIndex; i++)
			{
				RecipeDropHolder h = drops.get(i);
				String typeColor = h.isSpoil() ? "00FFFF" : "LEVEL";
				String typeLabel = h.isSpoil() ? "Spoil" : "Drop";

				sb.append("<tr><td width=140><a action=\"bypass voiced_recipe_mob_all_drops ").append(h.npcId()).append(" 1 r ").append(recipeItemId).append(" 0 ").append(returnPage).append(" ").append(returnGrade).append(" ").append(returnLevel).append(" ").append(returnQuery).append("\">").append(StringUtil.trimAndDress(h.npcName(), 16)).append("</a> (Lv ").append(h.npcLevel()).append(")</td>");
				sb.append("<td width=80><font color=").append(typeColor).append(">").append(typeLabel).append(": ").append(CHANCE_FORMAT.format(h.chancePercent())).append("%</font></td>");

				if (h.hasValidLocation())
				{
					sb.append("<td width=60 align=center><a action=\"bypass voiced_recipe_mob_loc ").append(recipeItemId).append(" ").append(h.npcId()).append("\">").append("<font color=00FF00>[Loc]</font></a></td>");
				}
				else
				{
					sb.append("<td width=60 align=center></td>");
				}
				sb.append("</tr>");
			}
			sb.append("</table>");

			if (totalMobPages > 1)
			{
				sb.append("<table width=280 bgcolor=000000 cellpadding=1 cellspacing=0><tr>");
				sb.append("<td width=50 align=center>");
				if (mobPage > 1)
					sb.append("<a action=\"bypass voiced_recipe_info ").append(recipeItemId).append(" ").append(mobPage - 1).append(" ").append(returnPage).append(" ").append(returnGrade).append(" ").append(returnLevel).append(" ").append(returnQuery).append("\">").append("&lt; Ant</a>");
				sb.append("</td>");
				sb.append("<td width=180 align=center><font color=B09878>Monstros: </font><font color=LEVEL>").append(mobPage).append("/").append(totalMobPages).append("</font></td>");
				sb.append("<td width=50 align=center>");
				if (mobPage < totalMobPages)
					sb.append("<a action=\"bypass voiced_recipe_info ").append(recipeItemId).append(" ").append(mobPage + 1).append(" ").append(returnPage).append(" ").append(returnGrade).append(" ").append(returnLevel).append(" ").append(returnQuery).append("\">").append("Prox &gt;</a>");
				sb.append("</td>");
				sb.append("</tr></table>");
			}
		}

		sb.append("<img src=L2UI.SquareGray width=280 height=1>");
		sb.append("<br><button value=Voltar action=\"bypass voiced_recipe ").append(returnPage).append(" ").append(returnGrade).append(" ").append(returnLevel).append(" ").append(returnQuery).append("\" width=80 height=22 back=L2UI_CH3.smallbutton2_down fore=L2UI_CH3.smallbutton2>");
		sb.append("</center></body></html>");

		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}

	private void handleMaterialLoc(Player player, StringTokenizer st)
	{
		if (!st.hasMoreTokens()) return;

		int materialItemId = Integer.parseInt(st.nextToken());
		List<RecipeDropHolder> drops = RecipeDropManager.getInstance().getDropsForItem(materialItemId);
		if (drops.isEmpty())
		{
			player.sendMessage("Nenhum monstro conhecido dropa ou spoila este item.");
			return;
		}

		player.getRadarList().removeAllMarkers();
		int markersAdded = 0;
		int maxMarkers = Math.max(1, ConfigServer.FIND_RECIPE_MAX_RADAR_MARKERS);
		for (RecipeDropHolder holder : drops)
		{
			if (!holder.hasValidLocation()) continue;

			player.getRadarList().addMarker(holder.locX(), holder.locY(), holder.locZ());
			markersAdded++;
			String dropType = holder.isSpoil() ? "Spoil" : "Drop";
			player.sendMessage("Goal Loc Material: " + holder.npcName() + " (Lv " + holder.npcLevel() + ") - " + dropType + ": " + CHANCE_FORMAT.format(holder.chancePercent()) + "%");
			if (markersAdded >= maxMarkers) break;
		}

		if (markersAdded > 0)
		{
			player.sendMessage(markersAdded + " marcador(es) adicionado(s) ao minimapa para este material.");
		}
		else
		{
			player.sendMessage("Os monstros que dropam este material nao possuem spawn fixo catalogado.");
		}
	}

	private void handleMaterialInfo(Player player, StringTokenizer st)
	{
		if (!st.hasMoreTokens()) return;

		int materialItemId = Integer.parseInt(st.nextToken());
		int mobPage = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 1;
		int parentRecipeItemId = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
		int returnPage = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 1;
		int returnGrade = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : -1;
		int returnLevel = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
		String returnQuery = "";
		if (st.hasMoreTokens())
		{
			StringBuilder sb = new StringBuilder();
			while (st.hasMoreTokens())
			{
				sb.append(st.nextToken());
				if (st.hasMoreTokens()) sb.append(" ");
			}
			returnQuery = sb.toString().trim();
		}

		Item matTemplate = ItemData.getInstance().getTemplate(materialItemId);
		if (matTemplate == null)
		{
			player.sendMessage("Material nao encontrado.");
			return;
		}

		final StringBuilder sb = new StringBuilder(4096);
		sb.append("<html><body><center>");

		// Header with item icon and name
		sb.append("<table width=280 bgcolor=000000 cellpadding=2 cellspacing=0><tr>");
		sb.append("<td width=36 align=center><table bgcolor=FFFFFF cellpadding=4 cellspacing=-5><tr><td><button width=32 height=32 back=\"").append(matTemplate.getIcon()).append("\" fore=\"").append(matTemplate.getIcon()).append("\"></td></tr></table></td>");
		sb.append("<td width=244><font color=LEVEL>").append(matTemplate.getName()).append("</font><br1>");
		if(player.isGM())
		{
			sb.append("<font color=B09878>Item ID: </font><font color=FFFFFF>").append(materialItemId).append("</font></td>");
		}else
		{
			sb.append("</td>");
		}
		sb.append("</tr></table><img src=L2UI.SquareGray width=280 height=1>");

		// Check if this material is craftable with a recipe
		Recipe craftRecipe = RecipeDropManager.getInstance().getRecipeForProduct(materialItemId);
		if (craftRecipe != null)
		{
			sb.append("<table width=280 bgcolor=151515 cellpadding=2 cellspacing=0><tr>");
			sb.append("<td width=190><font color=00FF00>Possui Receita de Craft!</font><br1><font color=B09878>Level ").append(craftRecipe.level()).append(" (Taxa ").append(craftRecipe.successRate()).append("%)").append("</font></td>");
			sb.append("<td width=90 align=center><button value=Recipe action=\"bypass voiced_recipe_info ").append(craftRecipe.recipeId()).append(" 1 ").append(returnPage).append(" ").append(returnGrade).append(" ").append(returnLevel).append(" ").append(returnQuery).append("\" width=80 height=22 back=L2UI_CH3.smallbutton2_down fore=L2UI_CH3.smallbutton2></td>");
			sb.append("</tr></table><img src=L2UI.SquareGray width=280 height=1>");
		}

		// Monsters that drop/spoil this material (Paginated)
		List<RecipeDropHolder> drops = RecipeDropManager.getInstance().getDropsForItem(materialItemId);
		sb.append("<table width=280 bgcolor=151515 cellpadding=1><tr><td width=180><font color=B09878>Monstros que Dropam/Spoilam:</font></td>");
		if (!drops.isEmpty())
		{
			sb.append("<td width=100 align=right><a action=\"bypass voiced_recipe_mat_loc ").append(materialItemId).append("\">").append("<font color=00FF00>[Marcar Todos]</font></a></td>");
		}
		sb.append("</tr></table>");

		if (drops.isEmpty())
		{
			sb.append("<br><font color=808080>Este item nao cai de nenhum monstro catalogado.</font><br>");
		}
		else
		{
			int pageSize = Math.clamp(ConfigServer.FIND_RECIPE_MOB_LIST_PAGE_LIMIT, 4, 8);
			int totalMobPages = (drops.size() + pageSize - 1) / pageSize;
			mobPage = Math.clamp(mobPage, 1, Math.max(1, totalMobPages));
			int fromIndex = (mobPage - 1) * pageSize;
			int toIndex = Math.min(fromIndex + pageSize, drops.size());

			sb.append("<table width=280 cellpadding=1 cellspacing=0>");
			for (int i = fromIndex; i < toIndex; i++)
			{
				RecipeDropHolder h = drops.get(i);
				String typeColor = h.isSpoil() ? "00FFFF" : "LEVEL";
				String typeLabel = h.isSpoil() ? "Spoil" : "Drop";

				sb.append("<tr><td width=140><a action=\"bypass voiced_recipe_mob_all_drops ").append(h.npcId()).append(" 1 m ").append(materialItemId).append(" ").append(parentRecipeItemId).append(" ").append(returnPage).append(" ").append(returnGrade).append(" ").append(returnLevel).append(" ").append(returnQuery).append("\">").append(StringUtil.trimAndDress(h.npcName(), 16)).append("</a> (Lv ").append(h.npcLevel()).append(")</td>");
				sb.append("<td width=80><font color=").append(typeColor).append(">").append(typeLabel).append(": ").append(CHANCE_FORMAT.format(h.chancePercent())).append("%</font></td>");

				if (h.hasValidLocation())
				{
					sb.append("<td width=60 align=center><a action=\"bypass voiced_recipe_mob_loc ").append(materialItemId).append(" ").append(h.npcId()).append("\">").append("<font color=00FF00>[Loc]</font></a></td>");
				}
				else
				{
					sb.append("<td width=60 align=center></td>");
				}
				sb.append("</tr>");
			}
			sb.append("</table>");

			if (totalMobPages > 1)
			{
				sb.append("<table width=280 bgcolor=000000 cellpadding=1 cellspacing=0><tr>");
				sb.append("<td width=50 align=center>");
				if (mobPage > 1)
					sb.append("<a action=\"bypass voiced_recipe_mat_info ").append(materialItemId).append(" ").append(mobPage - 1).append(" ").append(parentRecipeItemId).append(" ").append(returnPage).append(" ").append(returnGrade).append(" ").append(returnLevel).append(" ").append(returnQuery).append("\">").append("&lt; Ant</a>");
				sb.append("</td>");
				sb.append("<td width=180 align=center><font color=B09878>Monstros: </font><font color=LEVEL>").append(mobPage).append("/").append(totalMobPages).append("</font></td>");
				sb.append("<td width=50 align=center>");
				if (mobPage < totalMobPages)
					sb.append("<a action=\"bypass voiced_recipe_mat_info ").append(materialItemId).append(" ").append(mobPage + 1).append(" ").append(parentRecipeItemId).append(" ").append(returnPage).append(" ").append(returnGrade).append(" ").append(returnLevel).append(" ").append(returnQuery).append("\">").append("Prox &gt;</a>");
				sb.append("</td>");
				sb.append("</tr></table>");
			}
		}

		sb.append("<img src=L2UI.SquareGray width=280 height=1>");
		if (parentRecipeItemId > 0)
		{
			sb.append("<br><button value=Voltar action=\"bypass voiced_recipe_info ").append(parentRecipeItemId).append(" 1 ").append(returnPage).append(" ").append(returnGrade).append(" ").append(returnLevel).append(" ").append(returnQuery).append("\" width=80 height=22 back=L2UI_CH3.smallbutton2_down fore=L2UI_CH3.smallbutton2>");
		}
		else
		{
			sb.append("<br><button value=Voltar action=\"bypass voiced_recipe ").append(returnPage).append(" ").append(returnGrade).append(" ").append(returnLevel).append(" ").append(returnQuery).append("\" width=80 height=22 back=L2UI_CH3.smallbutton2_down fore=L2UI_CH3.smallbutton2>");
		}
		sb.append("</center></body></html>");

		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}

	private void handleMobAllDrops(Player player, StringTokenizer st)
	{
		if (!st.hasMoreTokens()) return;

		int npcId = Integer.parseInt(st.nextToken());
		int page = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 1;
		String returnType = st.hasMoreTokens() ? st.nextToken() : "r";
		int returnId = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
		int secondaryReturnId = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
		int returnPage = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 1;
		int returnGrade = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : -1;
		int returnLevel = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
		String returnQuery = "";
		if (st.hasMoreTokens())
		{
			StringBuilder sb = new StringBuilder();
			while (st.hasMoreTokens())
			{
				sb.append(st.nextToken());
				if (st.hasMoreTokens()) sb.append(" ");
			}
			returnQuery = sb.toString().trim();
		}

		NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
		if (template == null)
		{
			player.sendMessage("Monstro não encontrado.");
			return;
		}

		RecipeDropHolder testHolder = new RecipeDropHolder(npcId, template.getName(), template.getLevel(), false, 0, 0, 0, 0, 0, 0);
		boolean hasLoc = testHolder.hasValidLocation();

		List<DropData> allDrops = new ArrayList<>();
		if (template.getDropData() != null)
		{
			for (DropCategory cat : template.getDropData())
			{
				if (cat.getDropType() == DropType.HERB) continue;
				for (DropData d : cat.getAllDrops())
				{
					d.setCategoryType(cat.getDropType());
					allDrops.add(d);
				}
			}
		}

		int pageSize = Math.clamp(ConfigServer.FIND_RECIPE_MOB_DROP_PAGE_LIMIT, 5, 8);
		Pagination<DropData> list = new Pagination<>(allDrops.stream(), page, pageSize);

		list.append("<html><body><center>");

		// Header with monster name, level and location button (only if location is valid)
		list.append("<table width=280 bgcolor=000000 cellpadding=2 cellspacing=0><tr>");
		list.append("<td width=220><font color=LEVEL>", template.getName(), "</font> (Lv ", template.getLevel(), ")<br1>");
		list.append("<font color=B09878>HP: </font><font color=FFFFFF>", (int) template.getBaseHpMax(template.getLevel()), "</font> | <font color=B09878>Drops: </font><font color=00FF00>", allDrops.size(), "</font></td>");
		if (hasLoc)
		{
			list.append("<td width=60 align=center><button action=\"bypass voiced_recipe_mob_loc ", returnId, " ", npcId, "\" width=55 height=22 back=L2UI_CH3.mapbutton_zoomin2 fore=L2UI_CH3.mapbutton_zoomin1 value=></td>");
		}
		else
		{
			list.append("<td width=60 align=center></td>");
		}
		list.append("</tr></table><img src=L2UI.SquareGray width=280 height=1>");

		if (list.isEmpty())
		{
			list.append("<br><font color=808080>Este monstro não possui drops cadastrados.</font><br>");
		}
		else
		{
			for (DropData d : list)
			{
				Item item = ItemData.getInstance().getTemplate(d.getItemId());
				String itemName = item != null ? item.getName() : ("Item #" + d.getItemId());
				String icon = item != null ? item.getIcon() : "icon.etc_recipe_white_i00";

				boolean isSpoil = (d.getCategoryType() == DropType.SPOIL);
				String typeColor = isSpoil ? "00FFFF" : "LEVEL";
				String typeLabel = isSpoil ? "Spoil" : "Drop";
				double chance = d.getChance() / 10000.0;

				Recipe dropRecipe = RecipeData.getInstance().getRecipeByItemId(d.getItemId());
				Recipe craftRecipe = RecipeDropManager.getInstance().getRecipeForProduct(d.getItemId());

				list.append(((list.indexOf(d) % 2) == 0 ? "<table width=280 height=32 bgcolor=000000 cellpadding=1 cellspacing=0><tr>" : "<table width=280 height=32 cellpadding=1 cellspacing=0><tr>"));
				list.append("<td width=34 align=center><table bgcolor=FFFFFF cellpadding=4 cellspacing=-5><tr><td><button width=30 height=30 back=\"", icon, "\" fore=\"", icon, "\"></td></tr></table></td>");

				list.append("<td width=180><font color=FFFFFF>", StringUtil.trimAndDress(itemName, 20), "</font><br1>");
				list.append("<font color=", typeColor, ">", typeLabel, ": ", CHANCE_FORMAT.format(chance), "%</font> <font color=BDB76B>(", d.getMinDrop(), "-", d.getMaxDrop(), ")</font></td>");

				if (dropRecipe != null)
				{
					list.append("<td width=66 align=center><button value=Receita action=\"bypass voiced_recipe_info ", dropRecipe.recipeId(), " 1 ", returnPage, " ", returnGrade, " ", returnLevel, " ", returnQuery, "\" width=60 height=20 back=L2UI_CH3.smallbutton2_down fore=L2UI_CH3.smallbutton2></td>");
				}
				else if (craftRecipe != null)
				{
					list.append("<td width=66 align=center><button value=Craft action=\"bypass voiced_recipe_info ", craftRecipe.recipeId(), " 1 ", returnPage, " ", returnGrade, " ", returnLevel, " ", returnQuery, "\" width=60 height=20 back=L2UI_CH3.smallbutton2_down fore=L2UI_CH3.smallbutton2></td>");
				}
				else
				{
					list.append("<td width=66 align=center><a action=\"bypass voiced_recipe_mat_info ", d.getItemId(), " 1 ", returnId, " ", returnPage, " ", returnGrade, " ", returnLevel, " ", returnQuery, "\">[Drops]</a></td>");
				}
				list.append("</tr></table><img src=L2UI.SquareGray width=280 height=1>");
			}
		}

		list.generateSpace(24);

		String navBypass = "bypass voiced_recipe_mob_all_drops " + npcId + " %page% " + returnType + " " + returnId + " " + secondaryReturnId + " " + returnPage + " " + returnGrade + " " + returnLevel + " " + returnQuery;
		list.generatePages(navBypass);

		// Return button with correct breadcrumb
		if ("m".equalsIgnoreCase(returnType) && returnId > 0)
		{
			list.append("<br><button value=Voltar action=\"bypass voiced_recipe_mat_info ", returnId, " 1 ", secondaryReturnId, " ", returnPage, " ", returnGrade, " ", returnLevel, " ", returnQuery, "\" width=80 height=22 back=L2UI_CH3.smallbutton2_down fore=L2UI_CH3.smallbutton2>");
		}
		else if (returnId > 0)
		{
			list.append("<br><button value=Voltar action=\"bypass voiced_recipe_info ", returnId, " 1 ", returnPage, " ", returnGrade, " ", returnLevel, " ", returnQuery, "\" width=80 height=22 back=L2UI_CH3.smallbutton2_down fore=L2UI_CH3.smallbutton2>");
		}
		else
		{
			list.append("<br><button value=Voltar action=\"bypass voiced_recipe ", returnPage, " ", returnGrade, " ", returnLevel, " ", returnQuery, "\" width=80 height=22 back=L2UI_CH3.smallbutton2_down fore=L2UI_CH3.smallbutton2>");
		}

		list.append("</center></body></html>");

		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(list.getContent());
		player.sendPacket(html);
	}

	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}
