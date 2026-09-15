/*
 * Copyleft © 2024-2026 L2Brproject
 * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 * L2Brproject is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License.
 * L2Brproject is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package ext.mods.commons.gui;

import java.util.regex.Pattern;

/**
 * Utility to strip ANSI/VT100 escape sequences from text destined for Swing
 * components (JTextArea, JLabel, ...) which do not interpret them.
 *
 * <p>The JTextArea would otherwise display the raw bytes (ESC characters and
 * parameter numbers) producing illegible output such as "[96m" or "←[1m#←[0m".</p>
 *
 * <p>The filter keeps the actual text content (including UTF-8 multibyte chars)
 * and removes only the CSI/SGR control sequences like {@code \u001B[96m},
 * {@code \u001B[1;33m} and the resets {@code \u001B[0m}.</p>
 */
public final class AnsiFilter
{
    /**
     * Matches:
     *   - CSI sequences: ESC [ ... letter
     *   - OSC sequences: ESC ] ... (BEL or ESC \)
     *   - Two-byte ESC + single char (e.g. ESC c, ESC >)
     */
    private static final Pattern ANSI_PATTERN = Pattern.compile(
            "\u001B\\[[0-9;?]*[ -/]*[@-~]" +   // CSI ... final byte
            "|\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)" + // OSC
            "|\u001B[@-Z\\\\-_]"                  // Fe escape
    );

    private AnsiFilter() {}

    /**
     * Remove ANSI escape sequences from the supplied text. Safe to call with
     * {@code null} (returns empty string).
     *
     * @param text input that may contain ANSI codes
     * @return the cleaned string
     */
    public static String strip(String text)
    {
        if (text == null || text.isEmpty())
            return text == null ? "" : text;
        return ANSI_PATTERN.matcher(text).replaceAll("");
    }
}