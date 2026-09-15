package br.project.proxy;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyConfigLoaderTest {

    @Test
    void parsesExplicitHttpAndTcpRoutesWithRateLimiters() throws Exception {
        String xml = """
            <list>
              <reverseProxy enabled="true">
                <route name="tcp-one" type="tcp" enabled="true" bindHost="127.0.0.1" bindPort="7778" targetHost="127.0.0.1" targetPort="7777">
                  <rateLimiter enabled="true" windowSeconds="30" maxConnections="5" maxRequests="0"/>
                </route>
                <route name="site" type="http" enabled="false" bindHost="0.0.0.0" bindPort="8090" targetHost="127.0.0.1" targetPort="8080" addForwardedHeaders="true">
                  <rateLimiter enabled="true" windowSeconds="60" maxConnections="20" maxRequests="10"/>
                </route>
              </reverseProxy>
            </list>
            """;

        ProxyConfig config = ProxyConfigLoader.load(new StringReader(xml));

        assertTrue(config.enabled());
        assertEquals(2, config.routes().size());
        assertEquals(1, config.enabledRoutes().size());

        ProxyRoute tcp = config.routes().get(0);
        assertEquals("tcp-one", tcp.name());
        assertEquals(ProxyRoute.RouteType.TCP, tcp.type());
        assertEquals(7778, tcp.bindPort());
        assertEquals(7777, tcp.targetPort());
        assertEquals(5, tcp.rateLimit().maxConnections());
        assertEquals(30, tcp.rateLimit().windowSeconds());

        ProxyRoute http = config.routes().get(1);
        assertEquals(ProxyRoute.RouteType.HTTP, http.type());
        assertFalse(http.enabled());
        assertTrue(http.addForwardedHeaders());
        assertEquals(10, http.rateLimit().maxRequests());
    }

    @Test
    void parsesLegacyGameserverProxyAsTcpRoutes() throws Exception {
        String xml = """
            <list>
              <gameserver serverId="1" hide="true" fallbackToGameserver="false">
                <proxy proxyServerId="2" proxyHost="127.0.0.1" proxyPort="7778"/>
              </gameserver>
            </list>
            """;

        ProxyConfig config = ProxyConfigLoader.load(new StringReader(xml));

        assertTrue(config.enabled());
        assertEquals(1, config.routes().size());
        ProxyRoute route = config.routes().get(0);
        assertEquals("legacy-2", route.name());
        assertEquals(ProxyRoute.RouteType.TCP, route.type());
        assertEquals(7778, route.bindPort());
        assertEquals(7777, route.targetPort());
    }

    @Test
    void parsesTlsDomainAndMaxContentLength() throws Exception {
        String xml = """
            <list>
              <reverseProxy enabled="true">
                <route name="site-custom" type="http" enabled="true" bindHost="0.0.0.0" bindPort="443"
                       targetHost="127.0.0.1" targetPort="8080" autoTls="true" tlsDomain="127.0.0.1"
                       maxContentLength="104857600">
                  <rateLimiter enabled="false" windowSeconds="60" maxConnections="0" maxRequests="0"/>
                </route>
              </reverseProxy>
            </list>
            """;

        ProxyConfig config = ProxyConfigLoader.load(new StringReader(xml));
        assertEquals(1, config.routes().size());
        ProxyRoute route = config.routes().get(0);
        assertEquals("127.0.0.1", route.tlsDomain());
        assertEquals(104857600, route.maxContentLength());
    }
}
