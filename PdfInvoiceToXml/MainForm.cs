using System.Drawing;
using System.Drawing.Drawing2D;
using System.Reflection;
using System.Text;
using System.Windows.Forms;
using System.Xml;
using PdfInvoiceToXml.Parsing;
using PdfInvoiceToXml.Xml;

namespace PdfInvoiceToXml;

public class MainForm : Form
{
    // Bumped by hand on every commit that changes parsing/XML-building
    // behaviour. Shown in the title bar and startup log so a stale .exe
    // (built before a fix landed) is immediately obvious instead of looking
    // like "the fix didn't work" - see the debugging notes for how much time
    // that confusion has cost.
    private const string BuildTag = "2026-07-26";

    private static readonly Color AccentColor = Color.FromArgb(79, 70, 229);
    private static readonly Color AccentSoft = Color.FromArgb(231, 229, 253);
    private static readonly Color BackgroundColor = Color.FromArgb(246, 247, 250);
    private static readonly Color BorderColor = Color.FromArgb(203, 213, 225);
    private static readonly Color TextColor = Color.FromArgb(30, 41, 59);
    private static readonly Color MutedColor = Color.FromArgb(100, 110, 125);
    private static readonly Color OkColor = Color.FromArgb(21, 128, 61);
    private static readonly Color WarnColor = Color.FromArgb(180, 120, 0);
    private static readonly Color ErrorColor = Color.FromArgb(185, 28, 28);

    private enum LogKind { Info, Ok, Warn, Error }

    private readonly Panel _dropZoneInner = new();
    private readonly Panel _dropIconCanvas = new();
    private readonly Label _dropLabel = new();
    private readonly ListView _logView = new();
    private readonly ImageList _statusIcons = new();
    private readonly Label _statusIdleLabel = new();
    private readonly FlowLayoutPanel _statusChips = new();
    private readonly Label _statusOkChip = new();
    private readonly Label _statusWarnChip = new();
    private readonly Label _statusErrorChip = new();

    private bool _dropZoneActive;
    private int _okCount;
    private int _warnCount;
    private int _errorCount;

    public MainForm()
    {
        Text = $"PDF --> XML (BETA) - build {BuildTag}";
        var appIcon = TryLoadAppIcon();
        if (appIcon != null) Icon = appIcon;
        Width = 860;
        Height = 580;
        MinimumSize = new Size(640, 440);
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = BackgroundColor;
        Font = new Font("Segoe UI", 9.5f);
        AllowDrop = true;

        Controls.Add(BuildLogView());
        Controls.Add(BuildDropZone());
        Controls.Add(BuildHeader());
        Controls.Add(new Panel { Dock = DockStyle.Top, Height = 3, BackColor = AccentColor });

        DragEnter += Form_DragEnter;
        DragDrop += Form_DragDrop;

        Log($"Build {BuildTag} - pokud po opravě chyby vidíte v titulku okna starší datum, aplikaci jste nepřekompilovali.", LogKind.Info);
        UpdateStatusLabel();
    }

    private Control BuildHeader()
    {
        var header = new Panel
        {
            Dock = DockStyle.Top,
            Height = 72,
            BackColor = Color.White,
            Padding = new Padding(24, 0, 24, 0)
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
            Location = new Point(24, 14)
        };

        var title = new Label
        {
            AutoSize = true,
            Text = "PDF → XML",
            Font = new Font("Segoe UI", 16f, FontStyle.Bold),
            ForeColor = TextColor,
            Margin = new Padding(0, 0, 10, 0)
        };

        var badge = new Label
        {
            AutoSize = true,
            Text = "BETA",
            Font = new Font("Segoe UI", 8f, FontStyle.Bold),
            ForeColor = Color.White,
            BackColor = AccentColor,
            Padding = new Padding(9, 3, 9, 3),
            Margin = new Padding(0, 9, 0, 0)
        };
        badge.Resize += (_, _) =>
        {
            if (badge.Width > 0 && badge.Height > 0)
            {
                badge.Region?.Dispose();
                badge.Region = new Region(RoundedRect(new Rectangle(0, 0, badge.Width, badge.Height), badge.Height / 2));
            }
        };

        titleRow.Controls.Add(title);
        titleRow.Controls.Add(badge);

        var subtitle = new Label
        {
            AutoSize = true,
            Text = "Automatický převod PDF faktury do XML",
            Font = new Font("Segoe UI", 9f),
            ForeColor = MutedColor,
            Location = new Point(24, 46)
        };

        header.Controls.Add(titleRow);
        header.Controls.Add(subtitle);
        return header;
    }

