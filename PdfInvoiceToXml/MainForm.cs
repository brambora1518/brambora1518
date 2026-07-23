using System.Drawing;
using System.Drawing.Drawing2D;
using System.Text;
using System.Windows.Forms;
using System.Xml;
using PdfInvoiceToXml.Parsing;
using PdfInvoiceToXml.Xml;

namespace PdfInvoiceToXml;

public class MainForm : Form
{
    private static readonly Color AccentColor = Color.FromArgb(37, 99, 235);
    private static readonly Color BackgroundColor = Color.FromArgb(246, 247, 250);
    private static readonly Color BorderColor = Color.FromArgb(203, 213, 225);
    private static readonly Color OkColor = Color.FromArgb(21, 128, 61);
    private static readonly Color WarnColor = Color.FromArgb(180, 120, 0);
    private static readonly Color ErrorColor = Color.FromArgb(185, 28, 28);
    private static readonly Color MutedColor = Color.FromArgb(100, 110, 125);

    private readonly Panel _dropZone = new();
    private readonly Label _dropIcon = new();
    private readonly Label _dropLabel = new();
    private readonly ListView _logView = new();
    private readonly Label _statusLabel = new();

    private int _okCount;
    private int _warnCount;
    private int _errorCount;

    public MainForm()
    {
        Text = "PDF --> XML (BETA)";
        Width = 820;
        Height = 560;
        MinimumSize = new Size(620, 420);
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = BackgroundColor;
        Font = new Font("Segoe UI", 9.5f);
        AllowDrop = true;

        Controls.Add(BuildLogView());
        Controls.Add(BuildDropZone());
        Controls.Add(BuildHeader());

        DragEnter += Form_DragEnter;
        DragDrop += Form_DragDrop;

        UpdateStatusLabel();
    }

    private Control BuildHeader()
    {
        var header = new Panel
        {
            Dock = DockStyle.Top,
            Height = 70,
            BackColor = Color.White,
            Padding = new Padding(20, 0, 20, 0)
        };
        header.Paint += (_, e) =>
        {
            using var pen = new Pen(BorderColor);
            e.Graphics.DrawLine(pen, 0, header.Height - 1, header.Width, header.Height - 1);
        };

        var titleRow = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false,
            Location = new Point(20, 12)
        };

        var title = new Label
        {
            AutoSize = true,
            Text = "PDF → XML",
            Font = new Font("Segoe UI", 15f, FontStyle.Bold),
            ForeColor = Color.FromArgb(30, 41, 59),
            Margin = new Padding(0, 0, 10, 0)
        };

        var badge = new Label
        {
            AutoSize = true,
            Text = "BETA",
            Font = new Font("Segoe UI", 8f, FontStyle.Bold),
            ForeColor = Color.White,
            BackColor = AccentColor,
            Padding = new Padding(7, 2, 7, 2),
            Margin = new Padding(0, 8, 0, 0)
        };

        titleRow.Controls.Add(title);
        titleRow.Controls.Add(badge);

        var subtitle = new Label
        {
            AutoSize = true,
            Text = "Převod PDF faktury do formátu KASTNER IMPORT (XML)",
            Font = new Font("Segoe UI", 9f),
            ForeColor = MutedColor,
            Location = new Point(20, 44)
        };

