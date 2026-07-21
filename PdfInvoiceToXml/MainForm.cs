using System.Text;
using System.Windows.Forms;
using System.Xml;
using PdfInvoiceToXml.Parsing;
using PdfInvoiceToXml.Xml;

namespace PdfInvoiceToXml;

public class MainForm : Form
{
    private readonly ListBox _logList = new();
    private readonly Label _hintLabel = new();

    public MainForm()
    {
        Text = "PDF faktura -> XML (KASTNER IMPORT)";
        Width = 760;
        Height = 480;
        AllowDrop = true;

        _hintLabel.Dock = DockStyle.Top;
        _hintLabel.Height = 60;
        _hintLabel.Padding = new Padding(10);
        _hintLabel.Text = "Přetáhněte sem jednu nebo více PDF faktur." + Environment.NewLine +
                           "Vedle každého PDF souboru vznikne odpovídající .xml.";
        _hintLabel.AllowDrop = true;

        _logList.Dock = DockStyle.Fill;
        _logList.AllowDrop = true;
        _logList.HorizontalScrollbar = true;

        Controls.Add(_logList);
        Controls.Add(_hintLabel);

        DragEnter += Form_DragEnter;
        DragDrop += Form_DragDrop;
        _hintLabel.DragEnter += Form_DragEnter;
        _hintLabel.DragDrop += Form_DragDrop;
        _logList.DragEnter += Form_DragEnter;
        _logList.DragDrop += Form_DragDrop;
    }

    private void Form_DragEnter(object? sender, DragEventArgs e)
    {
        e.Effect = e.Data?.GetDataPresent(DataFormats.FileDrop) == true
            ? DragDropEffects.Copy
            : DragDropEffects.None;
    }

    private void Form_DragDrop(object? sender, DragEventArgs e)
    {
        if (e.Data?.GetData(DataFormats.FileDrop) is not string[] files) return;

        foreach (var file in files.Where(f => f.EndsWith(".pdf", StringComparison.OrdinalIgnoreCase)))
        {
            ConvertFile(file);
        }
    }

    private void ConvertFile(string pdfPath)
    {
        try
        {
            var invoice = InvoiceParser.Parse(pdfPath);
            var xml = InvoiceXmlBuilder.Build(invoice);

            var outputPath = Path.Combine(
                Path.GetDirectoryName(pdfPath) ?? ".",
                Path.GetFileNameWithoutExtension(pdfPath) + ".xml");

            var settings = new XmlWriterSettings
            {
                Indent = true,
                IndentChars = "  ",
                Encoding = new UTF8Encoding(false)
            };

            using (var writer = XmlWriter.Create(outputPath, settings))
            {
                xml.Save(writer);
            }

            Log($"OK: {Path.GetFileName(pdfPath)} -> {Path.GetFileName(outputPath)}");
        }
        catch (Exception ex)
        {
            Log($"CHYBA: {Path.GetFileName(pdfPath)} - {ex.Message}");
        }
    }

    private void Log(string message)
    {
        _logList.Items.Add($"{DateTime.Now:HH:mm:ss}  {message}");
        _logList.TopIndex = _logList.Items.Count - 1;
    }
}
