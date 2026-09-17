package ext.mods.commons.config

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Base64
import java.util.logging.Level
import java.util.logging.Logger

/**
 * On server startup, ensures that the base64 secret keys in `server.properties`
 * exist and are valid:
 *
 *   - SiteSessionSecret   (used by the Ktor site to sign session cookies)
 *   - SiteGameApiSecret   (HMAC secret the site uses to call the Game API)
 *   - GameApiSecret       (HMAC secret the game-server uses to verify the site)
 *
 *   SiteGameApiSecret and GameApiSecret MUST be the same value — this routine
 *   enforces that invariant. SiteSessionSecret is independent.
 *
 * If any value is missing, blank, the literal placeholder text from the
 * generated `server.properties`, fails base64 decoding, or decodes to fewer
 * than 32 bytes, a fresh 48-byte (=> 64 base64 chars) value is generated and
 * the file is rewritten atomically.
 *
 * Parsing is strictly line-based and uses an anchored per-line pattern (no
 * regex sweep over the whole file). Comments, blank lines and unrelated keys
 * are preserved verbatim, including their original line endings (CRLF or LF),
 * whitespace, and ordering. Only the value side of an existing matching line
 * is rewritten; keys that don't exist yet are appended at EOF under a
 * clearly labelled section.
 *
 * The write is guarded by a process-level file lock so two JVMs racing on
 * the same file (e.g. game-server + site) cannot corrupt it. The lock is
 * retried briefly (default ~250 ms) before giving up silently.
 */
object ServerPropertiesSecretBootstrap {
    private val LOGGER: Logger = Logger.getLogger(ServerPropertiesSecretBootstrap::class.java.name)
    private val RANDOM = SecureRandom()

    private const val SESSION_KEY = "SiteSessionSecret"
    private const val API_PAIR_PRIMARY = "SiteGameApiSecret"
    private const val API_PAIR_SECONDARY = "GameApiSecret"
    private const val MIN_DECODED_BYTES = 32
    /** 48 random bytes => 64 base64 chars. Slightly above the 32-byte minimum. */
    private const val SECRET_BYTES = 48

    // Placeholder strings that ship in server.properties. Detection is
    // case-insensitive and trimmed; we do NOT decode these as base64 (they
    // would fail anyway because of the `-` characters).
    private val PLACEHOLDER_TOKENS = listOf(
        "base64-encoded-48-bytes==",
        "same-base64-value-as-gameapisecret==",
        "same-base64-value-as-sitegameapisecret==",
        "same-base64-value-as-gameapi-secret==",
        "same-base64-value-as-sitegameapi-secret==",
        "changeme",
        "change-me",
        "todo",
    )

    /**
     * Convenience entry point that looks for `server.properties` under the
     * usual relative paths.
     */
    fun ensureDefault(): EnsureResult {
        val candidates = listOf(
            "game/config/server.properties",
            "config/server.properties",
            "../game/config/server.properties",
            "../../game/config/server.properties",
        )
        for (path in candidates) {
            val file = File(path)
            if (file.exists() && file.isFile) {
                return ensure(file)
            }
        }
        return EnsureResult.skipped("server.properties not found in any known location")
    }

