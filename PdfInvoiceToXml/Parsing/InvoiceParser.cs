using System.Globalization;
using System.Text.RegularExpressions;
using PdfInvoiceToXml.Models;

namespace PdfInvoiceToXml.Parsing;

/// <summary>
/// Best-effort parser for Czech invoice PDFs. It was built against a CÉZAR G1
/// export (DODAVATEL / ODBĚRATEL side-by-side header, IČO/DIČ, table with
/// Číslo/Název zboží/DPH/Jedn./MNOŽSTVÍ/PC bez DPH/DPH celkem/ČÁSTKA columns),
/// but relies on commonly-used Czech invoice labels rather than fixed
/// coordinates, so it should tolerate reasonably similar layouts from other
/// accounting systems too. Genuinely different layouts (e.g. no two-column
/// DODAVATEL/ODBĚRATEL header, or a differently labelled items table) may
/// need the column-alias lists below extended.
/// </summary>
public static class InvoiceParser
{
    // Order matters: more specific (multi-word) labels must be checked before
    // shorter labels they happen to contain as a substring (e.g. "DPH celkem"
    // and "PC bez DPH" both contain "dph", which alone means the VAT-rate
    // column - so the compound labels are listed first).
    private static readonly (string[] Keys, string Field)[] ItemColumnAliases =
    {
        (new[] { "dph celkem" }, "VatAmount"),
        (new[] { "pc bez dph", "cena bez dph" }, "UnitPrice"),
        (new[] { "číslo", "cislo" }, "Number"),
        (new[] { "název", "nazev", "položka", "polozka", "popis" }, "Name"),
        (new[] { "dph", "sazba" }, "VatRate"),
        (new[] { "jedn" }, "Unit"),
        (new[] { "množství", "mnozstvi" }, "Quantity"),
        (new[] { "částka", "castka" }, "Amount"),
        (new[] { "pozn" }, "Note"),
    };

    public static InvoiceData Parse(string pdfPath)
    {
        var lines = PdfTextExtractor.ExtractLines(pdfPath);
        if (lines.Count == 0)
        {
            throw new InvalidOperationException(
                "V PDF se nenašel žádný text – jde pravděpodobně o naskenovaný obrázek bez textové vrstvy (OCR), který tento nástroj neumí přečíst.");
        }

        var itemsHeaderIdx = lines.FindIndex(l =>
            l.Text.Contains("Název", StringComparison.OrdinalIgnoreCase) &&
            l.Text.Contains("žství", StringComparison.OrdinalIgnoreCase));

        var headerLines = itemsHeaderIdx >= 0 ? lines.Take(itemsHeaderIdx).ToList() : lines;
        var headerText = string.Join("\n", headerLines.Select(l => l.Text));
        var (leftText, rightText) = SplitColumns(headerLines);
        var fullText = string.Join("\n", lines.Select(l => l.Text));

        var invoice = new InvoiceData
        {
            DocumentId = Match1(headerText, @"DOKLAD\s*:?\s*([\w/\-]+)")
                         ?? Match1(headerText, @"FAKTURA\s*(?:č\.?|číslo)?\s*:?\s*([\w/\-]+)")
                         ?? "",
            Supplier = ParseParty(leftText, isSupplier: true),
            Buyer = ParseParty(rightText, isSupplier: false),
            IssueDate = FindDate(headerText, @"Datum\s+vystaven\S*\s+dokladu"),
            VatDate = FindDate(headerText, @"Datum\s+uskut\S*"),
            DueDate = FindDate(headerText, @"[Ss]platnosti"),
            VariableSymbol = Match1(headerText, @"[Vv]ariabiln\S*\s+symbol\s*:?\s*(\d+)") ?? "",
            PaymentType = MapPaymentType(Match1(headerText, @"[ZF]orm\S*\s+[úu]hrady\s*:?\s*([\w.]+)")),
            TextAbove = Match1(fullText, @"(TATO FAKTURA[^\n]*)") ?? "",
        };

        var account = Regex.Match(headerText, @"[ČC]íslo\s+[úu][čc]tu\s*:?\s*([\d\-]+)\s*/\s*(\d{3,4})");
        if (account.Success)
        {
            invoice.BankAccount = account.Groups[1].Value;
            invoice.BankCode = account.Groups[2].Value;
        }

        invoice.VatTable = ExtractVatTable(lines);
        invoice.Items = itemsHeaderIdx >= 0 ? ExtractItems(lines, itemsHeaderIdx) : new List<InvoiceItem>();

        var totalLine = lines.FirstOrDefault(l =>
            l.Text.Contains("CELKEM", StringComparison.OrdinalIgnoreCase) &&
            Regex.IsMatch(l.Text, "[ÚU]HRAD", RegexOptions.IgnoreCase));
        var totalMatch = totalLine != null ? Regex.Match(totalLine.Text, @"(\d[\d.,\s]*\d)\s*$") : Match.Empty;
        invoice.RoundedGrandTotal = totalMatch.Success
            ? ParseCzDecimal(totalMatch.Groups[1].Value)
            : Math.Round(invoice.VatTable.Sum(v => v.TotalWithVat), 0, MidpointRounding.AwayFromZero);

        invoice.IsTaxVoucher = fullText.Contains("DAŇOV", StringComparison.OrdinalIgnoreCase);
        invoice.ReverseCharge = fullText.Contains("přenesen", StringComparison.OrdinalIgnoreCase);

        return invoice;
    }

