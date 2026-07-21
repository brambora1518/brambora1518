using System.Globalization;
using System.Xml.Linq;
using PdfInvoiceToXml.Models;

namespace PdfInvoiceToXml.Xml;

/// <summary>
/// Builds a DocumentPack XML matching the KASTNER software s.r.o. "IMPORT"
/// format sample we were given. A few values are not printed anywhere on a
/// typical invoice PDF and can't be derived from it (constant symbol, the
/// internal VAT-type/VAT-source codes) - those use the configurable defaults
/// below. Per-item detail (DocumentRows/DocumentRow) is NOT part of the
/// confirmed sample (which had NumberOfLines=0 and no item rows at all); the
/// element names here are a best-effort guess mirroring the PDF's own column
/// headers. If the target importer rejects them, either drop the
/// BuildDocumentRows() call in Build() or rename the elements to match
/// KASTNER's real schema once you have it.
/// </summary>
public static class InvoiceXmlBuilder
{
    private const string SoftwareVendor = "KASTNER software s.r.o.";
    private const string SoftwareProduct = "IMPORT";

    private const string DefaultConstantSymbol = "";
    private const string DefaultTypeOfVat = "U";
    private const string DefaultVatSource = "TaxableValue";

    public static XDocument Build(InvoiceData invoice)
    {
        var document = new XElement("Document",
            new XAttribute("Version", "2.0"),
            new XElement("DocumentHeader",
                new XElement("DocumentID", invoice.DocumentId),
                new XElement("TypeOfDocument", "Invoice"),
                new XElement("DocumentCaption", "FAKTURA")),
            BuildSupplier(invoice.Supplier),
            BuildBuyer(invoice.Buyer),
            BuildEmptyDeliveryTo(),
            new XElement("Issue",
                new XElement("IssueDate", FormatDateTime(invoice.IssueDate))),
            new XElement("Payment",
                new XElement("PaymentType", invoice.PaymentType),
                new XElement("DueDate", FormatDate(invoice.DueDate)),
                new XElement("CurrencyCode", invoice.CurrencyCode),
                new XElement("BankAccount", invoice.BankAccount),
                new XElement("BankCode", invoice.BankCode),
                new XElement("VariableSymbol", invoice.VariableSymbol),
                new XElement("ConstantSymbol", string.IsNullOrEmpty(invoice.ConstantSymbol) ? DefaultConstantSymbol : invoice.ConstantSymbol),
                new XElement("CurrencyRate", "1.00"),
                new XElement("CurrencyAmount", "1")),
            BuildVatInfo(invoice),
            BuildDocumentRows(invoice.Items),
            BuildDocumentTotals(invoice),
            new XElement("TextAbove", invoice.TextAbove));

        var pack = new XElement("DocumentPack",
            new XAttribute(XNamespace.Xmlns + "xsi", "http://www.w3.org/2001/XMLSchema-instance"),
            new XAttribute(XNamespace.Xmlns + "xsd", "http://www.w3.org/2001/XMLSchema"),
            new XElement("HEADER",
                new XElement("Source",
                    new XElement("SoftwareVendor", SoftwareVendor),
                    new XElement("SoftwareProduct", SoftwareProduct))),
            new XElement("PARAMETERS"),
            new XElement("DOCUMENTS", document));

        return new XDocument(new XDeclaration("1.0", "utf-8", null), pack);
    }

    private static XElement BuildSupplier(PartyInfo p) =>
        new XElement("Suplier", // sic - matches the target schema's own (mis-)spelling
            new XElement("Person", new XElement("LastName", p.PersonName)),
            BuildAddress(p),
            BuildCompany(p));

    private static XElement BuildBuyer(PartyInfo p) =>
        new XElement("Buyer",
            BuildAddress(p),
            BuildCompany(p));

    private static XElement BuildAddress(PartyInfo p) =>
        new XElement("Address",
            new XElement("Street", p.Street),
            new XElement("City", p.City),
            new XElement("PostalCode", p.PostalCode),
            new XElement("State", p.State));

