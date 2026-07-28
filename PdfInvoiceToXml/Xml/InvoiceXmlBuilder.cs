using System.Globalization;
using System.Xml.Linq;
using PdfInvoiceToXml.Models;

namespace PdfInvoiceToXml.Xml;

/// <summary>
/// Builds a DocumentPack XML matching the KASTNER software s.r.o. "IMPORT"
/// format, verified field-by-field against a real STEREO NX export of
/// invoice 354/26.
///
/// Two things that reference export settles, which the earlier guesswork got
/// wrong: it carries NO per-item detail at all (no DocumentRows, NumberOfLines
/// is 0 - the document is imported at the VAT-summary level), and empty values
/// are written as self-closing elements rather than an empty start/end pair.
///
/// A couple of values are not printed anywhere on a typical invoice PDF and
/// cannot be derived from it (the internal VAT-type and VAT-source codes) -
/// those use the configurable defaults below.
/// </summary>
public static class InvoiceXmlBuilder
{
    private const string SoftwareVendor = "KASTNER software s.r.o.";
    private const string SoftwareProduct = "IMPORT";

    private const string DefaultTypeOfVat = "U";
    private const string DefaultVatSource = "TaxableValue";

    /// <summary>
    /// 0308 is the Czech constant symbol for payments for goods and services.
    /// Invoices like the 354/26 sample print no constant symbol anywhere on the
    /// page - the STEREO export still carries one because it comes from the
    /// accounting system's own settings, not from the document. A symbol the
    /// parser did find on the page always wins over this default.
    /// </summary>
    private const string DefaultConstantSymbol = "0308";

    public static XDocument Build(InvoiceData invoice)
    {
        var document = new XElement("Document",
            new XAttribute("Version", "2.0"),
            new XElement("DocumentHeader",
                Text("DocumentID", invoice.DocumentId),
                Text("TypeOfDocument", "Invoice"),
                Text("DocumentCaption", "FAKTURA")),
            BuildSupplier(invoice.Supplier),
            BuildBuyer(invoice.Buyer),
            BuildEmptyDeliveryTo(),
            new XElement("Issue",
                Text("IssueDate", FormatDateTime(invoice.IssueDate))),
            new XElement("Payment",
                Text("PaymentType", invoice.PaymentType),
                Text("DueDate", FormatDate(invoice.DueDate)),
                Text("CurrencyCode", invoice.CurrencyCode),
                Text("BankAccount", invoice.BankAccount),
                Text("BankCode", invoice.BankCode),
                Text("VariableSymbol", invoice.VariableSymbol),
                Text("ConstantSymbol", string.IsNullOrEmpty(invoice.ConstantSymbol)
                    ? DefaultConstantSymbol
                    : invoice.ConstantSymbol),
                Text("CurrencyRate", "1.00"),
                Text("CurrencyAmount", "1")),
            BuildVatInfo(invoice),
            BuildDocumentTotals(invoice),
            Text("TextAbove", invoice.TextAbove));

        var pack = new XElement("DocumentPack",
            new XAttribute(XNamespace.Xmlns + "xsi", "http://www.w3.org/2001/XMLSchema-instance"),
            new XAttribute(XNamespace.Xmlns + "xsd", "http://www.w3.org/2001/XMLSchema"),
            new XElement("HEADER",
                new XElement("Source",
                    Text("SoftwareVendor", SoftwareVendor),
                    Text("SoftwareProduct", SoftwareProduct))),
            new XElement("PARAMETERS"),
            new XElement("DOCUMENTS", document));

        return new XDocument(new XDeclaration("1.0", "utf-8", null), pack);
    }

    private static XElement BuildSupplier(PartyInfo p) =>
        new XElement("Suplier", // sic - matches the target schema's own (mis-)spelling
            new XElement("Person", Text("LastName", p.PersonName)),
            BuildAddress(p),
            BuildCompany(p));

    private static XElement BuildBuyer(PartyInfo p) =>
        new XElement("Buyer",
            BuildAddress(p),
            BuildCompany(p));

