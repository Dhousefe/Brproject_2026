package ext.mods.gameserver.network.serverpackets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de integridade, robustez e regressao para o pacote NpcHtmlMessage.
 */
public class NpcHtmlMessageTest
{
    @Test
    @DisplayName("runImpl deve executar com seguranca mesmo sem GameClient anexado")
    public void testRunImplWithoutClient()
    {
        NpcHtmlMessage msg = new NpcHtmlMessage(1001);
        msg.setHtml("<html><body>Hello <a action=\"bypass -h npc_1001_Chat 1\">Next</a></body></html>");

        assertDoesNotThrow(() -> msg.runImpl(),
            "runImpl nao deve lancar NullPointerException quando getClient() for nulo");
    }

    @Test
    @DisplayName("runImpl deve executar com seguranca se _html for nulo")
    public void testRunImplWithNullHtml()
    {
        NpcHtmlMessage msg = new NpcHtmlMessage(1001);
        assertDoesNotThrow(() -> msg.runImpl(),
            "runImpl nao deve lancar NullPointerException quando _html for nulo");
    }

    @Test
    @DisplayName("replace deve suportar value nulo sem lancar NullPointerException")
    public void testReplaceWithNullValue()
    {
        NpcHtmlMessage msg = new NpcHtmlMessage(1001);
        msg.setHtml("<html><body>Clan: %clanName%</body></html>");

        assertDoesNotThrow(() -> msg.replace("%clanName%", (String) null),
            "replace nao deve lancar NPE quando value for nulo");

        assertEquals("<html><body>Clan: </body></html>", msg.getContent());
    }

    @Test
    @DisplayName("replace deve tratar corretamente caracteres sensiveis como $ e contra-barra")
    public void testReplaceWithDollarAndBackslash()
    {
        NpcHtmlMessage msg = new NpcHtmlMessage(1001);
        msg.setHtml("<html><body>Cost: %cost% Path: %path%</body></html>");

        msg.replace("%cost%", "$10,000 $USD");
        msg.replace("%path%", "C:\\L2\\System");

        assertEquals("<html><body>Cost: $10,000 $USD Path: C:\\L2\\System</body></html>", msg.getContent());
    }

    @Test
    @DisplayName("Parser de bypass deve tolerar HTML truncado ou com bypass malformado sem estourar limites")
    public void testMalformedBypassParsing()
    {
        NpcHtmlMessage msg = new NpcHtmlMessage(1001);
        msg.setHtml("<html><body>Test \"bypass -h");

        assertDoesNotThrow(() -> msg.runImpl(),
            "runImpl nao deve lancar StringIndexOutOfBoundsException com bypass incompleto");
    }

    @Test
    @DisplayName("setFile deve preservar o path completo do arquivo sem truncamento para administradores")
    public void testSetFilePreservesFullPath()
    {
        NpcHtmlMessage msg = new NpcHtmlMessage(1001);
        final String fullPath = "data/html/teleporter/30001.htm";
        msg.setFile(java.util.Locale.ENGLISH, fullPath);

        assertEquals(fullPath, msg.getFileName(),
            "getFileName() deve retornar o path completo especificado sem truncar a raiz html/");
    }

    @Test
    @DisplayName("NpcHtmlMessage deve capturar automaticamente a classe Java que fabricou o pacote")
    public void testBuilderClassCapturing()
    {
        NpcHtmlMessage msg = new NpcHtmlMessage(1001);
        assertNotNull(msg.getBuilderClass(), "BuilderClass nao deve ser nula");
        assertTrue(msg.getBuilderClass().contains("NpcHtmlMessageTest"),
            "A classe capturada deve ser o proprio teste chamador NpcHtmlMessageTest: " + msg.getBuilderClass());
    }

    @Test
    @DisplayName("Diferenciacao entre HTML baseado em arquivo fisico e HTML fabricado por classe Java")
    public void testFileVsDynamicHtmlDistinction()
    {
        // Cenário 1: Baseado em arquivo fisico .htm
        NpcHtmlMessage fileMsg = new NpcHtmlMessage(1001);
        fileMsg.setFile(java.util.Locale.ENGLISH, "data/html/teleporter/30001.htm");
        assertEquals("data/html/teleporter/30001.htm", fileMsg.getFileName());
        assertFalse(fileMsg.isDynamicHtml(), "HTML de arquivo fisico nao deve ser marcado como dinamico");

        // Cenário 2: Fabricado em classe Java dinamicamente sem arquivo
        NpcHtmlMessage dynamicMsg = new NpcHtmlMessage(0);
        dynamicMsg.setHtml("<html><body>Custom Quest Recommender</body></html>");
        assertTrue(dynamicMsg.isDynamicHtml(), "HTML injetado via setHtml deve ser marcado como dinamico");
        assertNotNull(dynamicMsg.getBuilderClass(), "HTML fabricado em Java deve capturar o builder caller");
        assertTrue(dynamicMsg.getBuilderClass().endsWith(".java") || dynamicMsg.getBuilderClass().contains(".java:"),
            "O builder caller deve conter o path do arquivo .java: " + dynamicMsg.getBuilderClass());
    }
}