    private static XElement BuildCompany(PartyInfo p) =>
        new XElement("Company",
            new XElement("Company", p.CompanyName),
            new XElement("CompanyRegistrationNo", p.RegistrationNo),
            new XElement("CompanyTaxRegistrationNo", p.TaxRegistrationNo),
            new XElement("VATPayer", p.VatPayer ? "true" : "false"));

    private static XElement BuildEmptyDeliveryTo() =>
        new XElement("DeliveryTo",
            new XElement("Person", new XElement("LastName", "")),
            new XElement("Address",
                new XElement("Street", ""),
                new XElement("City", ""),
                new XElement("PostalCode", ""),
                new XElement("State", "")));

    private static XElement BuildVatInfo(InvoiceData invoice)
    {
        var el = new XElement("VatInfo",
            new XElement("TypeOfVAT", DefaultTypeOfVat),
            new XElement("VatDate", FormatDate(invoice.VatDate ?? invoice.IssueDate)),
            new XElement("TaxVoucher", invoice.IsTaxVoucher ? "true" : "false"),
            new XElement("VatSource", DefaultVatSource));

        foreach (var row in invoice.VatTable)
        {
            el.Add(new XElement("VATTableRow",
                new XElement("VATRate", FormatNumber(row.Rate, 0)),
                new XElement("VATRateCaption", $"{FormatNumber(row.Rate, 0)}%"),
                new XElement("TotalTaxableAtRate", FormatNumber(row.TaxableTotal, 2)),
                new XElement("VATAtRate", FormatNumber(row.VatTotal, 2)),
                new XElement("TotalWithVAT", FormatNumber(row.TotalWithVat, 2))));
        }

        return el;
    }

    private static XElement BuildDocumentRows(List<InvoiceItem> items)
    {
        var el = new XElement("DocumentRows");
        foreach (var item in items)
        {
            el.Add(new XElement("DocumentRow",
                new XElement("Number", item.Number),
                new XElement("Name", item.Name),
                new XElement("VATRate", FormatNumber(item.VatRate, 0)),
                new XElement("Unit", item.Unit),
                new XElement("Quantity", FormatNumber(item.Quantity, 2)),
                new XElement("UnitPrice", FormatNumber(item.UnitPriceNoVat, 2)),
                new XElement("VATAmount", FormatNumber(item.VatAmount, 2)),
                new XElement("Amount", FormatNumber(item.Amount, 2)),
                new XElement("Note", item.Note)));
        }
        return el;
    }

    private static XElement BuildDocumentTotals(InvoiceData invoice) =>
        new XElement("DocumentTotals",
            new XElement("NumberOfLines", invoice.Items.Count),
            new XElement("NumberOfVATRates", invoice.VatTable.Count),
            new XElement("TaxableTotal", FormatNumber(invoice.GrandTotal, 2)),
            new XElement("VatTotal", FormatNumber(invoice.VatTotal, 2)),
            new XElement("NetTotal", FormatNumber(invoice.NetTotal, 2)),
            new XElement("AdvancePaymentTotal", "0"),
            new XElement("NetPaymentTotal", FormatNumber(invoice.NetTotal, 2)),
            new XElement("NetPaymentTotalRounding", FormatNumber(invoice.RoundedGrandTotal - invoice.GrandTotal, 2)),
            new XElement("NetPaymentTotalRounded", FormatNumber(invoice.RoundedGrandTotal, 0)),
            new XElement("TypeOfOperation", ""),
            new XElement("TypeOfVAT", DefaultTypeOfVat),
            new XElement("ProcessVAT", "true"),
            new XElement("ReverseCharge", invoice.ReverseCharge ? "true" : "false"));

    private static string FormatDateTime(DateTime? dt) =>
        dt.HasValue ? dt.Value.ToString("yyyy-MM-ddTHH:mm:ss.ff", CultureInfo.InvariantCulture) : "";

    private static string FormatDate(DateTime? dt) =>
        dt.HasValue ? dt.Value.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture) : "";

    private static string FormatNumber(decimal value, int decimals) =>
        value.ToString("F" + decimals, CultureInfo.InvariantCulture);
}
