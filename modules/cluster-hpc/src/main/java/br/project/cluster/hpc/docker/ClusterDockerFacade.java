package br.project.cluster.hpc.docker;

import java.util.ArrayList;
import java.util.List;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

/** Docker Java API facade for Dashboardpanel. */
public final class ClusterDockerFacade implements AutoCloseable {
    private final DockerClient docker;

    public ClusterDockerFacade(String dockerHost) {
        final DefaultDockerClientConfig.Builder builder = DefaultDockerClientConfig.createDefaultConfigBuilder();
        if (dockerHost != null && !dockerHost.isBlank()) builder.withDockerHost(dockerHost);
        final var cfg = builder.build();
        final DockerHttpClient http = new ApacheDockerHttpClient.Builder()
            .dockerHost(cfg.getDockerHost())
            .sslConfig(cfg.getSSLConfig())
            .build();
        this.docker = DockerClientImpl.getInstance(cfg, http);
    }

    public List<ContainerTelemetry> list() {
        final List<ContainerTelemetry> out = new ArrayList<>();
        docker.listContainersCmd().withShowAll(true).exec().forEach(c -> out.add(new ContainerTelemetry(
            c.getId(), c.getNames() != null && c.getNames().length > 0 ? c.getNames()[0] : "", c.getImage(), c.getState(), c.getStatus()
        )));
        return out;
    }

    public void start(String idOrName) { docker.startContainerCmd(idOrName).exec(); }
    public void stop(String idOrName) { docker.stopContainerCmd(idOrName).exec(); }
    @Override public void close() { try { docker.close(); } catch (Exception ignored) { } }
}
