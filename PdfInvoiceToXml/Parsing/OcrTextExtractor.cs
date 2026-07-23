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
            iter.Begin();
            do
            {
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
            } while (iter.Next(PageIteratorLevel.Word));

            lines.AddRange(GroupIntoLines(words, pageIndex + 1, pageWidthPoints));
        }

        return lines
            .OrderBy(l => l.PageNumber)
            .ThenByDescending(l => l.Top)
            .ToList();
    }

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
