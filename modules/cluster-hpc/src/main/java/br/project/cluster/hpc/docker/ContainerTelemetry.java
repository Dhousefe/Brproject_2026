package br.project.cluster.hpc.docker;

public record ContainerTelemetry(String id, String name, String image, String state, String status) { }
