namespace PdfInvoiceToXml.Models;

public class PartyInfo
{
    public string PersonName { get; set; } = "";
    public string CompanyName { get; set; } = "";
    public string Street { get; set; } = "";
    public string City { get; set; } = "";
    public string PostalCode { get; set; } = "";
    public string State { get; set; } = "CZ";
    public string RegistrationNo { get; set; } = "";
    public string TaxRegistrationNo { get; set; } = "";
    public bool VatPayer { get; set; }
}

public class InvoiceItem
{
    public string Number { get; set; } = "";
    public string Name { get; set; } = "";
    public decimal VatRate { get; set; }
    public string Unit { get; set; } = "";
    public decimal Quantity { get; set; }
    public decimal UnitPriceNoVat { get; set; }
    public decimal VatAmount { get; set; }
    public decimal Amount { get; set; }
    public string Note { get; set; } = "";
}

public class VatRateSummary
{
    public decimal Rate { get; set; }
    public decimal TaxableTotal { get; set; }
    public decimal VatTotal { get; set; }
    public decimal TotalWithVat { get; set; }
}

public class InvoiceData
{
    public string DocumentId { get; set; } = "";
    public PartyInfo Supplier { get; set; } = new();
    public PartyInfo Buyer { get; set; } = new();

    public DateTime? IssueDate { get; set; }
    public DateTime? DueDate { get; set; }
    public DateTime? VatDate { get; set; }

    public string PaymentType { get; set; } = "BankTransfer";
    public string CurrencyCode { get; set; } = "Kč";
    public string BankAccount { get; set; } = "";
    public string BankCode { get; set; } = "";
    public string VariableSymbol { get; set; } = "";
    public string ConstantSymbol { get; set; } = "";

    public List<InvoiceItem> Items { get; set; } = new();
    public List<VatRateSummary> VatTable { get; set; } = new();

    public bool IsTaxVoucher { get; set; } = true;
    public bool ReverseCharge { get; set; }
    public string TextAbove { get; set; } = "";

    /// <summary>Total without VAT, summed across VAT-rate rows.</summary>
    public decimal NetTotal => VatTable.Sum(v => v.TaxableTotal);

    /// <summary>Total VAT, summed across VAT-rate rows.</summary>
    public decimal VatTotal => VatTable.Sum(v => v.VatTotal);

    /// <summary>Unrounded total including VAT.</summary>
    public decimal GrandTotal => NetTotal + VatTotal;

    /// <summary>Total including VAT, rounded to whole currency units as printed on the invoice.</summary>
    public decimal RoundedGrandTotal { get; set; }
}