    /**
     * Ensures the secrets exist and are valid. Safe to call from either the
     * game-server process or the Ktor site process — both reach the same
     * file and the operation is idempotent.
     */
    fun ensure(file: File): EnsureResult {
        if (!file.exists() || !file.isFile) {
            return EnsureResult.skipped("file not found: ${file.absolutePath}")
        }
        if (!file.canWrite()) {
            // Allow the file to be made writable for the bootstrap, but only if
            // it's a regular file. This makes the auto-generation work when
            // users receive a checked-in copy that lost its +w bit.
            val made = runCatching { file.setWritable(true) }.getOrDefault(false)
            if (!made) {
                return EnsureResult.skipped("file not writable: ${file.absolutePath}")
            }
        }

        // Read raw bytes so we preserve the original line-ending style.
        val raw: ByteArray = try {
            Files.readAllBytes(file.toPath())
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Could not read {0}: {1}", arrayOf<Any?>(file, e.message))
            return EnsureResult.skipped("read error: ${e.message}")
        }

        val lineEnding = detectLineEnding(raw) ?: "\n"
        val text = String(raw, StandardCharsets.UTF_8)
        val parts = text.split(lineEnding)
        // Detect whether the file ended with a trailing newline. If so, the
        // final split element is "".
        val trailingEmpty = parts.isNotEmpty() && parts.last().isEmpty()
        val lines = if (trailingEmpty) parts.dropLast(1) else parts

        val foundSessionIdx = findKeyLine(lines, SESSION_KEY)
        val foundSiteApiIdx = findKeyLine(lines, API_PAIR_PRIMARY)
        val foundGameApiIdx = findKeyLine(lines, API_PAIR_SECONDARY)

        val existingSession = foundSessionIdx?.let { readValue(lines[it], SESSION_KEY) }
        val existingSiteApi = foundSiteApiIdx?.let { readValue(lines[it], API_PAIR_PRIMARY) }
        val existingGameApi = foundGameApiIdx?.let { readValue(lines[it], API_PAIR_SECONDARY) }

        val validSession = isValidSecret(existingSession)
        val validSiteApi = isValidSecret(existingSiteApi)
        val validGameApi = isValidSecret(existingGameApi)
        val apiPairMatches = validSiteApi && validGameApi &&
            constantTimeEquals(existingSiteApi!!, existingGameApi!!)

        val newSiteApi: String
        val newGameApi: String
        var changedSession = false
        var changedSiteApi = false
        var changedGameApi = false

        val newSession: String = if (validSession) {
            existingSession!!
        } else {
            newSecret().also { changedSession = true }
        }

        when {
            apiPairMatches -> {
                newSiteApi = existingSiteApi
                newGameApi = existingGameApi
            }
            validSiteApi -> {
                // Site value is good; copy it to GameApiSecret so the pair matches.
                newSiteApi = existingSiteApi!!
                newGameApi = existingSiteApi
                changedGameApi = true
            }
            validGameApi -> {
                // Game value is good; copy it to SiteGameApiSecret so the pair matches.
                newGameApi = existingGameApi!!
                newSiteApi = existingGameApi
                changedSiteApi = true
            }
            else -> {
                // Neither is good: generate once, share the same value.
                val fresh = newSecret()
                newSiteApi = fresh
                newGameApi = fresh
                changedSiteApi = true
                changedGameApi = true
            }
        }

        if (!changedSession && !changedSiteApi && !changedGameApi) {
            return EnsureResult.alreadyValid(file.absolutePath)
        }

        // Apply the changes to a working copy of the lines.
        val updated = lines.toMutableList()

        // Section header for appended keys (single comment line).
        val headerComment = "# === Auto-generated secrets (ServerPropertiesSecretBootstrap) ==="

        fun replaceExisting(idx: Int, key: String, value: String) {
            updated[idx] = rewriteLine(lines[idx], key, value)
        }

        fun appendNew(key: String, value: String) {
            // Only emit the section header once. We append everything at the
            // end so we never touch existing ordering.
            if (updated.none { it == headerComment }) {
                updated.add(headerComment)
            }
            updated.add("$key=$value")
        }

        if (changedSession) {
            if (foundSessionIdx != null) replaceExisting(foundSessionIdx, SESSION_KEY, newSession)
            else appendNew(SESSION_KEY, newSession)
        }
        if (changedSiteApi) {
            if (foundSiteApiIdx != null) replaceExisting(foundSiteApiIdx, API_PAIR_PRIMARY, newSiteApi)
            else appendNew(API_PAIR_PRIMARY, newSiteApi)
        }
        if (changedGameApi) {
            if (foundGameApiIdx != null) replaceExisting(foundGameApiIdx, API_PAIR_SECONDARY, newGameApi)
            else appendNew(API_PAIR_SECONDARY, newGameApi)
        }

        // Re-serialise. Always end the file with at least one trailing newline.
        val sb = StringBuilder()
        for ((i, line) in updated.withIndex()) {
            sb.append(line)
            if (i != updated.lastIndex) sb.append(lineEnding)
        }
        if (trailingEmpty || updated.isEmpty()) sb.append(lineEnding)
        val serialized = sb.toString()

        // Cross-process file lock (best-effort).
        val target: Path = file.toPath()
        val lockPath: Path = target.resolveSibling(target.fileName.toString() + ".lock")
        val lockFile = RandomAccessFile(lockPath.toFile(), "rw")
        var lockChannel: FileChannel? = null
        try {
            lockChannel = lockFile.channel
            val lock = tryLockWithRetry(lockChannel)
            if (lock == null) {
                // Another process is mid-write; skip rather than corrupt.
                return EnsureResult.skipped("could not acquire lock on ${lockPath}")
            }
            try {
                writeAtomically(target, serialized)
            } finally {
                lock.release()
            }
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Failed to write secrets back to {0}: {1}", arrayOf<Any?>(file, e.message))
            return EnsureResult.skipped("write error: ${e.message}")
        } finally {
            runCatching { lockChannel?.close() }
            runCatching { lockFile.close() }
            runCatching { Files.deleteIfExists(lockPath) }
        }

        val changes = buildList {
            if (changedSession) add(SESSION_KEY)
            if (changedSiteApi) add(API_PAIR_PRIMARY)
            if (changedGameApi) add(API_PAIR_SECONDARY)
        }
        return EnsureResult.regenerated(file.absolutePath, changes)
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Strict anchored pattern for one property line. Matched per-line: never
     * operates across line endings. Anchored at start, captures only the
     * leading key + value, stops before any `#`/`;`/`!` (inline comment).
     */
    private val KEY_LINE = Regex("""^([A-Za-z][A-Za-z0-9_]*)\s*=\s*([^#;\r\n]*?)\s*$""")

    /** Prefix-only pattern used for in-place rewrites. */
    private fun prefixRegex(key: String): Regex =
        Regex("""^($key)(\s*=\s*)([^#;\r\n]*?)\s*$""")

    private fun findKeyLine(lines: List<String>, key: String): Int? {
        for ((i, line) in lines.withIndex()) {
            val first = line.firstOrNull() ?: continue
            // Skip blanks, comments and lookalikes quickly.
            if (first == '#' || first == ';' || first == '!') continue
            val m = KEY_LINE.matchEntire(line) ?: continue
            if (m.groupValues[1] == key) return i
        }
        return null
    }

    private fun readValue(line: String, key: String): String? {
        val m = KEY_LINE.matchEntire(line) ?: return null
        if (m.groupValues[1] != key) return null
        return m.groupValues[2]
    }

    /**
     * Rewrites only the value side of an existing `Key = oldValue [comment]`
     * line, preserving the original separator style (`=`, ` = `, etc.).
     * Inline comments and trailing whitespace are intentionally dropped —
     * secrets must not be annotated or commented.
     */
    private fun rewriteLine(original: String, key: String, newValue: String): String {
        val re = prefixRegex(key)
        val m = re.matchEntire(original)
            ?: throw IllegalStateException("internal: expected to rewrite line '$original' for key $key")
        return m.groupValues[1] + m.groupValues[2] + newValue
    }

    private fun isValidSecret(raw: String?): Boolean {
        if (raw == null) return false
        val v = raw.trim()
        if (v.isEmpty()) return false
        if (PLACEHOLDER_TOKENS.any { v.equals(it, ignoreCase = true) }) return false
        return try {
            Base64.getDecoder().decode(v).size >= MIN_DECODED_BYTES
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }

    private fun newSecret(): String {
        val bytes = ByteArray(SECRET_BYTES)
        RANDOM.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun detectLineEnding(raw: ByteArray): String? {
        if (raw.isEmpty()) return null
        for (i in 0 until raw.size - 1) {
            if (raw[i] == '\r'.code.toByte() && raw[i + 1] == '\n'.code.toByte()) return "\r\n"
            if (raw[i] == '\n'.code.toByte()) return "\n"
        }
        return null
    }

    private fun tryLockWithRetry(channel: FileChannel): java.nio.channels.FileLock? {
        // Brief loop: the window for a colliding writer is tiny.
        val deadline = System.currentTimeMillis() + 250L
        var attempt = 0L
        while (System.currentTimeMillis() < deadline) {
            val lock = channel.tryLock()
            if (lock != null) return lock
            try {
                if (attempt == 0L) Thread.sleep(20L) else Thread.sleep(40L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
            attempt++
        }
        return null
    }

    private fun writeAtomically(target: Path, content: String) {
        val tmp = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.write(tmp, content.toByteArray(StandardCharsets.UTF_8))
        try {
            Files.move(
                tmp,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Throwable) {
            // ATOMIC_MOVE not supported on the volume (some FAT32/exFAT scenarios).
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    data class EnsureResult(
        val path: String,
        val regeneratedKeys: List<String> = emptyList(),
        val reason: String? = null,
    ) {
        val skipped: Boolean get() = reason != null && regeneratedKeys.isEmpty()
        val changed: Boolean get() = regeneratedKeys.isNotEmpty()
        val alreadyValid: Boolean get() = reason == "already valid"

        override fun toString(): String = buildString {
            if (path.isNotEmpty()) append(path).append(": ")
            when {
                alreadyValid -> append("secrets already valid")
                skipped -> append("skipped (").append(reason).append(')')
                changed -> {
                    append("regenerated [")
                    append(regeneratedKeys.joinToString(", "))
                    append(']')
                }
                else -> append("no-op")
            }
        }

        companion object {
            fun regenerated(path: String, keys: List<String>) = EnsureResult(path, keys)
            fun alreadyValid(path: String) = EnsureResult(path, emptyList(), "already valid")
            fun skipped(reason: String) = EnsureResult("", emptyList(), reason)
        }
    }
}
