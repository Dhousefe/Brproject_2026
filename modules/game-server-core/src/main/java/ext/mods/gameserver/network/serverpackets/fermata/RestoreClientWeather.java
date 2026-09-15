package ext.mods.gameserver.network.serverpackets.fermata;

import ext.mods.gameserver.network.serverpackets.L2GameServerPacket;

/**
 * Pacote RestoreClientWeather (S->C 0xFD 0x0002) da extensão de protocolo Fermata.
 * Restaura o controle de clima e céu para as configurações locais do cliente (3 bytes totais).
 */
public final class RestoreClientWeather extends L2GameServerPacket
{
	public static final RestoreClientWeather STATIC_PACKET = new RestoreClientWeather();
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfd);
		writeH(0x0002);
	}
}
