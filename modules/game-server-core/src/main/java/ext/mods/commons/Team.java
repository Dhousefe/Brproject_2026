/*
| Copyleft © 2024-2026 L2Brproject
| * This file is part of L2Brproject derived from aCis409/RusaCis3.8
| * L2Brproject is free software: you can redistribute it and/or modify it
| under the terms of the GNU General Public License as published by the
| Free Software Foundation, either version 3 of the License.
| * L2Brproject is distributed in the hope that it will be useful,
| but WITHOUT ANY WARRANTY; without even the implied warranty of
| MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
| General Public License for more details.
| * You should have received a copy of the GNU General Public License
| along with this program. If not, see <http://www.gnu.org/licenses/>.
| Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
| Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY,
| SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
| as a contribution for the forum L2JBrasil.com
*/
package ext.mods.commons;

public final class Team
{
	private static final String RESET = "\u001B[0m";
	private static final String BOLD = "\u001B[1m";
	private static final String BRIGHT_CYAN = "\u001B[96m";
	private static final String BRIGHT_YELLOW = "\u001B[93m";

	/** Largura total da moldura. */
	private static final int LOGO_WIDTH = 117;

	private static final String YELLOW_PREFIX = "::::::::::::::::::::::::::::::::::: ";


	private static final String BRAND_LINE =
		"::::::::::::::::::::::::::::::::::::::::::::::::::::::[ L2JBr ]::::::::::::::::::::::::::::::::::::::::::::::::::::::";

	/**
	 * Logo em pixel art no estilo "ANSI Shadow" (pyfiglet ansi_shadow).
	 * Cada caractere nao-branco (bloco cheio ou box drawing) e pintado em
	 * amarelo negrito; espacos ficam sem cor. Linhas mais curtas que
	 * LOGO_WIDTH sao centralizadas automaticamente em printAsciiLine.
	 */
	private static final String[] LOGO = {
		"██████╗ ██████╗ ██████╗ ██████╗  ██████╗      ██╗███████╗ ██████╗████████╗",
		"██╔══██╗██╔══██╗██╔══██╗██╔══██╗██╔═══██╗     ██║██╔════╝██╔════╝╚══██╔══╝",
		"██████╔╝██████╔╝██████╔╝██████╔╝██║   ██║     ██║█████╗  ██║        ██║   ",
		"██╔══██╗██╔══██╗██╔═══╝ ██╔══██╗██║   ██║██   ██║██╔══╝  ██║        ██║   ",
		"██████╔╝██║  ██║██║     ██║  ██║╚██████╔╝╚█████╔╝███████╗╚██████╗   ██║   ",
		"╚═════╝ ╚═╝  ╚═╝╚═╝     ╚═╝  ╚═╝ ╚═════╝  ╚════╝ ╚══════╝ ╚═════╝   ╚═╝   ",
	};

	private Team() {}

	public static void info()
	{
		infoGameServer();
	}

	public static void infoLoginServer()
	{
		printBanner("LOGIN SERVER");
	}

	public static void infoGameServer()
	{
		printBanner("GAME SERVER");
	}

	private static void printBanner(String serverName)
	{
		// Habilita cores ANSI no console (Windows cmd.exe). No-op em Linux/Mac.
		AnsiConsole.enable();

		printHeaderLine(BrProjectMeta.TEAM, true);

		printHeaderLine(serverName, false);

		for (String line : LOGO)
			printAsciiLine(line);

		System.out.println(BRIGHT_CYAN + BRAND_LINE + RESET);

		printYellowDotted(BrProjectMeta.DISTRIB_MODE);
		printYellowDotted(BrProjectMeta.BUILD_LINE);
		printYellowDotted(BrProjectMeta.CORE_LINE);

		System.out.println("");

		printHeaderLine(BrProjectMeta.BRAND, true);
		System.out.flush();
	}

	/**
	 * Calcula e imprime barras horizontais dinamicamente baseadas no LOGO_WIDTH.
	 * * @param text O texto a ser emoldurado "=[ text ]"
	 * @param rightAligned Se verdadeiro, alinha o texto na direita. Falso centraliza.
	 */
	private static void printHeaderLine(String text, boolean rightAligned)
	{
		if (text == null) text = "Unknown";

		String label = "=[ " + text + " ]";
		int dashCount = LOGO_WIDTH - label.length();
		if (dashCount < 0) dashCount = 0;

		StringBuilder sb = new StringBuilder();
		sb.append(BRIGHT_CYAN);

		if (rightAligned)
		{
			for (int i = 0; i < dashCount; i++) sb.append("-");
			sb.append("=[ ").append(BRIGHT_YELLOW).append(text).append(BRIGHT_CYAN).append(" ]");
		}
		else
		{
			int leftDashes = dashCount / 2;
			int rightDashes = dashCount - leftDashes;

			for (int i = 0; i < leftDashes; i++) sb.append("-");
			sb.append("=[ ").append(BRIGHT_YELLOW).append(text).append(BRIGHT_CYAN).append(" ]");
			for (int i = 0; i < rightDashes; i++) sb.append("-");
		}

		sb.append(RESET);
		System.out.println(sb.toString());
	}

	/**
	 * Pinta a linha em pixel art:
	 *  - blocos cheios (█) e box-drawing (╗╔╚╝═║) -> amarelo negrito
	 *  - espacos -> sem cor
	 *  - qualquer outro caractere -> ciano (nao acontece no ansi_shadow)
	 * Se a linha for menor que LOGO_WIDTH, centraliza.
	 */
	private static void printAsciiLine(String line)
	{
		StringBuilder out = new StringBuilder();
		out.append(BRIGHT_CYAN);
		if (line.length() < LOGO_WIDTH)
		{
			int pad = (LOGO_WIDTH - line.length()) / 2;
			for (int i = 0; i < pad; i++) out.append(' ');
		}
		out.append(paintLogoLine(line));
		out.append(RESET);
		System.out.println(out.toString());
	}

	private static String paintLogoLine(String line)
	{
		StringBuilder sb = new StringBuilder(line.length() + 16);
		boolean inYellow = false;
		for (int i = 0; i < line.length(); i++)
		{
			char c = line.charAt(i);
			boolean isBlock = c == '\u2588' || (c >= '\u2500' && c <= '\u257F'); // block + box drawing
			if (isBlock && !inYellow)
			{
				sb.append(BRIGHT_YELLOW).append(BOLD);
				inYellow = true;
			}
			else if (!isBlock && inYellow)
			{
				sb.append(RESET);
				inYellow = false;
			}
			sb.append(c);
		}
		if (inYellow) sb.append(RESET);
		return sb.toString();
	}

	private static void printYellowDotted(String text)
	{
		if (text != null && !text.trim().isEmpty()) {
			System.out.println(BRIGHT_CYAN + YELLOW_PREFIX.replace(" ", "") + " " + BRIGHT_YELLOW + text + RESET);
		}
	}
}