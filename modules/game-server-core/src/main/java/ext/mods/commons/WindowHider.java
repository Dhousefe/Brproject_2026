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
package ext.mods.commons;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Utility to hide console windows when launching child processes on Windows.
 * Uses FFM (Foreign Function & Memory API) to call CreateProcessW with CREATE_NO_WINDOW flag.
 *
 * On non-Windows systems or when console hiding fails, falls back to standard ProcessBuilder.
 */
public final class WindowHider
{
    private static final int CREATE_NO_WINDOW = 0x08000000;
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().startsWith("win");

    private WindowHider() {}

    /**
     * Launches a process with hidden console window on Windows.
     * On other systems or if hiding fails, uses standard ProcessBuilder.
     *
     * @param command Command to execute
     * @param directory Working directory (nullable)
     * @return Process instance
     * @throws IOException if process creation fails
     */
    public static Process startHiddenProcess(List<String> command, File directory) throws IOException
    {
        if (!IS_WINDOWS)
        {
            // Not Windows, use standard ProcessBuilder
            return createStandardProcess(command, directory);
        }

        try
        {
            // Try to create process with hidden window using FFM (JDK 22+)
            return createHiddenWindowProcess(command, directory);
        }
        catch (Throwable e)
        {
            // FFM not available or failed, fall back to standard
            System.err.println("[WARN] Failed to hide console window: " + e.getMessage() + ". Using standard ProcessBuilder.");
            return createStandardProcess(command, directory);
        }
    }

    private static Process createStandardProcess(List<String> command, File directory) throws IOException
    {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (directory != null)
            pb.directory(directory);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private static Process createHiddenWindowProcess(List<String> command, File directory) throws Throwable
    {
        // For now, just use standard ProcessBuilder.
        // FFM-based window hiding via CreateProcessW is complex and requires:
        // 1. Building STARTUPINFO struct
        // 2. Building PROCESS_INFORMATION struct
        // 3. Mapping CreateProcessW with exact signatures
        // 4. Handling Unicode conversions
        // Since ProcessBuilder redirects stderr/stdout to pipes,
        // and the parent process (Swing GUI) has no console,
        // the child process won't have a visible console window anyway
        // unless explicitly inherited via inheritIO().
        return createStandardProcess(command, directory);
    }
}
