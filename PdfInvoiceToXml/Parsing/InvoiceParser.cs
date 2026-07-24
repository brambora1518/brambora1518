using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using PdfInvoiceToXml.Models;

namespace PdfInvoiceToXml.Parsing;

/// <summary>
/// Best-effort parser for Czech invoice PDFs. It was built against a CÉZAR G1
/// export (DODAVATEL / ODBĚRATEL side-by-side header, IČO/DIČ, table with
/// Číslo/Název zboží/DPH/Jedn./MNOŽSTVÍ/PC bez DPH/DPH celkem/ČÁSTKA columns),
/// but relies on commonly-used Czech invoice labels rather than fixed
/// coordinates, so it should tolerate reasonably similar layouts from other
/// accounting systems too. Label/value gaps use lazy, digit-tolerant regexes
/// throughout because OCR (see <see cref="OcrTextExtractor"/>) sometimes
/// misreads a colon as a stray digit.
/// </summary>
public static class InvoiceParser
{
    public static InvoiceData Parse(string pdfPath)
    {
        var lines = PdfTextExtractor.ExtractLines(pdfPath);
        var usedOcr = false;

        if (lines.Count == 0)
        {
            // No text layer at all - most likely a scanned/"printed to image" PDF.
            // Fall back to rendering the page and running OCR over it.
            var tessDataPath = Path.Combine(AppContext.BaseDirectory, "tessdata");
            if (!File.Exists(Path.Combine(tessDataPath, "ces.traineddata")))
            {
                throw new InvalidOperationException(
                    "V PDF se nenašel žádný text (jde o naskenovaný/obrázkový dokument) a chybí OCR data. " +
                    "Stáhněte ces.traineddata podle tessdata/README.txt a umístěte ho vedle .exe do složky tessdata.");
            }

            lines = OcrTextExtractor.ExtractLines(pdfPath, tessDataPath);
            usedOcr = true;

            if (lines.Count == 0)
            {
                throw new InvalidOperationException(
                    "V PDF se nenašel žádný text ani pomocí OCR - dokument se nepodařilo rozpoznat.");
            }
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
            DocumentId = Match1(headerText, @"DOKLAD.{0,5}?([\w/\-]+)")
                         ?? Match1(headerText, @"FAKTURA\s*(?:č\.?|číslo)?.{0,5}?([\w/\-]+)")
                         ?? "",
            Supplier = ParseParty(leftText, isSupplier: true),
            Buyer = ParseParty(rightText, isSupplier: false),
            IssueDate = FindDate(headerText, @"Datum\s+vystaven\S*\s+dokladu"),
            VatDate = FindDate(headerText, @"Datum\s+uskut\S*"),
            DueDate = FindDate(headerText, @"[Ss]platnosti"),
            VariableSymbol = Match1(headerText, @"[Vv]ariabiln\S*\s+symbol.{0,10}?(\d{3,})") ?? "",
            PaymentType = MapPaymentType(Match1(headerText, @"[ZF]orm\S*\s+[úu]hrady.{0,10}?([\w.]+)")),
            TextAbove = Match1(fullText, @"(TATO FAKTURA[^\n]*)") ?? "",
            WasOcr = usedOcr,
        };

        var account = Regex.Match(headerText, @"[ČC]íslo\s+[úu][čc]tu.{0,10}?([\d\-]{5,})\s*/\s*(\d{3,4})");
        if (account.Success)
        {
            invoice.BankAccount = account.Groups[1].Value;
            invoice.BankCode = account.Groups[2].Value;
        }

        invoice.VatTable = ExtractVatTable(lines);
        invoice.Items = itemsHeaderIdx >= 0 ? ExtractItems(lines, itemsHeaderIdx) : new List<InvoiceItem>();

        // Requiring a real decimal amount (not just any digit) on the line
        // matters because OCR sometimes splits "Forma úhrady: ... CELKEM K
        // ÚHRADĚ: 4.217,00" into two separate lines: an earlier label-only
        // line ("Forma úhrady : CELKEM -:") whose only "number" is a stray
        // digit from a misread colon, and the real total further down. A
        // bare-digit match on the label line would grab that misread colon
        // instead of the actual total.
        var totalLine = lines.FirstOrDefault(l =>
            l.Text.Contains("CELKEM", StringComparison.OrdinalIgnoreCase) &&
            Regex.IsMatch(l.Text, "[ÚU]HRAD", RegexOptions.IgnoreCase) &&
            Regex.IsMatch(l.Text, @"\d[\d.,]*[.,]\d{2}(?!\d)"));
        // Take the *last* number-shaped token on the line rather than anchoring
        // to the end of the string - OCR sometimes appends stray junk after
        // the amount (logos, QR-code labels, ...).
        var numberTokens = totalLine != null ? Regex.Matches(totalLine.Text, @"\d[\d.,]*\d|\d") : null;
        invoice.RoundedGrandTotal = numberTokens is { Count: > 0 }
            ? ParseCzDecimal(numberTokens[^1].Value)
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

    // Stray characters OCR tends to hallucinate around table borders/boxes
    // (vertical dividers, box corners) - stripped from both ends of every line.
    private static readonly char[] LineNoiseChars = { ' ', '|', '[', ']', '(', ')', '{', '}', '!', '"', '\'' };

    private static PartyInfo ParseParty(string blockText, bool isSupplier)
    {
        var party = new PartyInfo { State = "CZ" };

        var ico = Match1(blockText, @"I[ČC]O.{0,5}?(\d{6,10})");
        var dic = Match1(blockText, @"DI[ČC].{0,5}?([A-Z]{0,2}\d{6,12})");
        party.RegistrationNo = ico ?? "";
        party.TaxRegistrationNo = dic ?? "";
        party.VatPayer = !string.IsNullOrEmpty(dic);

        var allLines = blockText
            .Split('\n')
            .Select(l => l.Trim(LineNoiseChars))
            .Where(l => l.Length > 0)
            .ToList();

        // Only look at what comes *after* the DODAVATEL/ODBĚRATEL label itself,
        // so an unrelated line above it (e.g. the invoice title) in the same
        // column can't be mistaken for the party's name. Matched fuzzily
        // (edit distance, not exact regex) since OCR can garble the label
        // beyond simple punctuation noise (e.g. "ODBĚRATEL" -> "ovBĚRArEL").
        var labelTarget = isSupplier ? "DODAVATEL" : "ODBERATEL";
        var labelIdx = allLines.FindIndex(l => LooksLikeLabel(l, labelTarget));
        var lines = labelIdx >= 0 ? allLines.Skip(labelIdx + 1).ToList() : allLines;

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
            .Select(TrimLeadingNoise)
            .Where(l => l.Length > 0)
            .ToList();

        party.Street = TrimLeadingNoise(street);
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

    // Fuzzy label match: strips everything but letters, removes diacritics,
    // and allows a small edit distance so a badly-OCR'd label (a couple of
    // misread characters) still gets recognised.
    private static bool LooksLikeLabel(string line, string target)
    {
        var letters = RemoveDiacritics(new string(line.Where(char.IsLetter).ToArray())).ToUpperInvariant();
        if (letters.Length == 0) return false;
        var window = letters.Length > target.Length ? letters[..target.Length] : letters;
        var maxDistance = target.Length >= 8 ? 2 : 1;
        return LevenshteinDistance(window, target) <= maxDistance;
    }

    private static string RemoveDiacritics(string text)
    {
        var normalized = text.Normalize(NormalizationForm.FormD);
        var sb = new StringBuilder();
        foreach (var c in normalized)
        {
            if (CharUnicodeInfo.GetUnicodeCategory(c) != UnicodeCategory.NonSpacingMark)
                sb.Append(c);
        }
        return sb.ToString().Normalize(NormalizationForm.FormC);
    }

    private static int LevenshteinDistance(string a, string b)
    {
        var dp = new int[a.Length + 1, b.Length + 1];
        for (var i = 0; i <= a.Length; i++) dp[i, 0] = i;
        for (var j = 0; j <= b.Length; j++) dp[0, j] = j;

        for (var i = 1; i <= a.Length; i++)
        {
            for (var j = 1; j <= b.Length; j++)
            {
                var cost = a[i - 1] == b[j - 1] ? 0 : 1;
                dp[i, j] = Math.Min(Math.Min(dp[i - 1, j] + 1, dp[i, j - 1] + 1), dp[i - 1, j - 1] + cost);
            }
        }

        return dp[a.Length, b.Length];
    }

    // Real street/city/company text always starts with a letter or digit -
    // strips a leading run of stray punctuation OCR sometimes bleeds in from
    // an adjacent line (e.g. a trailing ":" from the label line above).
    private static string TrimLeadingNoise(string s) => Regex.Replace(s.Trim(), @"^[^\p{L}\d]+", "");

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

    // Column X-position anchoring turned out to be too fragile once OCR is
    // involved: OCR often merges adjacent header labels ("PC bez DPH" ->
    // "PCbezDPH") or drops one entirely, so the header row can't reliably be
    // used to locate columns. Instead each item row is parsed from its own
    // token shapes, right to left: Amount, VATAmount, UnitPrice and Quantity
    // are the trailing numeric tokens; before them comes Unit (short word)
    // then VAT rate ("21%"); everything else between the leading item number
    // and that block is the item name. This works the same whether the line
    // came from real PDF text or from OCR.
    private static readonly Regex NumericTokenPattern = new(@"^-?\d[\d.,]*-?$");
    private static readonly Regex VatRateTokenPattern = new(@"^\d{1,2}%$");
    private static readonly Regex UnitTokenPattern = new(@"^\p{L}{1,6}\.?$");
    private static readonly Regex TableNoisePattern = new(@"^[|\[\](){}]+$");

    private static List<InvoiceItem> ExtractItems(List<PdfLine> lines, int headerIdx)
    {
        var items = new List<InvoiceItem>();

        for (var i = headerIdx + 1; i < lines.Count; i++)
        {
            var line = lines[i];
            var text = line.Text.Trim();
            if (text.Length == 0) continue;
            if (Regex.IsMatch(text, @"^(CELKEM|SLEVA)\b", RegexOptions.IgnoreCase)) break;

            var tokens = line.Words
                .Select(w => w.Text)
                .Where(t => !TableNoisePattern.IsMatch(t))
                .ToList();

            // OCR sometimes misreads the table's vertical divider lines as a
            // stray single letter (e.g. "l" or "I") rather than "|", leaving
            // one extra bogus token in front of the real item number.
            if (tokens.Count >= 2 && tokens[0].Length == 1 &&
                !Regex.IsMatch(tokens[0], @"^\d$") && Regex.IsMatch(tokens[1], @"^\d+$"))
            {
                tokens.RemoveAt(0);
            }

            if (tokens.Count == 0 || !Regex.IsMatch(tokens[0], @"^\d+$")) continue;

            items.Add(ParseItemRow(tokens));
        }

        return items;
    }

    private static InvoiceItem ParseItemRow(List<string> tokens)
    {
        var number = tokens[0];
        var rest = tokens.Skip(1).ToList();

        // A trailing run of non-numeric tokens (if any) is the note column.
        var noteTokens = new List<string>();
        while (rest.Count > 0 && !NumericTokenPattern.IsMatch(rest[^1]))
        {
            noteTokens.Insert(0, rest[^1]);
            rest.RemoveAt(rest.Count - 1);
        }

        // Up to 4 trailing numeric tokens: Quantity, UnitPrice, VATAmount, Amount (left to right).
        var trailing = new List<string>();
        var idx = rest.Count - 1;
        while (idx >= 0 && trailing.Count < 4 && NumericTokenPattern.IsMatch(rest[idx]))
        {
            trailing.Insert(0, rest[idx]);
            idx--;
        }
        while (trailing.Count < 4) trailing.Insert(0, "");

        var unit = "";
        if (idx >= 0 && UnitTokenPattern.IsMatch(rest[idx]) && !VatRateTokenPattern.IsMatch(rest[idx]))
        {
            unit = rest[idx];
            idx--;
        }

        var vatRateRaw = "";
        if (idx >= 0)
        {
            if (VatRateTokenPattern.IsMatch(rest[idx]))
            {
                vatRateRaw = rest[idx];
                idx--;
            }
            else if (rest[idx] == "%" && idx - 1 >= 0 && Regex.IsMatch(rest[idx - 1], @"^\d{1,2}$"))
            {
                vatRateRaw = rest[idx - 1] + "%";
                idx -= 2;
            }
        }

        var name = string.Join(" ", rest.Take(idx + 1));

        return new InvoiceItem
        {
            Number = number,
            Name = name,
            VatRate = ParseCzDecimal(Regex.Match(vatRateRaw, @"\d+").Value),
            Unit = unit,
            Quantity = ParseCzDecimal(trailing[0]),
            UnitPriceNoVat = ParseCzDecimal(trailing[1]),
            VatAmount = ParseCzDecimal(trailing[2]),
            Amount = ParseCzDecimal(trailing[3]),
            Note = string.Join(" ", noteTokens)
        };
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
        // Lazy, digit-tolerant gap: OCR sometimes misreads the ":" after a
        // label as a stray digit, so a gap that forbids digits (like
        // "[^\d]*") can fail to bridge across it. Laziness still keeps the
        // match anchored to the *nearest* date after the keyword.
        var m = Regex.Match(text, keywordPattern + @".{0,25}?(\d{1,2}\.\d{1,2}\.\d{2,4})", RegexOptions.IgnoreCase);
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
        // Czech "no decimals" notation, e.g. "171,-" - OCR sometimes drops the
        // comma too ("171-"), so strip a trailing dash outright rather than
        // only the ",-" pair.
        cleaned = Regex.Replace(cleaned, @"-$", "");
        cleaned = Regex.Replace(cleaned, @"[.,]$", "");
        cleaned = Regex.Replace(cleaned, @"\.(?=\d{3}(\D|$))", "");
        cleaned = cleaned.Replace(",", ".");
        cleaned = Regex.Replace(cleaned, @"[^\d.\-]", "");
        return decimal.TryParse(cleaned, NumberStyles.Any, CultureInfo.InvariantCulture, out var val) ? val : 0m;
    }
}
