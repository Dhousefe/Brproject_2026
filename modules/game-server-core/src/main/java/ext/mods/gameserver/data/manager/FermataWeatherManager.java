package ext.mods.gameserver.data.manager;

import ext.mods.commons.logging.CLogger;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.fermata.WeatherProfile;
import ext.mods.gameserver.network.FermataProtocol;
import ext.mods.gameserver.network.serverpackets.fermata.RestoreClientWeather;
import ext.mods.gameserver.network.serverpackets.fermata.SetWeather;

/**
 * Gerenciador central de controle do motor de clima para clientes Fermata (Server-Driven Weather).
 * Garante entrega estrita de pacotes 0xFD apenas para conexões que negociaram o Bit 1 de capacidades,
 * assegurando 100% de transparência e zero regressão com o cliente Interlude clássico.
 */
public final class FermataWeatherManager
{
	private static final CLogger LOGGER = new CLogger(FermataWeatherManager.class.getName());
	
	private volatile WeatherProfile _globalProfile = null;
	
	protected FermataWeatherManager()
	{
		LOGGER.info("FermataWeatherManager initialized.");
	}
	
	public static FermataWeatherManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	/**
	 * Instala um perfil de clima global no servidor para todos os clientes Fermata online.
	 * Clientes legados não recebem nenhum pacote 0xFD.
	 */
	public void setGlobalWeather(WeatherProfile profile)
	{
		_globalProfile = profile;
		if (profile == null)
		{
			restoreGlobalWeather();
			return;
		}
		
		final SetWeather packet = new SetWeather(profile);
		int dispatched = 0;
		for (Player player : World.getInstance().getPlayers())
		{
			if (player != null && player.getClient() != null && player.getClient().hasFermataCapability(FermataProtocol.SERVER_WEATHER))
			{
				player.sendPacket(packet);
				dispatched++;
			}
		}
		LOGGER.info("Global weather set (precipitation={}, intensity={}) dispatched to {} Fermata client(s).",
			profile.getPrecipitation(), profile.getIntensity(), dispatched);
	}
	
	/**
	 * Restaura o controle do clima local para as configurações do cliente de todos os jogadores Fermata.
	 */
	public void restoreGlobalWeather()
	{
		_globalProfile = null;
		int dispatched = 0;
		for (Player player : World.getInstance().getPlayers())
		{
			if (player != null && player.getClient() != null && player.getClient().hasFermataCapability(FermataProtocol.SERVER_WEATHER))
			{
				player.sendPacket(RestoreClientWeather.STATIC_PACKET);
				dispatched++;
			}
		}
		LOGGER.info("Global weather restored to local client control on {} Fermata client(s).", dispatched);
	}
	
	/**
	 * Aplica uma substituição de clima a um jogador específico.
	 */
	public boolean setWeatherForPlayer(Player player, WeatherProfile profile)
	{
		if (player == null || player.getClient() == null || !player.getClient().hasFermataCapability(FermataProtocol.SERVER_WEATHER))
		{
			return false;
		}
		
		player.sendPacket(new SetWeather(profile));
		return true;
	}
	
	/**
	 * Restaura o clima local de um jogador específico.
	 */
	public boolean restoreWeatherForPlayer(Player player)
	{
		if (player == null || player.getClient() == null || !player.getClient().hasFermataCapability(FermataProtocol.SERVER_WEATHER))
		{
			return false;
		}
		
		player.sendPacket(RestoreClientWeather.STATIC_PACKET);
		return true;
	}
	
	/**
	 * Invocado quando um jogador conclui a entrada no mundo (EnterWorld).
	 * Se houver um clima global ativo no servidor e o jogador for Fermata, instala o clima alvo.
	 */
	public void onPlayerEnterWorld(Player player)
	{
		if (player == null || player.getClient() == null)
		{
			return;
		}
		
		if (player.getClient().hasFermataCapability(FermataProtocol.SERVER_WEATHER) && _globalProfile != null)
		{
			player.sendPacket(new SetWeather(_globalProfile));
		}
	}
	
	public WeatherProfile getGlobalProfile()
	{
		return _globalProfile;
	}
	
	private static class SingletonHolder
	{
		protected static final FermataWeatherManager INSTANCE = new FermataWeatherManager();
	}
}
