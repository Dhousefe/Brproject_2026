package ext.mods.commons.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Safe Properties Editor: Performs surgical in-place key updates in .properties files
 * preserving 100% of comments, whitespace, ordering, and without inserting ugly/broken
 * backslash escapes (e.g. \: or \/) in JDBC URLs.
 */
public final class SafePropertiesEditor {

    private SafePropertiesEditor() {}

    /**
     * Updates multiple properties in the specified file, preserving comments and formatting.
     * If the file does not exist, it will be created with the given properties.
     */
    public static synchronized void updateProperties(File targetFile, Map<String, String> keyValues) throws IOException {
        if (targetFile == null || keyValues == null || keyValues.isEmpty()) {
            return;
        }

        if (targetFile.getParentFile() != null) {
            targetFile.getParentFile().mkdirs();
        }

        Path path = targetFile.toPath();
        List<String> lines = new ArrayList<>();
        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String l;
                while ((l = reader.readLine()) != null) {
                    lines.add(l);
                }
            }
        }

        Map<String, String> pending = new LinkedHashMap<>(keyValues);
        Set<String> updatedKeys = new HashSet<>();
        List<String> outputLines = new ArrayList<>(lines.size() + pending.size());

        for (String line : lines) {
            String trimmed = line.trim();
            // Preserve comments and empty lines intact
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                outputLines.add(line);
                continue;
            }

            int sepIdx = findSeparatorIndex(line);
            if (sepIdx != -1) {
                String key = line.substring(0, sepIdx).trim();
                if (pending.containsKey(key)) {
                    String newValue = pending.get(key);
                    outputLines.add(key + " = " + (newValue != null ? newValue : ""));
                    updatedKeys.add(key);
                    continue;
                }
            }

            outputLines.add(line);
        }

        // Append any remaining keys that were not found in the file
        for (Map.Entry<String, String> entry : pending.entrySet()) {
            if (!updatedKeys.contains(entry.getKey())) {
                outputLines.add(entry.getKey() + " = " + (entry.getValue() != null ? entry.getValue() : ""));
            }
        }

        // Atomic write via temp file
        Path tmp = Path.of(targetFile.getAbsolutePath() + ".tmp." + System.nanoTime());
        try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (String l : outputLines) {
                writer.write(l);
                writer.newLine();
            }
        }

        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Fallback for filesystems that do not support ATOMIC_MOVE across boundaries
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int findSeparatorIndex(String line) {
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                escaped = !escaped;
                continue;
            }
            if (!escaped && (c == '=' || c == ':')) {
                return i;
            }
            escaped = false;
        }
        return -1;
    }

    /**
     * Result of a .properties structural and semantic validation.
     */
    public record ValidationResult(
        boolean isValid,
        List<String> errors,
        List<String> warnings,
        int totalPropertiesCount
    ) {
        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }
    }

    /**
     * Validates a .properties file for syntax integrity, required keys, and valid JDBC URLs.
     */
    public static ValidationResult validate(File targetFile, List<String> requiredKeys) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (targetFile == null || !targetFile.exists()) {
            errors.add("Arquivo inexistente: " + (targetFile != null ? targetFile.getPath() : "null"));
            return new ValidationResult(false, errors, warnings, 0);
        }

        Set<String> foundKeys = new HashSet<>();
        int lineNum = 0;
        try (BufferedReader reader = Files.newBufferedReader(targetFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                    continue;
                }

                int sepIdx = findSeparatorIndex(line);
                if (sepIdx == -1) {
                    warnings.add("Linha " + lineNum + " sem delimitador '=' ou ':': " + line);
                    continue;
                }

                String key = line.substring(0, sepIdx).trim();
                String val = line.substring(sepIdx + 1).trim();

                if (key.isEmpty()) {
                    warnings.add("Linha " + lineNum + " possui chave vazia.");
                    continue;
                }

                if (foundKeys.contains(key)) {
                    warnings.add("Chave duplicada encontrada na linha " + lineNum + ": '" + key + "'");
                }
                foundKeys.add(key);

                // JDBC URL format validation
                if ("sql.url".equals(key) || "URL".equals(key)) {
                    if (!isValidJdbcUrl(val)) {
                        errors.add("URL JDBC invalida na linha " + lineNum + ": '" + val + "'");
                    }
                }
            }
        } catch (IOException e) {
            errors.add("Erro de leitura no arquivo: " + e.getMessage());
            return new ValidationResult(false, errors, warnings, 0);
        }

        if (requiredKeys != null) {
            for (String req : requiredKeys) {
                if (!foundKeys.contains(req)) {
                    errors.add("Chave obrigatoria ausente: '" + req + "'");
                }
            }
        }

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, errors, warnings, foundKeys.size());
    }

    private static boolean isValidJdbcUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase().trim();
        return lower.startsWith("jdbc:sqlite:")
            || lower.startsWith("jdbc:mariadb:")
            || lower.startsWith("jdbc:mysql:")
            || lower.startsWith("jdbc:postgresql:")
            || lower.startsWith("jdbc:sqlserver:")
            || lower.startsWith("jdbc:hsqldb:");
    }
}
