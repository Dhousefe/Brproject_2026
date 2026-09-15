package br.project.cluster.hpc.disruptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** PreparedStatement cache + SQL mapping for hot L2J state changes. */
public final class WriteBehindJournal implements AutoCloseable {
    private final PreparedStatement updatePosition;
    private final PreparedStatement touchAccount;

    public WriteBehindJournal(Connection connection) throws SQLException {
        this.updatePosition = connection.prepareStatement(
            "UPDATE characters SET x=?, y=?, z=?, heading=?, lastAccess=? WHERE obj_Id=?"
        );
        this.touchAccount = connection.prepareStatement(
            "UPDATE accounts SET last_active=? WHERE login=?"
        );
    }

    public void add(StateChangeEvent e) throws SQLException {
        switch (e.type) {
            case PLAYER_POSITION -> {
                updatePosition.setInt(1, e.intA);
                updatePosition.setInt(2, e.intB);
                updatePosition.setInt(3, e.intC);
                updatePosition.setInt(4, (int) e.longA);
                updatePosition.setLong(5, e.longB);
                updatePosition.setInt(6, e.objectId);
                updatePosition.addBatch();
            }
            case ACCOUNT_TOUCH -> {
                touchAccount.setLong(1, e.longA);
                touchAccount.setString(2, Integer.toString(e.objectId));
                touchAccount.addBatch();
            }
            default -> { /* extension point for inventory, skills, quests */ }
        }
    }

    public void executeBatches() throws SQLException {
        updatePosition.executeBatch();
        touchAccount.executeBatch();
    }

    @Override public void close() throws SQLException {
        SQLException first = null;
        try { updatePosition.close(); } catch (SQLException e) { first = e; }
        try { touchAccount.close(); } catch (SQLException e) { if (first == null) first = e; else first.addSuppressed(e); }
        if (first != null) throw first;
    }
}
