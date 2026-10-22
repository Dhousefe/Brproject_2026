package ext.mods.gameserver.data.manager;

import ext.mods.commons.logging.CLogger;
import ext.mods.gameserver.data.xml.MultisellData;
import ext.mods.gameserver.model.buylist.NpcBuyList;
import ext.mods.gameserver.model.buylist.Product;
import ext.mods.gameserver.model.multisell.Entry;
import ext.mods.gameserver.model.multisell.Ingredient;
import ext.mods.gameserver.model.multisell.ListContainer;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * Gerenciador de Preço Mínimo de Mercado (Anti-Arbitragem de Adena).
 * <p>
 * Mapeia itemId -> MENOR preço de compra em adena em todo o servidor (varrendo todas as
 * buylists de NPC e multisells). Serve como teto dinâmico para a venda de itens ao NPC
 * e na Community Board (ver RequestSellItem e SellList).
 * <p>
 * Garante com Mechanical Sympathy e coleções primitivas Fastutil (Int2IntOpenHashMap)
 * que nenhum item possa ser revendido ao NPC por valor superior a 50% do menor preço
 * pelo qual pode ser adquirido, fechando permanentemente exploits de multiplicação infinita de adena.
 */
public final class MarketPriceManager
{
    private static final CLogger LOGGER = new CLogger(MarketPriceManager.class.getName());
    private static final int ADENA_ID = 57;

    private final Int2IntOpenHashMap _minBuyPrice = new Int2IntOpenHashMap();

    protected MarketPriceManager()
    {
        _minBuyPrice.defaultReturnValue(0);
        build();
    }

    public synchronized void build()
    {
        _minBuyPrice.clear();

        // 1) Varredura de Buylists de NPC (preço direto em adena)
        for (NpcBuyList list : BuyListManager.getInstance().getBuyLists())
        {
            for (Product p : list.values())
            {
                if (p.getPrice() > 0)
                {
                    consider(p.getItemId(), p.getPrice());
                }
            }
        }

        // 2) Varredura de Multisells (considera a parte exigida em adena rateada pelos produtos)
        for (ListContainer lc : MultisellData.getInstance().getLists())
        {
            for (Entry e : lc.getEntries())
            {
                long adenaReq = 0;
                for (Ingredient ing : e.getIngredients())
                {
                    if (ing.getItemId() == ADENA_ID)
                    {
                        adenaReq += ing.getItemCount();
                    }
                }

                if (adenaReq <= 0)
                    continue;

                int totalProduced = 0;
                for (Ingredient prod : e.getProducts())
                {
                    totalProduced += Math.max(prod.getItemCount(), 1);
                }

                if (totalProduced <= 0)
                    continue;

                final int perUnit = (int) Math.max(1, adenaReq / totalProduced);
                for (Ingredient prod : e.getProducts())
                {
                    consider(prod.getItemId(), perUnit);
                }
            }
        }

        LOGGER.info("MarketPriceManager: Menor preco de compra mapeado para {} itens (Buylists + Multisells).", _minBuyPrice.size());
    }

    private void consider(int itemId, int price)
    {
        if (price <= 0)
            return;

        final int current = _minBuyPrice.get(itemId);
        if (current == 0 || price < current)
        {
            _minBuyPrice.put(itemId, price);
        }
    }

    /**
     * @param itemId ID do item a consultar
     * @return Menor preço em adena pelo qual o item pode ser comprado, ou 0 se não for comprável em loja
     */
    public int getMinBuyPrice(int itemId)
    {
        return _minBuyPrice.get(itemId);
    }

    public void reload()
    {
        build();
    }

    public static MarketPriceManager getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder
    {
        protected static final MarketPriceManager INSTANCE = new MarketPriceManager();
    }
}
