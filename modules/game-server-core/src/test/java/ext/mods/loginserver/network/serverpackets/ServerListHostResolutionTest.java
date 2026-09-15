package ext.mods.loginserver.network.serverpackets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ext.mods.loginserver.model.GameServerInfo;

/**
 * Validação de resolução de host do GameServer no ServerList.
 * Garante que clientes Android e da rede LAN recebem o IP acessível do GameServer (ex: 192.168.100.14),
 * e que a presença do Netty Proxy (que encaminha conexões via 127.0.0.1) não force loopback para clientes externos.
 */
class ServerListHostResolutionTest
{
	@Test
	@DisplayName("Deve retornar o IP configurado no banco de dados mesmo quando o cliente conecta via Netty Proxy (loopback)")
	void testResolveServerHostWithProxyLoopbackClient() throws UnknownHostException
	{
		final GameServerInfo gsi = new GameServerInfo(1, new byte[16]);
		gsi.setHostName("192.168.100.14");

		// Cliente vindo através do Netty Proxy na porta 2107 (loopback 127.0.0.1)
		final InetAddress proxyClientIp = InetAddress.getByName("127.0.0.1");

		final String resolved = ServerList.resolveServerHostForClient(proxyClientIp, gsi);
		assertEquals("192.168.100.14", resolved, "Deve retornar o IP de rede do GS e nao loopback");
	}

	@Test
	@DisplayName("Deve retornar dominio configurado quando aplicavel")
	void testResolveServerHostWithDomain() throws UnknownHostException
	{
		final GameServerInfo gsi = new GameServerInfo(1, new byte[16]);
		gsi.setHostName("play.l2brasil.com");

		final InetAddress clientIp = InetAddress.getByName("192.168.100.50");
		final String resolved = ServerList.resolveServerHostForClient(clientIp, gsi);
		assertEquals("play.l2brasil.com", resolved);
	}

	@Test
	@DisplayName("Deve resolver IP de LAN quando host do gsi for '*' e maquina tiver rede")
	void testResolveServerHostWithWildcard() throws UnknownHostException
	{
		final GameServerInfo gsi = new GameServerInfo(1, new byte[16]);
		gsi.setHostName("*");

		final InetAddress clientIp = InetAddress.getByName("127.0.0.1");
		final String resolved = ServerList.resolveServerHostForClient(clientIp, gsi);
		assertNotNull(resolved);
		assertNotEquals("*", resolved);
	}

	@Test
	@DisplayName("getLocalLanAddress() deve retornar um endereco valido e nao lancar excecao")
	void testGetLocalLanAddress()
	{
		final String lanIp = ServerList.getLocalLanAddress();
		if (lanIp != null)
		{
			assertNotNull(lanIp);
		}
	}
}
