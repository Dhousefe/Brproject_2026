package ext.mods.config;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import ext.mods.Config;
import ext.mods.commons.config.ExProperties;
import ext.mods.gameserver.data.manager.CountryLocaleManager;
import ext.mods.gameserver.enums.GeoType;
import ext.mods.gameserver.model.holder.IntIntHolder;
import ext.mods.gameserver.model.olympiad.enums.OlympiadPeriod;
import ext.mods.protection.hwid.crypt.FirstKey;

/**
 * Phase 4 config domain: ConfigDonation.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigDonation
{
   private ConfigDonation()
   {
   }

   public static boolean ENABLE_PIX_MOD;
   public static boolean ANNOUNCE_DONATOR_ITEM_GLOBAL;
   public static boolean DONATION_ENABLED;
   public static int DONATION_PURCHASABLE_ITEM;
   public static boolean DONATION_DELETE_INACTIVE;
   public static boolean DONATION_DELETE_PAYMENT_DATA;
   public static boolean DONATION_HIDE_ENDED;
   public static String DONATION_SERVER_NAME;
   public static boolean DONATION_CALCULATOR;
   public static boolean DONATION_DROPDOWN;
   public static boolean DONATION_REQUIRE_TERMS;
   public static String[] DONATION_ALLOWED_EMAILS;
   public static String DONATION_MP_TOKEN;
   public static String DONATION_MP_PIX_PRICE;
   public static boolean DONATION_MP_PIX;
   public static int DONATION_MP_PIX_EXPIRATION_TIME;
   public static boolean DONATION_MP_PIX_MAIL;
   public static String DONATION_MP_PIX_ACCOUNT_OWNER;
   public static String DONATION_MP_PIX_ACCOUNT_CPF;
   public static String DONATION_MP_PIX_ACCOUNT_BANK;
   public static int[] DONATION_MP_PIX_DROPDOWN;
   public static String DONATION_MP_LINK_PRICE;
   public static boolean DONATION_MP_LINK;
   public static boolean DONATION_MP_LINK_MAIL;
   public static String DONATION_MP_CURRENCY;
   public static String[] DONATION_MP_CURRENCIES;
   public static int DONATION_MP_LINK_EXPIRATION_TIME;
   public static int[] DONATION_MP_LINK_DROPDOWN;
   public static boolean DONATION_PAYPAL_LINK;
   public static String DONATION_PAYPAL_CLIENT_ID;
   public static String DONATION_PAYPAL_CLIENT_SECRET;
   public static String DONATION_PAYPAL_PRICE;
   public static boolean DONATION_PAYPAL_SANDBOX_ENABLED;
   public static String DONATION_PAYPAL_ACCOUNT_EMAIL;
   public static boolean DONATION_PAYPAL_MAIL;
   public static int DONATION_PAYPAL_LINK_EXPIRATION_TIME;
   public static String DONATION_PAYPAL_CURRENCY;
   public static String[] DONATION_PAYPAL_CURRENCIES;
   public static int[] DONATION_PAYPAL_DROPDOWN;
   public static String DONATION_PAYPAL_WEBSITE;
   public static String DONATION_PAYPAL_NOTE_MSG;
   public static String DONATION_PAYPAL_LOGO_IMAGE;
   public static String DONATION_PAYPAL_PHONE_CODE;
   public static String DONATION_PAYPAL_PHONE_NUMBER;
   public static boolean DONATION_BINANCE_PAY;
   public static String DONATION_BINANCE_API_KEY;
   public static String DONATION_BINANCE_SECRET_KEY;
   public static String DONATION_BINANCE_PRICE;
   public static String DONATION_BINANCE_FIAT_CURRENCY;
   public static String[] DONATION_BINANCE_PAY_CURRENCY;
   public static boolean DONATION_BINANCE_MAIL;
   public static int DONATION_BINANCE_EXPIRATION_TIME;
   public static int[] DONATION_BINANCE_DROPDOWN;
   public static int DONATION_BINANCE_CURRENCY_TASK_INTERVAL;
   public static String DONATION_CURRENCY_CB_API_KEY;
   public static boolean DONATION_CURRENCY_AWESOMEAPI;
   public static int DONATION_CURRENCY_TASK_INTERVAL;
   public static String DONATION_MAILER_TOKEN;
   public static String DONATION_MAILER_ADDRESS;
   public static String DONATION_MAILER_TEMPLATE;
   public static int DONATION_MAXIMUM_NUMBER_EMAILS;
   public static int DONATION_PAY_TIME;
   public static int DONATION_CHECK_TIME;

   public static void load() {
      ExProperties pix = Config.initProperties(Config.CONFIG_PATH.resolve("Pix.properties").toString());
      ConfigDonation.ENABLE_PIX_MOD = pix.getProperty("EnablePixMod", false);      ConfigDonation.ANNOUNCE_DONATOR_ITEM_GLOBAL = pix.getProperty("AnnounceDonatorItemGlobal", true);      ExProperties donation = Config.initProperties(Config.CONFIG_PATH.resolve("donation.properties").toString());
      ConfigDonation.DONATION_ENABLED = donation.getProperty("EnableDonationManager", false) && ConfigDonation.ENABLE_PIX_MOD;      ConfigDonation.DONATION_PURCHASABLE_ITEM = donation.getProperty("PurchasableItem", 0);      ConfigDonation.DONATION_DELETE_INACTIVE = donation.getProperty("DeleteInactivePurchases", false);      ConfigDonation.DONATION_DELETE_PAYMENT_DATA = donation.getProperty("DeletePaymentData", true);      ConfigDonation.DONATION_HIDE_ENDED = donation.getProperty("HideEndedPurchases", true);      ConfigDonation.DONATION_SERVER_NAME = donation.getProperty("ServerName", "Brproject");      ConfigDonation.DONATION_CALCULATOR = donation.getProperty("Calculator", true);      ConfigDonation.DONATION_DROPDOWN = donation.getProperty("ShowDropdown", true);      ConfigDonation.DONATION_REQUIRE_TERMS = donation.getProperty("RequireTerms", true);      ConfigDonation.DONATION_ALLOWED_EMAILS = Config.parseDonationStringArray(donation.getProperty("AllowedEmailAddresses", ""));      ConfigDonation.DONATION_MP_TOKEN = donation.getProperty("MercadoPagoApiToken", pix.getProperty("MercadoPagoApiToken", ""));      ConfigDonation.DONATION_MP_PIX_PRICE = donation.getProperty("MercadoPagoPixPrice", "1.00");      ConfigDonation.DONATION_MP_PIX = donation.getProperty("MercadoPagoPix", true);      ConfigDonation.DONATION_MP_PIX_EXPIRATION_TIME = donation.getProperty("MercadoPagoPixExpirationTime", 30);      ConfigDonation.DONATION_MP_PIX_MAIL = donation.getProperty("MercadoPagoPixMail", false);      ConfigDonation.DONATION_MP_PIX_ACCOUNT_OWNER = donation.getProperty("MercadoPagoPixAccountOwner", "");      ConfigDonation.DONATION_MP_PIX_ACCOUNT_CPF = donation.getProperty("MercadoPagoPixAccountCpf", "");      ConfigDonation.DONATION_MP_PIX_ACCOUNT_BANK = donation.getProperty("MercadoPagoPixAccountBank", "");      ConfigDonation.DONATION_MP_PIX_DROPDOWN = Config.parseDonationIntArray(donation.getProperty("MercadoPagoPixDropdown", "5,10,15,20,25"));      ConfigDonation.DONATION_MP_LINK_PRICE = donation.getProperty("MercadoPagoLinkPrice", "1.00");      ConfigDonation.DONATION_MP_LINK = donation.getProperty("MercadoPagoLink", false);      ConfigDonation.DONATION_MP_LINK_MAIL = donation.getProperty("MercadoPagoLinkMail", true);      ConfigDonation.DONATION_MP_CURRENCY = donation.getProperty("MercadoPagoCurrency", "BRL");      ConfigDonation.DONATION_MP_CURRENCIES = Config.parseDonationStringArray(donation.getProperty("MercadoPagoCurrencies", ""));      ConfigDonation.DONATION_MP_LINK_EXPIRATION_TIME = donation.getProperty("MercadoPagoLinkExpirationTime", 30);      ConfigDonation.DONATION_MP_LINK_DROPDOWN = Config.parseDonationIntArray(donation.getProperty("MercadoPagoLinkDropdown", "5,10,15,20,25"));      ConfigDonation.DONATION_PAYPAL_LINK = donation.getProperty("PayPalLink", false);      ConfigDonation.DONATION_PAYPAL_CLIENT_ID = donation.getProperty("PayPalClientId", "");      ConfigDonation.DONATION_PAYPAL_CLIENT_SECRET = donation.getProperty("PayPalClientSecret", "");      ConfigDonation.DONATION_PAYPAL_PRICE = donation.getProperty("PayPalPrice", "2.00");      ConfigDonation.DONATION_PAYPAL_SANDBOX_ENABLED = donation.getProperty("PayPalSandboxEnabled", true);      ConfigDonation.DONATION_PAYPAL_ACCOUNT_EMAIL = donation.getProperty("PayPalAccountEmail", "");      ConfigDonation.DONATION_PAYPAL_MAIL = donation.getProperty("PayPalMail", true);      ConfigDonation.DONATION_PAYPAL_LINK_EXPIRATION_TIME = donation.getProperty("PayPalLinkExpirationTime", 30);      ConfigDonation.DONATION_PAYPAL_CURRENCY = donation.getProperty("PayPalCurrency", "BRL");      ConfigDonation.DONATION_PAYPAL_CURRENCIES = Config.parseDonationStringArray(donation.getProperty("PayPalCurrencies", ""));      ConfigDonation.DONATION_PAYPAL_DROPDOWN = Config.parseDonationIntArray(donation.getProperty("PayPalDropdown", "5,10,15,20,25"));      ConfigDonation.DONATION_PAYPAL_WEBSITE = donation.getProperty("PayPalWebSite", "");      ConfigDonation.DONATION_PAYPAL_NOTE_MSG = donation.getProperty("PayPalNoteMsg", "");      ConfigDonation.DONATION_PAYPAL_LOGO_IMAGE = donation.getProperty("PayPalLogoImage", "");      ConfigDonation.DONATION_PAYPAL_PHONE_CODE = donation.getProperty("PayPalPhoneCountryCode", "");      ConfigDonation.DONATION_PAYPAL_PHONE_NUMBER = donation.getProperty("PayPalPhoneNumber", "");      ConfigDonation.DONATION_BINANCE_PAY = donation.getProperty("BinancePay", false);      ConfigDonation.DONATION_BINANCE_API_KEY = donation.getProperty("BinanceApiKey", "");      ConfigDonation.DONATION_BINANCE_SECRET_KEY = donation.getProperty("BinanceSecretKey", "");      ConfigDonation.DONATION_BINANCE_PRICE = donation.getProperty("BinancePrice", "1.00");      ConfigDonation.DONATION_BINANCE_FIAT_CURRENCY = donation.getProperty("BinanceFiatCurrency", "BRL");      ConfigDonation.DONATION_BINANCE_PAY_CURRENCY = Config.parseDonationStringArray(donation.getProperty("BinancePayCurrency", ""));      ConfigDonation.DONATION_BINANCE_MAIL = donation.getProperty("BinanceMail", true);      ConfigDonation.DONATION_BINANCE_EXPIRATION_TIME = donation.getProperty("BinanceExpirationTime", 30);      ConfigDonation.DONATION_BINANCE_DROPDOWN = Config.parseDonationIntArray(donation.getProperty("BinanceDropdown", "5,10,15,20,25"));      ConfigDonation.DONATION_BINANCE_CURRENCY_TASK_INTERVAL = donation.getProperty("BinanceCurrencyTaskInterval", 5);      ConfigDonation.DONATION_CURRENCY_CB_API_KEY = donation.getProperty("CurrencyBeaconApiKey", "");      ConfigDonation.DONATION_CURRENCY_AWESOMEAPI = donation.getProperty("AwesomeApi", true);      ConfigDonation.DONATION_CURRENCY_TASK_INTERVAL = donation.getProperty("FiatCurrencyTaskInterval", 20);      ConfigDonation.DONATION_MAILER_TOKEN = donation.getProperty("MailerApiToken", "");      ConfigDonation.DONATION_MAILER_ADDRESS = donation.getProperty("MailerAddress", "");      ConfigDonation.DONATION_MAILER_TEMPLATE = donation.getProperty("MailerTemplateId", "");      ConfigDonation.DONATION_MAXIMUM_NUMBER_EMAILS = donation.getProperty("MaximumNumberEmails", 2);      ConfigDonation.DONATION_PAY_TIME = donation.getProperty("PayTime", pix.getProperty("PayTime", 10000));      ConfigDonation.DONATION_CHECK_TIME = donation.getProperty("CheckTime", pix.getProperty("CheckTime", 5000));   }
}
