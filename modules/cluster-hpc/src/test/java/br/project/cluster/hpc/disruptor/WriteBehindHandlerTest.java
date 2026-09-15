package br.project.cluster.hpc.disruptor;

import br.project.cluster.hpc.sqlite.SQLiteConnectionFactory;
import br.project.cluster.hpc.sqlite.SingleWriterHandle;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WriteBehindHandlerTest {
    @Test void flushesPlayerPositionsInSingleTransaction() throws Exception {
        final var db = Files.createTempFile("brproject-cluster-hpc", ".sqlite");
        final String url = "jdbc:sqlite:" + db;
        try (Connection c = new SQLiteConnectionFactory(url).openReadWrite(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE characters(obj_Id INTEGER PRIMARY KEY, x INTEGER, y INTEGER, z INTEGER, heading INTEGER, lastAccess INTEGER)");
            st.execute("CREATE TABLE accounts(login TEXT PRIMARY KEY, last_active INTEGER)");
            st.execute("INSERT INTO characters(obj_Id,x,y,z,heading,lastAccess) VALUES (1,0,0,0,0,0)");
        }
        try (SingleWriterHandle writer = new SingleWriterHandle(new SQLiteConnectionFactory(url));
             WriteBehindHandler handler = new WriteBehindHandler(writer, new BatchPolicy(1, 1_000_000L), new DisruptorMetrics())) {
            StateChangeEvent e = new StateChangeEvent();
            e.type = StateChangeType.PLAYER_POSITION;
            e.objectId = 1;
            e.intA = 100; e.intB = 200; e.intC = 300;
            e.longA = 7; e.longB = 1234;
            handler.onEvent(e, 0, true);
        }
        try (Connection c = new SQLiteConnectionFactory(url).openReadWrite()) {
            var row = c.createStatement().executeQuery("SELECT x,y,z,heading,lastAccess FROM characters WHERE obj_Id=1");
            row.next();
            assertEquals(100, row.getInt(1));
            assertEquals(200, row.getInt(2));
            assertEquals(300, row.getInt(3));
            assertEquals(7, row.getInt(4));
            assertEquals(1234, row.getLong(5));
        }
    }
}