    private Control BuildDropZone()
    {
        var dropZone = new Panel
        {
            Dock = DockStyle.Top,
            Height = 150,
            Margin = new Padding(24),
            BackColor = BackgroundColor,
            Padding = new Padding(24, 16, 24, 16),
            AllowDrop = true
        };

        _dropZoneInner.Dock = DockStyle.Fill;
        _dropZoneInner.BackColor = BackgroundColor;
        _dropZoneInner.AllowDrop = true;
        EnableDoubleBuffer(_dropZoneInner);
        _dropZoneInner.Paint += DropZoneInner_Paint;

        _dropIconCanvas.Dock = DockStyle.Top;
        _dropIconCanvas.Height = 72;
        _dropIconCanvas.BackColor = Color.White;
        _dropIconCanvas.AllowDrop = true;
        _dropIconCanvas.Paint += UploadIcon_Paint;

        _dropLabel.Dock = DockStyle.Fill;
        _dropLabel.BackColor = Color.White;
        _dropLabel.Text = "Přetáhněte sem jednu nebo více PDF faktur\nvedle každého PDF vznikne odpovídající .xml";
        _dropLabel.Font = new Font("Segoe UI", 9.5f);
        _dropLabel.TextAlign = ContentAlignment.TopCenter;
        _dropLabel.ForeColor = MutedColor;
        _dropLabel.AllowDrop = true;

        _dropZoneInner.Controls.Add(_dropLabel);
        _dropZoneInner.Controls.Add(_dropIconCanvas);

        dropZone.Controls.Add(_dropZoneInner);

        foreach (var c in new Control[] { dropZone, _dropZoneInner, _dropIconCanvas, _dropLabel })
        {
            c.DragEnter += Form_DragEnter;
            c.DragDrop += Form_DragDrop;
            c.DragEnter += (_, _) => SetDropZoneActive(true);
            c.DragLeave += (_, _) => SetDropZoneActive(false);
        }

        return dropZone;
    }

    private void SetDropZoneActive(bool active)
    {
        _dropZoneActive = active;
        _dropZoneInner.Invalidate();
    }

    private void DropZoneInner_Paint(object? sender, PaintEventArgs e)
    {
        var panel = _dropZoneInner;
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
        var rect = new Rectangle(0, 0, panel.Width - 1, panel.Height - 1);
        using var path = RoundedRect(rect, 18);
        using var fillBrush = new SolidBrush(_dropZoneActive ? AccentSoft : Color.White);
        e.Graphics.FillPath(fillBrush, path);
        using var pen = new Pen(AccentColor, 1.6f) { DashStyle = DashStyle.Dash };
        e.Graphics.DrawPath(pen, path);
    }

