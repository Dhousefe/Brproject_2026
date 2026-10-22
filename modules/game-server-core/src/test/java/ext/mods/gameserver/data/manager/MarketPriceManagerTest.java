package ext.mods.gameserver.data.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integridade e validacao anti-arbitragem para o MarketPriceManager.
 */
public class MarketPriceManagerTest
{
    @Test
    @DisplayName("MarketPriceManager deve inicializar limpo e retornar 0 para item nao compravel")
    public void testNonBuyableItemReturnsZero()
    {
        MarketPriceManager manager = MarketPriceManager.getInstance();
        assertNotNull(manager);
        assertEquals(0, manager.getMinBuyPrice(-9999), "Item inexistente deve retornar 0 (sem teto anti-arbitragem)");
    }
}
