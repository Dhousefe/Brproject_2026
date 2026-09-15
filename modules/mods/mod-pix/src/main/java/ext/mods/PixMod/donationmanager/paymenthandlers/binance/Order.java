/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager.paymenthandlers.binance;

public class Order
{
    public Env env;
    public String merchantTradeNo;
    public String orderAmount; // double
    public String currency;
    public String fiatAmount; // double
    public String fiatCurrency;
    public GoodsDetail[] goodsDetails;
    public Buyer buyer;
    public long orderExpireTime;
    public String supportPayCurrency;
    public String description;

    static class Env
    {
        public String terminalType;
    }

    static class GoodsDetail
    {
        public String goodsType;
        public String goodsCategory;
        public String referenceGoodsId;
        public String goodsName;
        public String goodsDetail;
        public GoodsUnitAmount goodsUnitAmount;
    }

    static class GoodsUnitAmount
    {
        public String currency;
        public Double amount;
    }

    static class Buyer
    {
        public BuyerNamer buyerName;
        public String buyerEmail;
    }

    static class BuyerNamer
    {
        public String firstName;
        public String lastName;
    }
}