    private void UploadIcon_Paint(object? sender, PaintEventArgs e)
    {
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        var bounds = _dropIconCanvas.ClientRectangle;
        const float diameter = 52f;
        var circleRect = new RectangleF((bounds.Width - diameter) / 2f, (bounds.Height - diameter) / 2f, diameter, diameter);

        using (var circleBrush = new SolidBrush(AccentSoft))
        {
            g.FillEllipse(circleBrush, circleRect);
        }

        var cx = circleRect.X + diameter / 2f;
        var cy = circleRect.Y + diameter / 2f;

        using (var arrowBrush = new SolidBrush(AccentColor))
        using (var arrowPath = new GraphicsPath())
        {
            arrowPath.AddPolygon(new[]
            {
                new PointF(cx, cy - 13f),
                new PointF(cx - 8f, cy - 4f),
                new PointF(cx - 3f, cy - 4f),
                new PointF(cx - 3f, cy + 7f),
                new PointF(cx + 3f, cy + 7f),
                new PointF(cx + 3f, cy - 4f),
                new PointF(cx + 8f, cy - 4f),
            });
            g.FillPath(arrowBrush, arrowPath);
        }

        using var trayPen = new Pen(AccentColor, 2.4f) { StartCap = LineCap.Round, EndCap = LineCap.Round };
        g.DrawLine(trayPen, cx - 10f, cy + 13f, cx + 10f, cy + 13f);
    }

    private Control BuildLogView()
    {
        var wrapper = new Panel
        {
            Dock = DockStyle.Fill,
            Padding = new Padding(24, 4, 24, 16),
            BackColor = BackgroundColor
        };

        var card = new Panel { Dock = DockStyle.Fill, BackColor = BackgroundColor, Padding = new Padding(10) };
        EnableDoubleBuffer(card);
        card.Paint += (_, e) =>
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var rect = new Rectangle(0, 0, card.Width - 1, card.Height - 1);
            using var path = RoundedRect(rect, 14);
            using var fillBrush = new SolidBrush(Color.White);
            e.Graphics.FillPath(fillBrush, path);
            using var pen = new Pen(BorderColor);
            e.Graphics.DrawPath(pen, path);
        };

        _statusIcons.ColorDepth = ColorDepth.Depth32Bit;
        _statusIcons.ImageSize = new Size(12, 12);
        _statusIcons.Images.Add("ok", MakeStatusDot(OkColor));
        _statusIcons.Images.Add("warn", MakeStatusDot(WarnColor));
        _statusIcons.Images.Add("error", MakeStatusDot(ErrorColor));
        _statusIcons.Images.Add("info", MakeStatusDot(MutedColor));

        _logView.Dock = DockStyle.Fill;
        _logView.View = View.Details;
        _logView.FullRowSelect = true;
        _logView.GridLines = false;
        _logView.BorderStyle = BorderStyle.None;
        _logView.HeaderStyle = ColumnHeaderStyle.Nonclickable;
        _logView.SmallImageList = _statusIcons;
        _logView.Columns.Add("", 28);
        _logView.Columns.Add("Čas", 64);
        _logView.Columns.Add("Zpráva", 600);
        _logView.AllowDrop = true;
        _logView.DragEnter += Form_DragEnter;
        _logView.DragDrop += Form_DragDrop;

        card.Controls.Add(_logView);

        var statusBar = new Panel { Dock = DockStyle.Bottom, Height = 28 };

        _statusIdleLabel.Dock = DockStyle.Fill;
        _statusIdleLabel.TextAlign = ContentAlignment.MiddleLeft;
        _statusIdleLabel.ForeColor = MutedColor;
        _statusIdleLabel.Font = new Font("Segoe UI", 8.5f);
        _statusIdleLabel.Text = "Připraveno - čekám na PDF soubory.";

        _statusChips.Dock = DockStyle.Fill;
        _statusChips.FlowDirection = FlowDirection.LeftToRight;
        _statusChips.WrapContents = false;
        _statusChips.Visible = false;

        StyleStatusChip(_statusOkChip);
        StyleStatusChip(_statusWarnChip);
        StyleStatusChip(_statusErrorChip);
        _statusChips.Controls.Add(_statusOkChip);
        _statusChips.Controls.Add(_statusWarnChip);
        _statusChips.Controls.Add(_statusErrorChip);

        statusBar.Controls.Add(_statusIdleLabel);
        statusBar.Controls.Add(_statusChips);