    private static (string Left, string Right) SplitColumns(List<PdfLine> lines)
    {
        var left = new List<string>();
        var right = new List<string>();

        foreach (var line in lines)
        {
            var midX = line.PageWidth / 2.0;
            var leftWords = line.Words.Where(w => w.Left < midX).Select(w => w.Text);
            var rightWords = line.Words.Where(w => w.Left >= midX).Select(w => w.Text);
            left.Add(string.Join(" ", leftWords));
            right.Add(string.Join(" ", rightWords));
        }

        return (string.Join("\n", left), string.Join("\n", right));
    }

    private static PartyInfo ParseParty(string blockText, bool isSupplier)
    {
        var party = new PartyInfo { State = "CZ" };

        var ico = Match1(blockText, @"I[ČC]O\s*:?\s*(\d{6,10})");
        var dic = Match1(blockText, @"DI[ČC]\s*:?\s*([A-Z]{0,2}\d{6,12})");
        party.RegistrationNo = ico ?? "";
        party.TaxRegistrationNo = dic ?? "";
        party.VatPayer = !string.IsNullOrEmpty(dic);

        var lines = blockText
            .Split('\n')
            .Select(l => l.Trim())
            .Where(l => l.Length > 0)
            .Where(l => !Regex.IsMatch(l, @"^(DODAVATEL|ODB[ĚE]RATEL)\s*:?\s*$", RegexOptions.IgnoreCase))
            .ToList();

        var zipLineIdx = lines.FindIndex(l => Regex.IsMatch(l, @"^\d{3}\s?\d{2}\s+\S"));

        string street = "", city = "", zip = "";
        List<string> nameLines;

        if (zipLineIdx >= 0)
        {
            var m = Regex.Match(lines[zipLineIdx], @"^(\d{3})\s?(\d{2})\s+(.+)$");
            zip = m.Groups[1].Value + m.Groups[2].Value;
            city = m.Groups[3].Value.Trim();
            street = zipLineIdx - 1 >= 0 ? lines[zipLineIdx - 1] : "";
            nameLines = lines.Take(Math.Max(zipLineIdx - 1, 0)).ToList();
        }
        else
        {
            nameLines = lines.Take(Math.Min(2, lines.Count)).ToList();
        }

        nameLines = nameLines
            .Where(l => !Regex.IsMatch(l, @"^(I[ČC]O|DI[ČC]|Tel|Email|E-?mail)\s*:", RegexOptions.IgnoreCase))
            .ToList();

        party.Street = street;
        party.City = city;
        party.PostalCode = zip;

        if (nameLines.Count == 0)
        {
            party.PersonName = "";
            party.CompanyName = "";
        }
        else if (nameLines.Count == 1)
        {
            party.PersonName = isSupplier ? nameLines[0] : "";
            party.CompanyName = nameLines[0];
        }
        else
        {
            party.PersonName = nameLines[0];
            party.CompanyName = string.Join(" - ", nameLines);
        }

        return party;
    }

    private static List<VatRateSummary> ExtractVatTable(List<PdfLine> lines)
    {
        var result = new List<VatRateSummary>();

        foreach (var line in lines)
        {
            var m = Regex.Match(line.Text.Trim(), @"^(\d{1,2})\s*%\s*:?\s*([\d.,\s]+)$");
            if (!m.Success) continue;

            var rate = decimal.Parse(m.Groups[1].Value, CultureInfo.InvariantCulture);
            var numbers = Regex.Matches(m.Groups[2].Value, @"[\d][\d.,]*")
                .Select(x => ParseCzDecimal(x.Value))
                .ToList();
            if (numbers.Count == 0) continue;

            var taxable = numbers.ElementAtOrDefault(0);
            var vat = numbers.ElementAtOrDefault(1);
            if (taxable == 0m && vat == 0m) continue;

            result.Add(new VatRateSummary
            {
                Rate = rate,
                TaxableTotal = taxable,
                VatTotal = vat,
                TotalWithVat = taxable + vat
            });
        }

        return result;
    }