        header.Controls.Add(titleRow);
        header.Controls.Add(subtitle);
        return header;
    }

    private Control BuildDropZone()
    {
        _dropZone.Dock = DockStyle.Top;
        _dropZone.Height = 130;
        _dropZone.Margin = new Padding(20);
        _dropZone.BackColor = BackgroundColor;
        _dropZone.Padding = new Padding(20, 14, 20, 14);
        _dropZone.AllowDrop = true;

        var inner = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = Color.White,
            AllowDrop = true
        };
        inner.Paint += DropZone_Paint;

        _dropIcon.AutoSize = false;
        _dropIcon.Height = 34;
        _dropIcon.Text = "↓";
        _dropIcon.Font = new Font("Segoe UI", 20f);
        _dropIcon.ForeColor = AccentColor;
        _dropIcon.TextAlign = ContentAlignment.MiddleCenter;
        _dropIcon.Dock = DockStyle.Top;
        _dropIcon.Padding = new Padding(0, 14, 0, 0);
        _dropIcon.AllowDrop = true;

        _dropLabel.AutoSize = false;
        _dropLabel.Dock = DockStyle.Fill;
        _dropLabel.Text = "Přetáhněte sem jednu nebo více PDF faktur\nvedle každého PDF vznikne odpovídající .xml";
        _dropLabel.TextAlign = ContentAlignment.TopCenter;
        _dropLabel.ForeColor = MutedColor;
        _dropLabel.AllowDrop = true;

        inner.Controls.Add(_dropLabel);
        inner.Controls.Add(_dropIcon);

        _dropZone.Controls.Add(inner);

        foreach (var c in new Control[] { _dropZone, inner, _dropIcon, _dropLabel })
        {
            c.DragEnter += Form_DragEnter;
            c.DragDrop += Form_DragDrop;
            c.DragEnter += (_, _) => SetDropZoneActive(inner, true);
            c.DragLeave += (_, _) => SetDropZoneActive(inner, false);
        }

        return _dropZone;
    }

    private void SetDropZoneActive(Panel inner, bool active)
    {
        inner.BackColor = active ? Color.FromArgb(239, 246, 255) : Color.White;
        inner.Invalidate();
    }

    private void DropZone_Paint(object? sender, PaintEventArgs e)
    {
        if (sender is not Panel panel) return;
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
        using var pen = new Pen(AccentColor, 1.5f) { DashStyle = DashStyle.Dash };
        var rect = new Rectangle(1, 1, panel.Width - 3, panel.Height - 3);
        e.Graphics.DrawRectangle(pen, rect);
    }

    private Control BuildLogView()
    {
        var wrapper = new Panel
        {
            Dock = DockStyle.Fill,
            Padding = new Padding(20, 4, 20, 16),
            BackColor = BackgroundColor
        };

        _logView.Dock = DockStyle.Fill;
        _logView.View = View.Details;
        _logView.FullRowSelect = true;
        _logView.GridLines = false;
        _logView.HeaderStyle = ColumnHeaderStyle.Nonclickable;
        _logView.BorderStyle = BorderStyle.FixedSingle;
        _logView.Columns.Add("Čas", 70);
        _logView.Columns.Add("Zpráva", 640);
        _logView.AllowDrop = true;
        _logView.DragEnter += Form_DragEnter;
        _logView.DragDrop += Form_DragDrop;

        _statusLabel.Dock = DockStyle.Bottom;
        _statusLabel.Height = 26;
        _statusLabel.TextAlign = ContentAlignment.MiddleLeft;
        _statusLabel.ForeColor = MutedColor;
        _statusLabel.Font = new Font("Segoe UI", 8.5f);

        wrapper.Controls.Add(_logView);
        wrapper.Controls.Add(_statusLabel);
        return wrapper;
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

            Log($"✓  {Path.GetFileName(pdfPath)} → {Path.GetFileName(outputPath)}", OkColor);
            _okCount++;

            if (invoice.WasOcr)
            {
                Log("   POZOR: PDF nemělo textovou vrstvu, data se četla přes OCR - zkontrolujte prosím čísla ručně.", WarnColor);
                _warnCount++;
            }
        }
        catch (Exception ex)
        {
            Log($"✗  {Path.GetFileName(pdfPath)} - {ex.Message}", ErrorColor);
            _errorCount++;
        }

        UpdateStatusLabel();
    }

    private void Log(string message, Color color)
    {
        var item = new ListViewItem(DateTime.Now.ToString("HH:mm:ss"));
        item.SubItems.Add(message);
        item.ForeColor = color;
        _logView.Items.Add(item);
        item.EnsureVisible();
    }

    private void UpdateStatusLabel()
    {
        _statusLabel.Text = _okCount == 0 && _errorCount == 0 && _warnCount == 0
            ? "Připraveno - čekám na PDF soubory."
            : $"Zpracováno: {_okCount}   Upozornění: {_warnCount}   Chyby: {_errorCount}";
    }
}
