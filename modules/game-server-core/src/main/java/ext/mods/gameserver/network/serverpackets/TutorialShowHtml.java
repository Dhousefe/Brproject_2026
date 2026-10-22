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
package ext.mods.gameserver.network.serverpackets;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.GameClient;

public final class TutorialShowHtml extends L2GameServerPacket
{
	private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	private final String _html;
	private final String _builderCaller;

	public TutorialShowHtml(String html)
	{
		_html = html;
		_builderCaller = resolveDynamicCaller();
	}

	private static String resolveDynamicCaller()
	{
		try
		{
			return STACK_WALKER.walk(frames -> frames
				.filter(f -> !f.getClassName().equals(TutorialShowHtml.class.getName())
						 && !f.getClassName().startsWith("java.lang.")
						 && !f.getClassName().startsWith("jdk.internal."))
				.findFirst()
				.map(f -> {
					final String className = f.getClassName();
					final int innerIdx = className.indexOf('$');
					final String baseName = (innerIdx != -1) ? className.substring(0, innerIdx) : className;
					final String path = baseName.replace('.', '/') + ".java";
					final int line = f.getLineNumber();
					return (line > 0) ? (path + ":" + line) : path;
				})
				.orElse("UnknownCaller.java"));
		}
		catch (Throwable t)
		{
			return "UnknownCaller.java";
		}
	}

	@Override
	public void runImpl()
	{
		final GameClient client = getClient();
		if (client == null)
			return;

		final Player player = client.getPlayer();
		if (player == null || !player.isGM())
			return;

		player.sendMessage("[JAVA HTML Builder]: " + _builderCaller);
	}

	@Override
	protected void writeImpl()
	{
		writeC(0xa0);
		writeS(_html);
	}
}