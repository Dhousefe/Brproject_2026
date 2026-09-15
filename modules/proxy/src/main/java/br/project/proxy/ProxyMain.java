package br.project.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bootstraps the proxy: parses the XML, starts one TcpProxyServer or HttpProxyServer
 * per enabled route, runs a single shared FixedWindowRateLimiter, and installs a
 * periodic GC sweep on the limiter buckets.
 */
public final class ProxyMain {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyMain.class);

    private final List<AutoCloseable> servers = new ArrayList<>();
    private final List<Thread> hookThreads = new ArrayList<>();
    private ScheduledExecutorService gcExecutor;

    public static void main(String[] args) throws Exception {
        Path configPath = resolveConfigPath(args);
        LOG.info("[proxy] loading config from {}", configPath.toAbsolutePath());
        ProxyConfig config = ProxyConfigLoader.load(configPath);

        if (!config.enabled()) {
            LOG.warn("[proxy] reverseProxy disabled (enabled=\"{}\") in {}; exiting.",
                config.enabled(), configPath.toAbsolutePath());
            return;
        }

        ProxyMain bootstrap = new ProxyMain();
        bootstrap.run(config);
    }

    private static Path resolveConfigPath(String[] args) {
        if (args != null && args.length > 0 && !args[0].isBlank()) {
            return Path.of(args[0]);
        }
        String env = System.getenv("PROXY_CONFIG");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of(ProxyConfigLoader.defaultFilePath());
    }

    private void run(ProxyConfig config) throws Exception {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();
        ProxyBanCache.getInstance().start();
        gcExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "proxy-rate-limiter-gc");
            t.setDaemon(true);
            return t;
        });
        gcExecutor.scheduleAtFixedRate(limiter::gc, 60, 60, TimeUnit.SECONDS);

        Thread shutdownHook = new Thread(this::shutdown, "proxy-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        hookThreads.add(shutdownHook);

        for (ProxyRoute route : config.enabledRoutes()) {
            try {
                switch (route.type()) {
                    case TCP -> {
                        TcpProxyServer server = new TcpProxyServer(route, limiter);
                        server.start();
                        servers.add(server);
                    }
                    case HTTP -> {
                        HttpProxyServer server = new HttpProxyServer(route, limiter);
                        server.start();
                        servers.add(server);
                    }
                    case HTTP_REDIRECT -> {
                        HttpRedirectServer server = new HttpRedirectServer(route);
                        server.start();
                        servers.add(server);
                    }
                }
            } catch (Exception e) {
                LOG.error("[proxy] failed to start route '{}': {}", route.name(), e.toString(), e);
            }
        }

        if (servers.isEmpty()) {
            LOG.error("[proxy] no route started successfully. Shutting down.");
            shutdown();
            System.exit(1);
            return;
        }

        LOG.info("[proxy] {} route(s) started. Press Ctrl+C to stop.", servers.size());

        Thread.currentThread().join();
    }

    private void shutdown() {
        for (AutoCloseable s : servers) {
            try { s.close(); } catch (Exception e) {
                LOG.debug("[proxy] close error: {}", e.toString());
            }
        }
        if (gcExecutor != null) {
            gcExecutor.shutdownNow();
        }
        ProxyBanCache.getInstance().close();
    }
}
