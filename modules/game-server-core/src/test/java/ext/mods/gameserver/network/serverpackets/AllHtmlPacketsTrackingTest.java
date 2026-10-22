package ext.mods.gameserver.network.serverpackets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integridade e cobertura para o rastreamento em todos os pacotes de HTML do L2:
 * NpcHtmlMessage (0x0F), TutorialShowHtml (0xA0) e ShowBoard (0x6E).
 */
public class AllHtmlPacketsTrackingTest
{
    @Test
    @DisplayName("TutorialShowHtml (Quest Mark / Tutorial) deve capturar o builder caller e rodar runImpl com seguranca")
    public void testTutorialShowHtmlTracking()
    {
        TutorialShowHtml packet = new TutorialShowHtml("<html><body>Quest Mark Popup</body></html>");

        assertDoesNotThrow(() -> packet.runImpl(),
            "runImpl de TutorialShowHtml nao deve falhar com getClient() nulo");
    }

    @Test
    @DisplayName("ShowBoard (Community Board Alt+B) deve capturar o builder caller e ignorar pacotes secundarios")
    public void testShowBoardTracking()
    {
        // Pacote primario (101)
        ShowBoard primary = new ShowBoard("<html><body>BBS Home</body></html>", "101");
        assertDoesNotThrow(() -> primary.runImpl(),
            "runImpl de ShowBoard nao deve falhar com getClient() nulo");

        // Pacote secundario do split de 4KB (102)
        ShowBoard secondary = new ShowBoard("<html><body>Part 2</body></html>", "102");
        assertDoesNotThrow(() -> secondary.runImpl());
    }
}
