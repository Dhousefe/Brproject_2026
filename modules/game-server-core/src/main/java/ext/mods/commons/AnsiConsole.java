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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Habilita o processamento de sequencias ANSI (cores, negrito, etc.) no
 * console do Windows (cmd.exe / Windows Terminal) sem depender de JNA,
 * DLL externa ou alteracoes no registro.
 *
 * <p>O Team.java imprime o banner usando ANSI (\u001B[96m, \u001B[93m, ...).
 * No Windows, o cmd.exe so interpreta esses codigos se o bit
 * ENABLE_VIRTUAL_TERMINAL_PROCESSING (0x4) estiver setado no ConsoleMode
 * do handle STDOUT. Esta classe ativa esse bit via FFM (Foreign Function
 * & Memory API, estavel desde JDK 22).</p>
 *
 * <p>Em sistemas nao-Windows (Linux, macOS, WSL, Git Bash) ou quando
 * rodando em modo headless (sem console), ela faz no-op silencioso.</p>
 */
public final class AnsiConsole
{
	private static final int STD_OUTPUT_HANDLE = -11;
	private static final int STD_ERROR_HANDLE  = -12;
	private static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;
	private static final int ENABLE_VIRTUAL_TERMINAL_INPUT       = 0x0200;

	private AnsiConsole() {}

	/**
	 * Tenta ativar VT no STDOUT e STDERR atuais. Falhas sao ignoradas
	 * silenciosamente - em Linux/Mac/headless simplesmente nao faz nada.
	 *
	 * @return true se o bit VT foi efetivamente ativado (ou ja estava),
	 *         false se nao foi possivel (sem console, kernel32 indisponivel, etc.)
	 */
	public static boolean enable()
	{
		String os = System.getProperty("os.name", "");
		if (os == null || !os.toLowerCase().startsWith("win"))
			return false;

		boolean ok = false;
		try { ok |= applyToHandle(STD_OUTPUT_HANDLE); } catch (Throwable ignored) {}
		try { ok |= applyToHandle(STD_ERROR_HANDLE);  } catch (Throwable ignored) {}
		return ok;
	}

	private static boolean applyToHandle(int stdHandle) throws Throwable
	{
		Linker linker = Linker.nativeLinker();
		SymbolLookup kernel32 = linker.defaultLookup();

		MemorySegment getStdHandleAddr = kernel32.find("GetStdHandle")
				.orElseThrow(() -> new IllegalStateException("GetStdHandle nao encontrada"));
		MemorySegment getConsoleModeAddr = kernel32.find("GetConsoleMode")
				.orElseThrow(() -> new IllegalStateException("GetConsoleMode nao encontrada"));
		MemorySegment setConsoleModeAddr = kernel32.find("SetConsoleMode")
				.orElseThrow(() -> new IllegalStateException("SetConsoleMode nao encontrada"));

		// GetStdHandle(DWORD nStdHandle) -> HANDLE
		MethodHandle getStdHandle = linker.downcallHandle(getStdHandleAddr,
				FunctionDescriptor.of(ADDRESS, JAVA_INT));
		// GetConsoleMode(HANDLE hConsoleHandle, LPDWORD lpMode) -> BOOL
		MethodHandle getConsoleMode = linker.downcallHandle(getConsoleModeAddr,
				FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
		// SetConsoleMode(HANDLE hConsoleHandle, DWORD dwMode) -> BOOL
		MethodHandle setConsoleMode = linker.downcallHandle(setConsoleModeAddr,
				FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

		MemorySegment handle = (MemorySegment) getStdHandle.invokeExact(stdHandle);
		if (handle.address() == 0L)
			return false; // sem console

		try (Arena arena = Arena.ofConfined())
		{
			MemorySegment modePtr = arena.allocate(MemoryLayout.sequenceLayout(1, JAVA_INT));
			int getResult = (int) getConsoleMode.invokeExact(handle, modePtr);
			if (getResult == 0)
				return false; // sem console (pipe, redirecionamento, etc.)

			int mode = modePtr.get(JAVA_INT, 0L);
			int newMode = mode | ENABLE_VIRTUAL_TERMINAL_PROCESSING | ENABLE_VIRTUAL_TERMINAL_INPUT;
			if (mode == newMode)
				return true;

			int setResult = (int) setConsoleMode.invokeExact(handle, newMode);
			return setResult != 0;
		}
	}
}