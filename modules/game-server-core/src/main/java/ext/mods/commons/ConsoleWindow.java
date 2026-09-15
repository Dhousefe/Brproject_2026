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
*/
package ext.mods.commons;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Utilitario para ocultar/mostrar a janela do console (cmd.exe) no Windows.
 *
 * <p>Usa a FFM API (JDK 22+) para chamar {@code GetConsoleWindow()} e
 * {@code ShowWindow(hwnd, SW_HIDE/SW_SHOW)} do kernel32/user32.</p>
 *
 * <p>Em Linux/Mac ou headless, todos os metodos fazem no-op.</p>
 */
public final class ConsoleWindow
{
	private static final int SW_HIDE = 0;
	private static final int SW_SHOW = 5;

	private ConsoleWindow() {}

	/**
	 * Oculta a janela do console apos {@code delayMs} milissegundos,
	 * em uma thread daemon (nao bloqueia o caller).
	 *
	 * @param delayMs tempo em milissegundos antes de ocultar (ex: 6000 = 6s)
	 */
	public static void hideAfter(long delayMs)
	{
		if (!isWindows()) return;

		Thread t = new Thread(() -> {
			try
			{
				Thread.sleep(delayMs);
				hide();
			}
			catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
			catch (Throwable ignored) {}
		}, "ConsoleWindow-hide");
		t.setDaemon(true);
		t.start();
	}

	/** Oculta a janela do console imediatamente. */
	public static void hide()
	{
		showWindow(SW_HIDE);
	}

	/** Mostra a janela do console (caso tenha sido ocultada). */
	public static void show()
	{
		showWindow(SW_SHOW);
	}

	private static void showWindow(int nCmdShow)
	{
		if (!isWindows()) return;

		try
		{
			Linker linker = Linker.nativeLinker();
			SymbolLookup lookup = linker.defaultLookup();

			// HWND GetConsoleWindow() — kernel32.dll
			MethodHandle getConsoleWindow = linker.downcallHandle(
				lookup.find("GetConsoleWindow").orElseThrow(),
				FunctionDescriptor.of(ADDRESS));

			// BOOL ShowWindow(HWND hWnd, int nCmdShow) — user32.dll
			MethodHandle showWindow = linker.downcallHandle(
				lookup.find("ShowWindow").orElseThrow(),
				FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

			MemorySegment hwnd = (MemorySegment) getConsoleWindow.invokeExact();
			if (hwnd.address() == 0L)
				return; // sem janela de console

			showWindow.invokeExact(hwnd, nCmdShow);
		}
		catch (Throwable ignored) {}
	}

	private static boolean isWindows()
	{
		String os = System.getProperty("os.name", "");
		return os != null && os.toLowerCase().startsWith("win");
	}
}