package ext.mods.commons.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutonomousDatabaseInstallerTest {

    @Test
    void splitsStatementsSafely() {
        String script = "-- comment\n" +
            "CREATE TABLE a (id INT);\n" +
            "INSERT INTO a VALUES (1); -- inline comment\n" +
            "INSERT INTO a VALUES (';inside string;');\n";
        List<String> stmts = AutonomousDatabaseInstaller.splitSqlStatements(script);
        assertEquals(3, stmts.size());
        assertTrue(stmts.get(0).contains("CREATE TABLE a"));
        assertTrue(stmts.get(1).contains("VALUES (1)"));
        assertTrue(stmts.get(2).contains("';inside string;'"));
    }

    @Test
    void adaptsPostgresDdl() {
        String mysql = "CREATE TABLE `test` (`id` INT(11) NOT NULL AUTO_INCREMENT, `active` TINYINT(1) UNSIGNED, PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        String adapted = AutonomousDatabaseInstaller.adaptSqlForDatabase(mysql, SupportedDatabase.POSTGRESQL);
        assertFalse(adapted.contains("ENGINE="));
        assertFalse(adapted.contains("CHARSET="));
        assertFalse(adapted.contains("UNSIGNED"));
        assertFalse(adapted.contains("`"));
        assertTrue(adapted.contains("\"test\""));
    }
}
