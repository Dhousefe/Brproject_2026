package br.project.proxy;

import java.util.List;

public record ProxyConfig(boolean enabled, List<ProxyRoute> routes) {
    public List<ProxyRoute> enabledRoutes() {
        return routes.stream().filter(ProxyRoute::enabled).toList();
    }
}