    private static List<InvoiceItem> ExtractItems(List<PdfLine> lines, int headerIdx)
    {
        var items = new List<InvoiceItem>();
        var headerLine = lines[headerIdx];

        // Merge adjacent words into header "cells" first (column labels like
        // "DPH celkem" or "Název zboží" are printed as 2+ separate PDF words
        // with normal spacing; a real column boundary leaves a much wider gap).
        var cells = GroupHeaderCells(headerLine.Words);

        var anchors = new List<(double X, string Field)>();
        foreach (var (x, text) in cells)
        {
            var lw = text.ToLowerInvariant();
            var field = ItemColumnAliases
                .Where(a => a.Keys.Any(k => lw.Contains(k)))
                .Select(a => a.Field)
                .FirstOrDefault();

            if (field != null && anchors.All(a => a.Field != field))
            {
                anchors.Add((x, field));
            }
        }

        if (anchors.Count < 3) return items;
        anchors = anchors.OrderBy(a => a.X).ToList();

        for (var i = headerIdx + 1; i < lines.Count; i++)
        {
            var line = lines[i];
            var text = line.Text.Trim();
            if (text.Length == 0) continue;
            if (Regex.IsMatch(text, @"^(CELKEM|SLEVA)\b", RegexOptions.IgnoreCase)) break;

            var firstWord = line.Words.FirstOrDefault()?.Text ?? "";
            if (!Regex.IsMatch(firstWord, @"^\d+$")) continue;

            var columns = anchors.ToDictionary(a => a.Field, _ => new List<string>());

            foreach (var word in line.Words)
            {
                var field = anchors[0].Field;
                for (var a = 0; a < anchors.Count; a++)
                {
                    var upperBound = a + 1 < anchors.Count
                        ? (anchors[a].X + anchors[a + 1].X) / 2
                        : double.MaxValue;
                    if (word.Left < upperBound)
                    {
                        field = anchors[a].Field;
                        break;
                    }
                }
                columns[field].Add(word.Text);
            }

            string Get(string f) => columns.TryGetValue(f, out var v) ? string.Join(" ", v) : "";

            items.Add(new InvoiceItem
            {
                Number = Get("Number"),
                Name = Get("Name"),
                VatRate = ParseCzDecimal(Regex.Match(Get("VatRate"), @"\d+").Value),
                Unit = Get("Unit"),
                Quantity = ParseCzDecimal(Get("Quantity")),
                UnitPriceNoVat = ParseCzDecimal(Get("UnitPrice")),
                VatAmount = ParseCzDecimal(Get("VatAmount")),
                Amount = ParseCzDecimal(Get("Amount")),
                Note = Get("Note")
            });
        }

        return items;
    }

    private static List<(double X, string Text)> GroupHeaderCells(List<PdfWord> words)
    {
        var cells = new List<(double X, string Text)>();
        if (words.Count == 0) return cells;

        var cellX = words[0].Left;
        var cellWords = new List<string> { words[0].Text };
        var prevRight = words[0].Right;

        for (var i = 1; i < words.Count; i++)
        {
            var w = words[i];
            if (w.Left - prevRight > 8.0)
            {
                cells.Add((cellX, string.Join(" ", cellWords)));
                cellX = w.Left;
                cellWords = new List<string>();
            }

            cellWords.Add(w.Text);
            prevRight = w.Right;
        }

        cells.Add((cellX, string.Join(" ", cellWords)));
        return cells;
    }

    private static string MapPaymentType(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return "BankTransfer";
        var lower = raw.ToLowerInvariant();
        if (lower.Contains("hotov")) return "Cash";
        if (lower.Contains("kart")) return "Card";
        if (lower.Contains("dobírk") || lower.Contains("dobirk")) return "CashOnDelivery";
        return "BankTransfer";
    }

    private static string? Match1(string text, string pattern)
    {
        var m = Regex.Match(text, pattern, RegexOptions.IgnoreCase);
        return m.Success ? m.Groups[1].Value.Trim() : null;
    }

    private static DateTime? FindDate(string text, string keywordPattern)
    {
        var m = Regex.Match(text, keywordPattern + @"[^\d\n]{0,20}(\d{1,2}\.\d{1,2}\.\d{2,4})", RegexOptions.IgnoreCase);
        return m.Success ? ParseCzDate(m.Groups[1].Value) : null;
    }

    private static DateTime? ParseCzDate(string s)
    {
        s = s.Trim();
        string[] formats = { "d.M.yyyy", "dd.MM.yyyy", "d.M.yy", "dd.MM.yy" };
        return DateTime.TryParseExact(s, formats, CultureInfo.InvariantCulture, DateTimeStyles.None, out var dt)
            ? dt
            : null;
    }

    private static decimal ParseCzDecimal(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return 0m;
        var cleaned = s.Replace(" ", "").Replace("Kč", "", StringComparison.OrdinalIgnoreCase).Trim();
        cleaned = cleaned.Replace(",-", ",00"); // Czech "no decimals" notation, e.g. "171,-"
        cleaned = Regex.Replace(cleaned, @"\.(?=\d{3}(\D|$))", "");
        cleaned = cleaned.Replace(",", ".");
        cleaned = Regex.Replace(cleaned, @"[^\d.\-]", "");
        return decimal.TryParse(cleaned, NumberStyles.Any, CultureInfo.InvariantCulture, out var val) ? val : 0m;
    }
}
