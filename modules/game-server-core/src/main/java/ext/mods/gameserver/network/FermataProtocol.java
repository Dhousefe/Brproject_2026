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
package ext.mods.gameserver.network;

/**
 * Constantes e utilitários compartilhados para extensões de protocolo do cliente Fermata no BrProject-2026.
 */
public final class FermataProtocol
{
	public static final int ENVELOPE_OPCODE = 0xfd;
	public static final int PROTOCOL_REVISION = 1;
	
	public static final int LINKED_ITEM_CHAT = 1 << 0;
	public static final int SERVER_WEATHER = 1 << 1;
	public static final int SUPPORTED_CAPABILITIES = LINKED_ITEM_CHAT | SERVER_WEATHER;
	
	private FermataProtocol()
	{
	}
	
	public static int negotiateCapabilities(int protocolRevision, int advertisedCapabilities)
	{
		return protocolRevision == PROTOCOL_REVISION ? (advertisedCapabilities & SUPPORTED_CAPABILITIES) : 0;
	}
}
