package br.project.cluster.hpc.dashboard;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;

import br.project.cluster.hpc.docker.ClusterDockerFacade;
import br.project.cluster.hpc.disruptor.DisruptorMetrics;
import picocli.CommandLine;

@CommandLine.Command(name = "dashboardpanel", mixinStandardHelpOptions = true, description = "BrProject mini-cluster Docker control panel")
public final class Dashboardpanel implements Callable<Integer> {
    @CommandLine.Option(names = "--docker-host", description = "Docker host (Windows: npipe:////./pipe/docker_engine)")
    String dockerHost;

    @Override public Integer call() throws Exception {
        final MetricsBroadcaster metrics = new MetricsBroadcaster(new DisruptorMetrics());
        try (ClusterDockerFacade docker = new ClusterDockerFacade(dockerHost);
             BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("BrProject Dashboardpanel — comandos: status | metrics | start <id> | stop <id> | quit");
            while (true) {
                System.out.print("br-cluster> ");
                final String line = in.readLine();
                if (line == null || line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) return 0;
                final String[] parts = line.trim().split("\\s+");
                if (parts.length == 0 || parts[0].isBlank()) continue;
                switch (parts[0]) {
                    case "status" -> docker.list().forEach(c -> System.out.printf("%s %-28s %-18s %s%n", c.id().substring(0, 12), c.name(), c.state(), c.status()));
                    case "metrics" -> System.out.println(metrics.snapshotJson());
                    case "start" -> { if (parts.length > 1) docker.start(parts[1]); }
                    case "stop" -> { if (parts.length > 1) docker.stop(parts[1]); }
                    default -> System.out.println("comando desconhecido");
                }
            }
        }
    }

    public static void main(String[] args) { System.exit(new CommandLine(new Dashboardpanel()).execute(args)); }
}
