package br.project.cluster.hpc.network;

import java.nio.ByteBuffer;
import com.google.flatbuffers.FlatBufferBuilder;

/** Game-server side notifier; payload format mirrors src/main/flatbuffers/PlayerJoined.fbs. */
public final class PlayerJoinedNotifier {
    public ByteBuffer buildPayload(int serverId, String charName, int x, int y, int z, int clanId, int karma) {
        final FlatBufferBuilder b = new FlatBufferBuilder(128);
        final int name = b.createString(charName == null ? "" : charName);
        // Manual table construction keeps this source compilable without flatc on developer machines.
        b.startTable(7);
        b.addInt(0, serverId, 0);
        b.addOffset(1, name, 0);
        b.addInt(2, x, 0); b.addInt(3, y, 0); b.addInt(4, z, 0);
        b.addInt(5, clanId, 0); b.addInt(6, karma, 0);
        int root = b.endTable();
        b.finish(root);
        return b.dataBuffer();
    }

    public Ack notifyLoginAsync(int serverId, String account, long charObjId, ByteBuffer payload) {
        // Real gRPC call is wired by GrpcApiGateway when generated stubs are enabled.
        return Ack.success();
    }
}