    private static XElement BuildAddress(PartyInfo p) =>
        new XElement("Address",
            Text("Street", p.Street),
            Text("City", p.City),
            Text("PostalCode", p.PostalCode),
            Text("State", p.State));

    private static XElement BuildCompany(PartyInfo p) =>
        new XElement("Company",
            Text("Company", p.CompanyName),
            Text("CompanyRegistrationNo", p.RegistrationNo),
            Text("CompanyTaxRegistrationNo", p.TaxRegistrationNo),
            Text("VATPayer", p.VatPayer ? "true" : "false"));

    private static XElement BuildEmptyDeliveryTo() =>
        new XElement("DeliveryTo",
            new XElement("Person", new XElement("LastName")),
            new XElement("Address",
                new XElement("Street"),
                new XElement("City"),
                new XElement("PostalCode"),
                new XElement("State")));

    private static XElement BuildVatInfo(InvoiceData invoice)
    {
        var el = new XElement("VatInfo",
            Text("TypeOfVAT", DefaultTypeOfVat),
            Text("VatDate", FormatDate(invoice.VatDate ?? invoice.IssueDate)),
            Text("TaxVoucher", invoice.IsTaxVoucher ? "true" : "false"),
            Text("VatSource", DefaultVatSource));

        foreach (var row in invoice.VatTable)
        {
            el.Add(new XElement("VATTableRow",
                Text("VATRate", FormatInteger(row.Rate)),
                Text("VATRateCaption", $"{FormatInteger(row.Rate)}%"),
                Text("TotalTaxableAtRate", FormatAmount(row.TaxableTotal)),
                Text("VATAtRate", FormatAmount(row.VatTotal)),
                Text("TotalWithVAT", FormatAmount(row.TotalWithVat))));
        }

        return el;
    }

    private static XElement BuildDocumentTotals(InvoiceData invoice) =>
        new XElement("DocumentTotals",
            // The reference export carries no item rows, so this is always 0.
            Text("NumberOfLines", "0"),
            Text("NumberOfVATRates", invoice.VatTable.Count.ToString(CultureInfo.InvariantCulture)),
            Text("TaxableTotal", FormatAmount(invoice.GrandTotal)),
            Text("VatTotal", FormatAmount(invoice.VatTotal)),
            Text("NetTotal", FormatAmount(invoice.NetTotal)),
            Text("AdvancePaymentTotal", "0"),
            Text("NetPaymentTotal", FormatAmount(invoice.NetTotal)),
            Text("NetPaymentTotalRounding", FormatAmount(invoice.RoundedGrandTotal - invoice.GrandTotal)),
            Text("NetPaymentTotalRounded", FormatInteger(invoice.RoundedGrandTotal)),
            Text("TypeOfOperation", ""),
            Text("TypeOfVAT", DefaultTypeOfVat),
            Text("ProcessVAT", "true"),
            Text("ReverseCharge", invoice.ReverseCharge ? "true" : "false"));

    /// <summary>
    /// Empty values have to serialise as &lt;Foo /&gt;, the way the reference
    /// STEREO export writes them. Handing XElement an empty string instead
    /// produces &lt;Foo&gt;&lt;/Foo&gt;, which is what the old code did.
    /// </summary>
    private static XElement Text(string name, string? value) =>
        string.IsNullOrEmpty(value) ? new XElement(name) : new XElement(name, value);

    private static string FormatDateTime(DateTime? dt) =>
        dt.HasValue ? dt.Value.ToString("yyyy-MM-ddTHH:mm:ss.ff", CultureInfo.InvariantCulture) : "";

    private static string FormatDate(DateTime? dt) =>
        dt.HasValue ? dt.Value.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture) : "";

    /// <summary>
    /// Money as the reference export writes it: up to two decimals with
    /// trailing zeros dropped, so 16596.00 is written as "16596" while
    /// 3485.16 keeps both places.
    /// </summary>
    private static string FormatAmount(decimal value) =>
        value.ToString("0.##", CultureInfo.InvariantCulture);

    private static string FormatInteger(decimal value) =>
        value.ToString("F0", CultureInfo.InvariantCulture);
}
