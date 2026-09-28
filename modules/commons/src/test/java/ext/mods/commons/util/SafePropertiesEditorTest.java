package ext.mods.commons.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SafePropertiesEditorTest {

    @Test
    void preservesCommentsAndFormatting(@TempDir Path tempDir) throws Exception {
        File testFile = tempDir.resolve("test.properties").toFile();
        String original = "# Header comment\n" +
            "# Section Database\n" +
            "sql.url = jdbc:sqlite:data/old.db\n" +
            "sql.login = root\n" +
            "\n" +
            "# Other setting\n" +
            "AutoCreateAccounts = False\n";
        Files.writeString(testFile.toPath(), original);

        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("sql.url", "jdbc:mariadb://localhost:3306/l2jdb?useUnicode=true");
        updates.put("sql.login", "brproject_user");
        updates.put("sql.password", "secret");

        SafePropertiesEditor.updateProperties(testFile, updates);

        String result = Files.readString(testFile.toPath());
        assertTrue(result.contains("# Header comment"), "Must preserve header comments");
        assertTrue(result.contains("# Section Database"), "Must preserve section comments");
        assertTrue(result.contains("sql.url = jdbc:mariadb://localhost:3306/l2jdb?useUnicode=true"), "Must not escape colons/slashes");
        assertFalse(result.contains("jdbc\\:"), "Must not contain ugly backslash escapes");
        assertTrue(result.contains("sql.login = brproject_user"));
        assertTrue(result.contains("sql.password = secret"), "Must append missing property");
        assertTrue(result.contains("AutoCreateAccounts = False"), "Must keep existing settings");
    }

    @Test
    void preservesStrictLineOrdering(@TempDir Path tempDir) throws Exception {
        File testFile = tempDir.resolve("order.properties").toFile();
        String original = "keyA = 1\n" +
            "keyB = 2\n" +
            "keyC = 3\n";
        Files.writeString(testFile.toPath(), original);

        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("keyB", "updated_2");

        SafePropertiesEditor.updateProperties(testFile, updates);

        java.util.List<String> lines = Files.readAllLines(testFile.toPath());
        assertEquals(3, lines.size());
        assertEquals("keyA = 1", lines.get(0));
        assertEquals("keyB = updated_2", lines.get(1));
        assertEquals("keyC = 3", lines.get(2));
    }

    @Test
    void validatesRequiredKeysAndJdbcUrl(@TempDir Path tempDir) throws Exception {
        File testFile = tempDir.resolve("valid.properties").toFile();
        String content = "Hostname = 127.0.0.1\n" +
            "sql.url = jdbc:mariadb://localhost:3306/l2jdb\n" +
            "sql.login = root\n";
        Files.writeString(testFile.toPath(), content);

        var result = SafePropertiesEditor.validate(testFile, java.util.List.of("Hostname", "sql.url"));
        assertTrue(result.isValid(), "Should be valid: " + result.errors());
        assertEquals(3, result.totalPropertiesCount());

        // Missing required key test
        var invalidResult = SafePropertiesEditor.validate(testFile, java.util.List.of("NonExistentKey"));
        assertFalse(invalidResult.isValid());
        assertTrue(invalidResult.errors().get(0).contains("NonExistentKey"));
    }
}
