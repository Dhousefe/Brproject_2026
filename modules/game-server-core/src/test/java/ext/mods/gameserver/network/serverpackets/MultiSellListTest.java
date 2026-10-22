package ext.mods.gameserver.network.serverpackets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ext.mods.gameserver.model.multisell.ListContainer;
import ext.mods.gameserver.model.multisell.PreparedListContainer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integridade e regressao para exibicao de [Multisell Path]: para GMs.
 */
public class MultiSellListTest
{
    @Test
    @DisplayName("ListContainer e PreparedListContainer devem manter o filePath do XML")
    public void testMultisellPathRetention()
    {
        final String path = "data/xml/multisell/1000.xml";
        ListContainer template = new ListContainer(1000, path);
        assertEquals(path, template.getFilePath());

        PreparedListContainer prepared = new PreparedListContainer(template, false, null, null);
        assertEquals(path, prepared.getFilePath(), "PreparedListContainer deve herdar o filePath do template");
    }

    @Test
    @DisplayName("MultiSellList deve executar runImpl com seguranca mesmo sem cliente")
    public void testMultiSellListRunImplWithoutClient()
    {
        ListContainer list = new ListContainer(1000, "data/xml/multisell/1000.xml");
        MultiSellList packetPage1 = new MultiSellList(list, 0);
        assertDoesNotThrow(() -> packetPage1.runImpl(), "runImpl de MultiSellList nao deve falhar com getClient() nulo");

        MultiSellList packetPage2 = new MultiSellList(list, 40);
        assertDoesNotThrow(() -> packetPage2.runImpl(), "Pagina 2 de MultiSellList deve ignorar sem falhar");
    }
}
