using UglyToad.PdfPig;

namespace PdfInvoiceToXml.Parsing;

public class PdfWord
{
    public string Text { get; init; } = "";
    public double Left { get; init; }
    public double Right { get; init; }
    public double Top { get; init; }
}

public class PdfLine
{
    public int PageNumber { get; init; }
    public double Top { get; init; }
    public double PageWidth { get; init; }
    public List<PdfWord> Words { get; init; } = new();
    public string Text => string.Join(" ", Words.Select(w => w.Text));
}

/// <summary>
/// Turns a PDF's positioned words back into reading-order lines, so that
/// label/value pairs and table columns can be recovered with plain regexes
/// instead of relying on the raw (often column-scrambled) text stream.
/// </summary>
public static class PdfTextExtractor
{
    public static List<PdfLine> ExtractLines(string pdfPath)
    {
        var lines = new List<PdfLine>();

        using var document = PdfDocument.Open(pdfPath);
        foreach (var page in document.GetPages())
        {
            var words = page.GetWords()
                .Where(w => !string.IsNullOrWhiteSpace(w.Text))
                .Select(w => new PdfWord
                {
                    Text = w.Text,
                    Left = w.BoundingBox.Left,
                    Right = w.BoundingBox.Right,
                    Top = w.BoundingBox.Top
                })
                .OrderByDescending(w => w.Top)
                .ToList();

            var used = new bool[words.Count];
            for (var i = 0; i < words.Count; i++)
            {
                if (used[i]) continue;

                var refTop = words[i].Top;
                var lineWords = new List<PdfWord>();

                for (var j = i; j < words.Count; j++)
                {
                    if (used[j]) continue;
                    if (Math.Abs(words[j].Top - refTop) <= 3.0)
                    {
                        lineWords.Add(words[j]);
                        used[j] = true;
                    }
                }

                lines.Add(new PdfLine
                {
                    PageNumber = page.Number,
                    Top = refTop,
                    PageWidth = page.Width,
                    Words = lineWords.OrderBy(w => w.Left).ToList()
                });
            }
        }

        return lines
            .OrderBy(l => l.PageNumber)
            .ThenByDescending(l => l.Top)
            .ToList();
    }
}
