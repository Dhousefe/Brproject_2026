/*
 * Copyright (c) Dev A.L.N. Todos os direitos reservados.
 */

package ext.mods.PixMod.donationmanager.paymenthandlers.paypal;

public class Invoice
{
    public Detail detail;
    public Invoicer invoicer;
    public Recipient[] primary_recipients;
    public Item[] items;
    
    static class Invoicer
    {
    	public String business_name;
        public Name name;
        public Address address;
        public String email_address;
        public Phone[] phones;
        public String website;
        public String tax_id;
        public String logo_url;
        public String additional_notes;
    }
    
    static class Recipient
    {
        public BillingInfo billing_info;
        public ShippingInfo shipping_info;
    }
    
    static class Item
    {
        public String name;
        public String description;
        public String quantity;
        public UnitAmount unit_amount;
        public Tax tax;
        public Discount discount;
        public String unit_of_measure;
    }
    
    static class Detail
    {
        public String currency_code;
        public String invoice_number;
        public String reference;
        public String invoice_date;
        public String note;
        public String term;
        public String memo;
        public PaymentTerm payment_term;
    }
    
    static class PaymentTerm
    {
        public String term_type;
        public String due_date;
    }
    
    static class Name
    {
        public String given_name;
        public String surname;
    }
    
    static class Address
    {
        public String address_line_1;
        public String address_line_2;
        public String admin_area_2;
        public String admin_area_1;
        public String postal_code;
        public String country_code;
    }
    
    static class Phone
    {
        public String country_code;
        public String national_number;
        public String phone_type;
    }
    
    static class BillingInfo
    {
    	public String business_name;
        public Name name;
        public Address address;
        public String email_address;
        public Phone[] phones;
        public String additional_info_value;
    }
    
    static class ShippingInfo
    {
        public Name name;
        public Address address;
    }
    
    static class UnitAmount
    {
        public String currency_code;
        public String value;
    }
    
    static class Tax
    {
        public String name;
        public String percent;
        public String tax_note;
    }
    
    static class Discount
    {
        public String percent;
        public Amount amount;
    }
    
    static class Amount
    {
        public String currency_code;
        public String value;
    }
}