        wrapper.Controls.Add(card);
        wrapper.Controls.Add(statusBar);
        return wrapper;
    }

    private static void StyleStatusChip(Label chip)
    {
        chip.AutoSize = true;
        chip.Font = new Font("Segoe UI", 8.5f, FontStyle.Bold);
        chip.Margin = new Padding(0, 6, 22, 0);
        chip.TextAlign = ContentAlignment.MiddleLeft;
    }

    private static Icon? TryLoadAppIcon()
    {
        try
        {
            // Pulls the icon straight from the .exe's own embedded resource
            // (set via <ApplicationIcon> in the .csproj) so the taskbar,
            // Alt-Tab, and title bar all show the same icon without having
            // to ship/load a separate .ico file at runtime.
            return Icon.ExtractAssociatedIcon(Application.ExecutablePath);
        }
        catch
        {
            return null;
        }
    }

    private static Bitmap MakeStatusDot(Color color)
    {
        var bmp = new Bitmap(12, 12);
        using var g = Graphics.FromImage(bmp);
        g.SmoothingMode = SmoothingMode.AntiAlias;
        using var brush = new SolidBrush(color);
        g.FillEllipse(brush, 1, 1, 10, 10);
        return bmp;
    }

    private static GraphicsPath RoundedRect(Rectangle bounds, int radius)
    {
        var diameter = radius * 2;
        var path = new GraphicsPath();
        if (diameter <= 0 || diameter > bounds.Width || diameter > bounds.Height)
        {
            path.AddRectangle(bounds);
            return path;
        }

        var arc = new Rectangle(bounds.Location, new Size(diameter, diameter));
        path.AddArc(arc, 180, 90);

        arc.X = bounds.Right - diameter;
        path.AddArc(arc, 270, 90);

        arc.Y = bounds.Bottom - diameter;
        path.AddArc(arc, 0, 90);

        arc.X = bounds.Left;
        path.AddArc(arc, 90, 90);

        path.CloseFigure();
        return path;
    }

    private static void EnableDoubleBuffer(Control control)
    {
        try
        {
            typeof(Control)
                .GetProperty("DoubleBuffered", BindingFlags.NonPublic | BindingFlags.Instance)
                ?.SetValue(control, true);
        }
        catch
        {
            // Purely a flicker-reduction nicety - safe to skip if unavailable.
        }
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

            Log($"{Path.GetFileName(pdfPath)} → {Path.GetFileName(outputPath)}", LogKind.Ok);
            _okCount++;

            if (invoice.WasOcr)
            {
                Log("   POZOR: PDF nemělo textovou vrstvu, data se četla přes OCR - zkontrolujte prosím čísla ručně.", LogKind.Warn);
                _warnCount++;
            }
        }
        catch (Exception ex)
        {
            Log($"{Path.GetFileName(pdfPath)} - {ex.Message}", LogKind.Error);
            _errorCount++;
        }

        UpdateStatusLabel();
    }

    private void Log(string message, LogKind kind)
    {
        var iconKey = kind switch
        {
            LogKind.Ok => "ok",
            LogKind.Warn => "warn",
            LogKind.Error => "error",
            _ => "info"
        };

        var item = new ListViewItem("") { ImageKey = iconKey, ForeColor = TextColor };
        item.SubItems.Add(DateTime.Now.ToString("HH:mm:ss"));
        item.SubItems.Add(message);
        _logView.Items.Add(item);
        item.EnsureVisible();
    }

    private void UpdateStatusLabel()
    {
        var idle = _okCount == 0 && _warnCount == 0 && _errorCount == 0;
        _statusIdleLabel.Visible = idle;
        _statusChips.Visible = !idle;

        _statusOkChip.Text = $"●  Zpracováno: {_okCount}";
        _statusWarnChip.Text = $"●  Upozornění: {_warnCount}";
        _statusErrorChip.Text = $"●  Chyby: {_errorCount}";

        _statusOkChip.ForeColor = _okCount > 0 ? OkColor : MutedColor;
        _statusWarnChip.ForeColor = _warnCount > 0 ? WarnColor : MutedColor;
        _statusErrorChip.ForeColor = _errorCount > 0 ? ErrorColor : MutedColor;
    }
}
