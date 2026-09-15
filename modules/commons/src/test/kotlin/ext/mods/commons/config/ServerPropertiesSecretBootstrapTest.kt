package ext.mods.commons.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Base64

class ServerPropertiesSecretBootstrapTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `replaces placeholder secrets without corrupting unrelated server properties`() {
        val file = tempDir.resolve("server.properties").toFile()
        file.writeText(
            listOf(
                "# before",
                "Hostname = 127.0.0.1",
                "TitleTemplate = [A-Za-z0-9 ._-]{0,16}",
                "SiteSessionSecret=base64-encoded-48-bytes==",
                "SiteGameApiSecret=same-base64-value-as-GameApiSecret==",
                "GameApiSecret=same-base64-value-as-SiteGameApiSecret==",
                "# after",
                "",
            ).joinToString("\r\n"),
            Charsets.UTF_8,
        )

        val result = ServerPropertiesSecretBootstrap.ensure(file)
        assertTrue(result.changed)
        assertEquals(listOf("SiteSessionSecret", "SiteGameApiSecret", "GameApiSecret"), result.regeneratedKeys)

        val updated = file.readText(Charsets.UTF_8)
        assertTrue(updated.contains("\r\n"), "CRLF line endings should be preserved")
        assertTrue(updated.contains("Hostname = 127.0.0.1"))
        assertTrue(updated.contains("TitleTemplate = [A-Za-z0-9 ._-]{0,16}"))
        assertTrue(updated.contains("# before"))
        assertTrue(updated.contains("# after"))

        val props = parse(updated)
        assertValidBase64Secret(props.getValue("SiteSessionSecret"))
        assertValidBase64Secret(props.getValue("SiteGameApiSecret"))
        assertValidBase64Secret(props.getValue("GameApiSecret"))
        assertEquals(props.getValue("SiteGameApiSecret"), props.getValue("GameApiSecret"))
        assertNotEquals(props.getValue("SiteSessionSecret"), props.getValue("GameApiSecret"))
    }

    @Test
    fun `keeps already valid matching secrets unchanged and does not rewrite file`() {
        val session = secret("session")
        val api = secret("api")
        val file = tempDir.resolve("server.properties").toFile()
        val original = "SiteSessionSecret=$session\nSiteGameApiSecret=$api\nGameApiSecret=$api\n"
        file.writeText(original, Charsets.UTF_8)

        val result = ServerPropertiesSecretBootstrap.ensure(file)

        assertFalse(result.changed)
        assertTrue(result.alreadyValid)
        assertEquals(original, file.readText(Charsets.UTF_8))
    }

    @Test
    fun `copies a valid GameApiSecret into invalid SiteGameApiSecret`() {
        val session = secret("session")
        val api = secret("api")
        val file = tempDir.resolve("server.properties").toFile()
        file.writeText(
            "SiteSessionSecret=$session\nSiteGameApiSecret=changeme\nGameApiSecret=$api\n",
            Charsets.UTF_8,
        )

        val result = ServerPropertiesSecretBootstrap.ensure(file)
        val props = parse(file.readText(Charsets.UTF_8))

        assertEquals(listOf("SiteGameApiSecret"), result.regeneratedKeys)
        assertEquals(api, props.getValue("SiteGameApiSecret"))
        assertEquals(api, props.getValue("GameApiSecret"))
        assertEquals(session, props.getValue("SiteSessionSecret"))
    }

    @Test
    fun `appends missing keys under generated section`() {
        val file = tempDir.resolve("server.properties").toFile()
        file.writeText("Hostname = 127.0.0.1\n", Charsets.UTF_8)

        val result = ServerPropertiesSecretBootstrap.ensure(file)
        val updated = file.readText(Charsets.UTF_8)
        val props = parse(updated)

        assertTrue(result.changed)
        assertTrue(updated.contains("# === Auto-generated secrets (ServerPropertiesSecretBootstrap) ==="))
        assertValidBase64Secret(props.getValue("SiteSessionSecret"))
        assertValidBase64Secret(props.getValue("SiteGameApiSecret"))
        assertEquals(props.getValue("SiteGameApiSecret"), props.getValue("GameApiSecret"))
    }

    @Test
    fun `forceRotate generates fresh secrets even when existing secrets are valid`() {
        val oldSession = secret("old-session")
        val oldApi = secret("old-api")
        val file = tempDir.resolve("server.properties").toFile()
        val original = "SiteSessionSecret=$oldSession\r\nSiteGameApiSecret=$oldApi\r\nGameApiSecret=$oldApi\r\n"
        file.writeText(original, Charsets.UTF_8)

        val result = ServerPropertiesSecretBootstrap.rotate(file)
        assertTrue(result.changed)
        assertEquals(listOf("SiteSessionSecret", "SiteGameApiSecret", "GameApiSecret"), result.regeneratedKeys)

        val updated = file.readText(Charsets.UTF_8)
        assertTrue(updated.contains("\r\n"), "CRLF should be preserved")
        val props = parse(updated)
        val newSession = props.getValue("SiteSessionSecret")
        val newSiteApi = props.getValue("SiteGameApiSecret")
        val newGameApi = props.getValue("GameApiSecret")

        assertValidBase64Secret(newSession)
        assertValidBase64Secret(newSiteApi)
        assertValidBase64Secret(newGameApi)

        assertNotEquals(oldSession, newSession)
        assertNotEquals(oldApi, newSiteApi)
        assertEquals(newSiteApi, newGameApi)
        assertNotEquals(newSession, newGameApi)
    }

    private fun parse(text: String): Map<String, String> = text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }
        .toMap()

    private fun assertValidBase64Secret(value: String) {
        assertTrue(Base64.getDecoder().decode(value).size >= 32)
    }

    private fun secret(label: String): String = Base64.getEncoder().encodeToString(
        (label.padEnd(48, label.first())).toByteArray(Charsets.UTF_8),
    )
}
