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
package ext.mods.gameserver.network.clientpackets.fermata;

import ext.mods.gameserver.network.GameClient;
import ext.mods.gameserver.network.GameClient.GameClientState;
import ext.mods.gameserver.network.clientpackets.L2GameClientPacket;

/**
 * Pacote ClientCapabilities (C->S 0xFD 0x0000) da extensão de protocolo Fermata.
 * Anuncia as famílias de pacotes do servidor que o cliente aceita com segurança.
 * Enviado pelo cliente imediatamente antes de EnterWorld no estado ENTERING.
 */
public final class ClientCapabilities extends L2GameClientPacket
{
	public static final int PAYLOAD_LENGTH = Integer.BYTES * 2;
	
	private int _protocolRevision;
	private int _capabilityFlags;
	
	@Override
	protected void readImpl()
	{
		if (_buf.remaining() != PAYLOAD_LENGTH)
		{
			getClient().closeNow();
			return;
		}
		
		_protocolRevision = readD();
		_capabilityFlags = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final GameClient client = getClient();
		if (client == null)
			return;
		
		if (client.getState() != GameClientState.ENTERING)
		{
			client.closeNow();
			return;
		}
		
		if (!client.negotiateFermataCapabilities(_protocolRevision, _capabilityFlags))
		{
			client.closeNow();
		}
	}
	
	@Override
	protected boolean triggersOnActionRequest()
	{
		return false;
	}
}
