package br.project.cluster.hpc.network;

/** Minimal ack DTO used until generated gRPC stubs are wired. */
public record Ack(boolean ok, String reason) { public static Ack success() { return new Ack(true, "OK"); } }
