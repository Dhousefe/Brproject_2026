package br.project.proxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class HttpRedirectServerTest {

    private HttpRedirectServer redirectServer;

    @AfterEach
    void tearDown() {
        if (redirectServer != null) {
            redirectServer.close();
        }
    }

    @Test
    void redirectsToDefaultHttpsPort() throws Exception {
        int freePort = 19180;
        ProxyRoute route = new ProxyRoute(
            "test-redirect-default",
            ProxyRoute.RouteType.HTTP_REDIRECT,
            true,
            "127.0.0.1",
            freePort,
            "127.0.0.1",
            443,
            false,
            false,
            RateLimitConfig.defaultsFor(ProxyRoute.RouteType.HTTP),
            null,
            null,
            false
        );

        redirectServer = new HttpRedirectServer(route);
        redirectServer.start();

        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + freePort + "/ranking"))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(301, response.statusCode(), "Must return 301 Moved Permanently");
        assertEquals("https://localhost/ranking", response.headers().firstValue("location").orElse(""));
    }

    @Test
    void redirectsToCustomHttpsPort() throws Exception {
        int freePort = 19181;
        ProxyRoute route = new ProxyRoute(
            "test-redirect-custom",
            ProxyRoute.RouteType.HTTP_REDIRECT,
            true,
            "127.0.0.1",
            freePort,
            "127.0.0.1",
            8443,
            false,
            false,
            RateLimitConfig.defaultsFor(ProxyRoute.RouteType.HTTP),
            null,
            null,
            false
        );

        redirectServer = new HttpRedirectServer(route);
        redirectServer.start();

        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + freePort + "/account"))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(301, response.statusCode(), "Must return 301 Moved Permanently");
        assertEquals("https://127.0.0.1:8443/account", response.headers().firstValue("location").orElse(""));
    }
}
