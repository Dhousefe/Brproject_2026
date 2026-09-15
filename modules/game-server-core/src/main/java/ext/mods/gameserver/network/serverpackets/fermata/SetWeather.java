package ext.mods.gameserver.network.serverpackets.fermata;

import ext.mods.gameserver.model.fermata.WeatherProfile;
import ext.mods.gameserver.network.serverpackets.L2GameServerPacket;

/**
 * Pacote SetWeather (S->C 0xFD 0x0001) da extensão de protocolo Fermata.
 * Configura parâmetros de clima e atmosfera definidos pelo servidor (100 bytes totais).
 */
public final class SetWeather extends L2GameServerPacket
{
	private final int _weatherType;
	private final double[] _params;
	
	public SetWeather(WeatherProfile profile)
	{
		this(profile.getPrecipitation(), profile.toParamArray());
	}
	
	public SetWeather(int weatherType, double[] params)
	{
		_weatherType = Math.max(0, Math.min(2, weatherType));
		_params = new double[12];
		if (params != null)
		{
			for (int i = 0; i < Math.min(params.length, 12); i++)
			{
				double val = params[i];
				_params[i] = Double.isFinite(val) ? val : 0.0;
			}
		}
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfd);
		writeH(0x0001);
		
		writeC(_weatherType);
		for (int i = 0; i < 12; i++)
		{
			writeF(_params[i]);
		}
	}
}
