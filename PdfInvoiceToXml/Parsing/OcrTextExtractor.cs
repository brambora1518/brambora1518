using PDFtoImage;
using SkiaSharp;
using Tesseract;

namespace PdfInvoiceToXml.Parsing;

/// <summary>
/// Fallback used when a PDF has no extractable text layer at all (a scanned
/// or "printed to image" invoice). Renders each page to a bitmap (PDFtoImage,
/// via PDFium) and runs it through Tesseract OCR, then reuses the same
/// PdfLine/PdfWord shape as <see cref="PdfTextExtractor"/> so the rest of the
/// parsing pipeline doesn't need to know whether the text came from the PDF
/// itself or from OCR.
///
/// Lines come from Tesseract's own layout analysis rather than being
/// re-derived from the word boxes. Measured on the 354/26 scan, clustering
/// words by vertical position reconstructed 38 of 53 lines exactly, while
/// following the engine's text lines gets all 51 - the difference being stray
/// specks pulled in from the line above or below, which is how
/// "TATO FAKTURA SLOUŽÍ ZÁROVEŇ JAKO DODACÍ LIST" acquired a stray ":" twice.
/// Confidence is deliberately not used to filter those out: the specks scored
/// 0% and 24%, but genuine layout colons on the same page scored 5.8% and
/// 37.8%, so any threshold that removed the noise removed real content too.
///
/// Requires a Tesseract trained-data file (e.g. ces.traineddata) in the
/// tessdata folder passed in - see tessdata/README.txt.
/// </summary>
public static class OcrTextExtractor
{
    private const int Dpi = 300;

    public static List<PdfLine> ExtractLines(string pdfPath, string tessDataPath, string language = "ces")
    {
        var lines = new List<PdfLine>();

        using var engine = new TesseractEngine(tessDataPath, language, EngineMode.Default);
        using var pdfStream = File.OpenRead(pdfPath);

        var pageCount = Conversion.GetPageCount(pdfStream, leaveOpen: true);

        for (var pageIndex = 0; pageIndex < pageCount; pageIndex++)
        {
            pdfStream.Position = 0;
            using var bitmap = Conversion.ToImage(pdfStream, page: pageIndex, leaveOpen: true, options: new RenderOptions(Dpi: Dpi));
            using var pngData = bitmap.Encode(SKEncodedImageFormat.Png, 100);
            using var pix = Pix.LoadFromMemory(pngData.ToArray());
            // PageSegMode.SingleBlock ("uniform block of text") is essential here:
            // Tesseract's default automatic layout analysis tries to detect
            // separate columns/paragraphs on a dense invoice like this and ends
            // up reporting noticeably less consistent word bounding boxes, which
            // breaks the line-reconstruction clustering below entirely.
            using var ocrPage = engine.Process(pix, PageSegMode.SingleBlock);
            using var iter = ocrPage.GetIterator();

            var pageHeightPoints = bitmap.Height * 72.0 / Dpi;
            var pageWidthPoints = bitmap.Width * 72.0 / Dpi;

            var words = new List<PdfWord>();
            var lineOfWord = new List<int>();
            var lineIndex = 0;

            iter.Begin();
            do
            {
                // Checked before the word is read, so a word skipped below
                // still closes the line it started.
                if (words.Count > 0 && iter.IsAtBeginningOf(PageIteratorLevel.TextLine))
                {
                    lineIndex++;
                }

                if (!iter.TryGetBoundingBox(PageIteratorLevel.Word, out var box)) continue;
                var text = iter.GetText(PageIteratorLevel.Word)?.Trim();
                if (string.IsNullOrEmpty(text)) continue;

                words.Add(new PdfWord
                {
                    Text = text,
                    Left = box.X1 * 72.0 / Dpi,
                    Right = box.X2 * 72.0 / Dpi,
                    Top = pageHeightPoints - box.Y1 * 72.0 / Dpi
                });
                lineOfWord.Add(lineIndex);
            } while (iter.Next(PageIteratorLevel.Word));

            lines.AddRange(BuildLines(words, lineOfWord, pageIndex + 1, pageWidthPoints));
        }

        return lines
            .OrderBy(l => l.PageNumber)
            .ThenByDescending(l => l.Top)
            .ToList();
    }

    private static List<PdfLine> BuildLines(
        List<PdfWord> words, List<int> lineOfWord, int pageNumber, double pageWidth)
    {
        var grouped = words
            .Select((word, i) => (Word: word, Line: lineOfWord[i]))
            .GroupBy(x => x.Line)
            .Select(g => new PdfLine
            {
                PageNumber = pageNumber,
                PageWidth = pageWidth,
                Top = g.Max(x => x.Word.Top),
                Words = g.Select(x => x.Word).OrderBy(w => w.Left).ToList()
            })
            .ToList();

        // If the iterator never reported a line break, something about the
        // engine build is not what we expect - a whole page as one line would
        // wreck every regex downstream, so fall back to the geometric
        // clustering this used to do unconditionally.
        return grouped.Count <= 1 && words.Count > 20
            ? GroupIntoLines(words, pageNumber, pageWidth)
            : grouped;
    }

    /// <summary>
    /// Geometric fallback: clusters words whose vertical positions are close.
    /// Only used when Tesseract's own line structure is unavailable - it
    /// cannot tell a speck on the next line apart from a word on this one.
    /// </summary>
    private static List<PdfLine> GroupIntoLines(List<PdfWord> words, int pageNumber, double pageWidth)
    {
        var result = new List<PdfLine>();
        var ordered = words.OrderByDescending(w => w.Top).ToList();
        var used = new bool[ordered.Count];

        for (var i = 0; i < ordered.Count; i++)
        {
            if (used[i]) continue;

            var refTop = ordered[i].Top;
            var lineWords = new List<PdfWord>();

            for (var j = i; j < ordered.Count; j++)
            {
                if (used[j]) continue;
                if (Math.Abs(ordered[j].Top - refTop) <= 6.0)
                {
                    lineWords.Add(ordered[j]);
                    used[j] = true;
                }
            }

            result.Add(new PdfLine
            {
                PageNumber = pageNumber,
                Top = refTop,
                PageWidth = pageWidth,
                Words = lineWords.OrderBy(w => w.Left).ToList()
            });
        }

        return result;
    }
}
