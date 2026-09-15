package br.project.cluster.hpc.network;

/** Lightweight placeholder for the central API gateway. Generated gRPC stubs plug in here. */
public final class GrpcApiGateway implements AutoCloseable {
    private final int port;
    private volatile boolean started;

    public GrpcApiGateway(int port) { this.port = port; }
    public void start() { started = true; }
    public boolean started() { return started; }
    public int port() { return port; }
    public Ack onPlayerJoined(int serverId, String account, long charObjId, byte[] flatBufferPayload) { return Ack.success(); }
    @Override public void close() { started = false; }
}
