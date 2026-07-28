using System.Diagnostics;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Globalization;
using System.Text;
using System.Windows.Forms;
using System.Xml;
using PdfInvoiceToXml.Parsing;
using PdfInvoiceToXml.Ui;
using PdfInvoiceToXml.Xml;

namespace PdfInvoiceToXml;

public class MainForm : Form
{
    // Bumped by hand on every commit that changes parsing/XML-building
    // behaviour. Shown in the title bar and startup log so a stale .exe
    // (built before a fix landed) is immediately obvious instead of looking
    // like "the fix didn't work" - see the debugging notes for how much time
    // that confusion has cost.
    private const string BuildTag = "2026-07-27";

    private enum LogKind { Info, Ok, Warn, Error }

    private readonly Panel _dropZone = new();
    private readonly Panel _progressPanel = new();
    private readonly Panel _progressTrack = new();
    private readonly Label _progressLabel = new();
    private readonly ListView _logView = new();
    private readonly ImageList _statusIcons = new();
    private readonly Label _statusIdleLabel = new();
    private readonly FlowLayoutPanel _statusChips = new();
    private readonly Label _statusOkChip = new();
    private readonly Label _statusWarnChip = new();
    private readonly Label _statusErrorChip = new();

    private readonly Label _clockLabel = new();
    // Fully qualified: System.Threading.Timer is in scope via implicit usings,
    // and only the WinForms one ticks on the UI thread.
    private readonly System.Windows.Forms.Timer _clock = new();

    private readonly AccentButton _pickButton = new();
    private readonly AccentButton _openFolderButton = new();
    private readonly AccentButton _clearButton = new();

    private readonly ToolTip _tips = new();

    private readonly Font _dropTitleFont = UiTheme.Heading(11.5f);
    private readonly Font _dropHintFont = UiTheme.Body(9f);
    private readonly Font _logFont = UiTheme.Body(9.5f);
    private readonly Font _tipFont = UiTheme.Body(9f);

    /// <summary>What a log row carries, so a click can act on the file it named.</summary>
    private sealed record LogRow(LogKind Kind, string? XmlPath);

    private bool _dropActive;
    private bool _dropHover;
    private int _hoveredLogRow = -1;
    private bool _converting;
    private double _progressValue;

    // A single file gives no meaningful progress - parsing plus OCR is one
    // opaque step - so the bar animates instead of sitting frozen at 0%.
    private bool _progressIndeterminate;
    private double _progressPhase;
    private readonly System.Windows.Forms.Timer _progressAnimation = new();

    private string? _lastOutputDirectory;

    private int _okCount;
    private int _warnCount;
    private int _errorCount;

    public MainForm()
    {
        Text = $"PDF → XML (BETA) — build {BuildTag}";

        var appIcon = TryLoadAppIcon();
        if (appIcon != null) Icon = appIcon;

        Width = 900;
        Height = 640;
        MinimumSize = new Size(720, 540);
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = UiTheme.Background;
        Font = UiTheme.Body();
        AllowDrop = true;
        UiTheme.EnableDoubleBuffer(this);

        // Docked children are laid out from the end of the collection
        // backwards, so this list reads bottom-of-window first.
        Controls.Add(BuildLogArea());
        Controls.Add(BuildProgressArea());
        Controls.Add(BuildDropZone());
        Controls.Add(BuildToolbar());
        Controls.Add(BuildHeader());

        DragEnter += Form_DragEnter;
        DragDrop += Form_DragDrop;
        FormClosing += MainForm_FormClosing;

        UpdateClock();
        _clock.Interval = 1000;
        _clock.Tick += (_, _) => UpdateClock();
        _clock.Start();

        _progressAnimation.Interval = 40;
        _progressAnimation.Tick += (_, _) =>
        {
            _progressPhase = (_progressPhase + 0.02) % 1.0;
            _progressTrack.Invalidate();
        };

        SetUpTooltips();

        LogBuildTag();
        UpdateStatusBar();
    }

    /// <summary>
    /// Tooltips are owner-drawn because the stock one is a light-yellow system
    /// balloon, which is jarring in a dark window - and they carry the keyboard
    /// shortcut, which is how the shortcuts stay discoverable at all.
    /// </summary>
    private void SetUpTooltips()
    {
        _tips.OwnerDraw = true;
        _tips.Draw += (_, e) =>
        {
            using var back = new SolidBrush(UiTheme.Fill);
            e.Graphics.FillRectangle(back, e.Bounds);

            using var pen = new Pen(UiTheme.Border);
            e.Graphics.DrawRectangle(pen, 0, 0, e.Bounds.Width - 1, e.Bounds.Height - 1);

            TextRenderer.DrawText(
                e.Graphics, e.ToolTipText, _tipFont, e.Bounds, UiTheme.Text,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
        };

        _tips.SetToolTip(_pickButton, "Vybrat PDF faktury k převodu   (Ctrl+O)");
        _tips.SetToolTip(_openFolderButton, "Otevřít složku s vytvořenými XML   (Ctrl+E)");
        _tips.SetToolTip(_clearButton, "Vymazat záznamy v logu   (Ctrl+L)");
    }

    protected override bool ProcessCmdKey(ref Message msg, Keys keyData)
    {
        switch (keyData)
        {
            case Keys.Control | Keys.O:
                PickFiles();
                return true;
            case Keys.Control | Keys.E:
                OpenOutputFolder();
                return true;
            case Keys.Control | Keys.L:
                ClearLog();
                return true;
            default:
                return base.ProcessCmdKey(ref msg, keyData);
        }
    }

    protected override void OnHandleCreated(EventArgs e)
    {
        base.OnHandleCreated(e);

        // Rounds the window's outer corners and switches the title bar to dark
        // mode tinted to match the header, so the caption and the app read as
        // one surface. Rounding is Windows 11 only and silently does nothing
        // on Windows 10; the dark caption works from Windows 10 1809 on.
        WindowChrome.Apply(Handle, UiTheme.Surface, UiTheme.Text, UiTheme.Border);

        // Has to wait for the handle: the list's scroll bars are drawn by the
        // theme engine, so setting BackColor alone leaves them bright white.
        WindowChrome.ApplyDarkControlTheme(_logView);
    }

    // ---------------------------------------------------------------- layout

    private Control BuildHeader()
    {
        var header = new Panel
        {
            Dock = DockStyle.Top,
            Height = 66,
            BackColor = UiTheme.Surface,
            Padding = new Padding(28, 0, 28, 0)
        };
        header.Paint += (_, e) =>
        {
            // A hairline, the way a macOS toolbar separates from its content -
            // not the hard rule a default WinForms panel would give you.
            using var pen = new Pen(UiTheme.Separator);
            e.Graphics.DrawLine(pen, 0, header.Height - 1, header.Width, header.Height - 1);
        };

        _clockLabel.Dock = DockStyle.Right;
        _clockLabel.Width = 200;
        _clockLabel.Font = UiTheme.Body(10f);
        _clockLabel.ForeColor = UiTheme.Muted;
        _clockLabel.TextAlign = ContentAlignment.MiddleRight;
        _clockLabel.BackColor = UiTheme.Surface;

        var titleRow = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false,
            BackColor = UiTheme.Surface,
            Location = new Point(28, 19)
        };

        var title = new Label
        {
            AutoSize = true,
            Text = "PDF → XML",
            Font = UiTheme.Heading(16.5f),
            ForeColor = UiTheme.Text,
            Margin = new Padding(0, 0, 12, 0)
        };

        var badge = new Label
        {
            AutoSize = true,
            Text = "BETA",
            Font = UiTheme.Body(7.5f, FontStyle.Bold),
            ForeColor = Color.White,
            BackColor = UiTheme.AccentFill,
            Padding = new Padding(10, 3, 10, 3),
            Margin = new Padding(0, 9, 0, 0)
        };
        badge.Resize += (_, _) => UiTheme.ApplyPillRegion(badge);

        titleRow.Controls.Add(title);
        titleRow.Controls.Add(badge);

        header.Controls.Add(titleRow);
        header.Controls.Add(_clockLabel);
        return header;
    }

    private Control BuildToolbar()
    {
        var bar = new Panel
        {
            Dock = DockStyle.Top,
            Height = 68,
            BackColor = UiTheme.Background,
            Padding = new Padding(28, 15, 28, 15)
        };

        var row = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false,
            BackColor = UiTheme.Background
        };

        ConfigureButton(_pickButton, "Vybrat PDF faktury…", 180, primary: true);
        _pickButton.Click += (_, _) => PickFiles();

        ConfigureButton(_openFolderButton, "Otevřít složku s XML", 175);
        _openFolderButton.Enabled = false;
        _openFolderButton.Click += (_, _) => OpenOutputFolder();

        ConfigureButton(_clearButton, "Vymazat log", 125);
        _clearButton.Click += (_, _) => ClearLog();

        row.Controls.Add(_pickButton);
        row.Controls.Add(_openFolderButton);
        row.Controls.Add(_clearButton);
        bar.Controls.Add(row);
        return bar;
    }

    private static void ConfigureButton(AccentButton button, string text, int width, bool primary = false)
    {
        button.Text = text;
        button.Primary = primary;
        button.Width = width;
        button.Height = 38;
        button.Margin = new Padding(0, 0, 10, 0);
    }

    private Control BuildDropZone()
    {
        var wrapper = new Panel
        {
            Dock = DockStyle.Top,
            Height = 186,
            BackColor = UiTheme.Background,
            Padding = new Padding(21, 0, 21, 10),
            AllowDrop = true
        };

        _dropZone.Dock = DockStyle.Fill;
        _dropZone.BackColor = UiTheme.Background;
        _dropZone.AllowDrop = true;
        _dropZone.Cursor = Cursors.Hand;
        UiTheme.EnableDoubleBuffer(_dropZone);

        // The whole zone is painted in one pass. An earlier version stacked a
        // white icon panel and a white label on top of the card, which covered
        // the rounded corners and hid the dashed border completely.
        _dropZone.Paint += DropZone_Paint;
        _dropZone.Click += (_, _) => PickFiles();

        // The zone is clickable, and a cursor change alone is a weak hint.
        _dropZone.MouseEnter += (_, _) => SetDropHover(true);
        _dropZone.MouseLeave += (_, _) => SetDropHover(false);

        _dropZone.DragEnter += DropZone_DragEnter;
        _dropZone.DragLeave += DropZone_DragLeave;
        _dropZone.DragDrop += Form_DragDrop;

        wrapper.Controls.Add(_dropZone);
        wrapper.DragEnter += Form_DragEnter;
        wrapper.DragDrop += Form_DragDrop;
        return wrapper;
    }

    private void DropZone_Paint(object? sender, PaintEventArgs e)
    {
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;

        // Inset so the soft shadow has room to fall outside the card.
        var rect = Rectangle.Inflate(new Rectangle(0, 0, _dropZone.Width - 1, _dropZone.Height - 1), -7, -7);
        if (rect.Width <= 8 || rect.Height <= 8) return;

        UiTheme.DrawSoftShadow(g, rect, UiTheme.DropZoneRadius);

        using (var path = UiTheme.RoundedRect(rect, UiTheme.DropZoneRadius))
        {
            var fillColor = _dropActive ? UiTheme.AccentSoft
                : _dropHover ? UiTheme.SurfaceHover
                : UiTheme.Surface;
            using var fill = new SolidBrush(fillColor);
            g.FillPath(fill, path);

            // Solid once a drag is actually over the zone: dashes read as
            // "drop something here", a solid accent ring as "release now".
            using var pen = _dropActive
                ? new Pen(UiTheme.Accent, 2f)
                : new Pen(_dropHover ? UiTheme.Accent : UiTheme.Border, 1.4f) { DashStyle = DashStyle.Dash };
            g.DrawPath(pen, path);
        }

        const float diameter = 52f;
        var centerX = rect.X + rect.Width / 2f;
        var circleTop = rect.Y + Math.Max(12f, rect.Height * 0.17f);
        var circleRect = new RectangleF(centerX - diameter / 2f, circleTop, diameter, diameter);

        using (var circleBrush = new SolidBrush(_dropActive ? UiTheme.AccentSoftDeep : UiTheme.AccentSoft))
        {
            g.FillEllipse(circleBrush, circleRect);
        }

        DrawUploadGlyph(g, centerX, circleTop + diameter / 2f);

        var titleTop = (int)(circleTop + diameter + 16f);
        var titleRect = new Rectangle(rect.X, titleTop, rect.Width, 24);
        TextRenderer.DrawText(
            g,
            _converting ? "Probíhá převod…" : "Přetáhněte sem PDF faktury",
            _dropTitleFont, titleRect, UiTheme.Text,
            TextFormatFlags.HorizontalCenter | TextFormatFlags.Top);

        var hintRect = new Rectangle(rect.X, titleRect.Bottom + 3, rect.Width, 20);
        TextRenderer.DrawText(
            g,
            _converting
                ? "Počkejte prosím na dokončení."
                : "nebo klikněte kamkoli sem — vedle každého PDF vznikne stejnojmenný .xml",
            _dropHintFont, hintRect, UiTheme.Muted,
            TextFormatFlags.HorizontalCenter | TextFormatFlags.Top | TextFormatFlags.EndEllipsis);
    }

    private static void DrawUploadGlyph(Graphics g, float cx, float cy)
    {
        using (var arrowBrush = new SolidBrush(UiTheme.Accent))
        using (var arrowPath = new GraphicsPath())
        {
            arrowPath.AddPolygon(new[]
            {
                new PointF(cx, cy - 12f),
                new PointF(cx - 8f, cy - 3f),
                new PointF(cx - 3f, cy - 3f),
                new PointF(cx - 3f, cy + 7f),
                new PointF(cx + 3f, cy + 7f),
                new PointF(cx + 3f, cy - 3f),
                new PointF(cx + 8f, cy - 3f)
            });
            g.FillPath(arrowBrush, arrowPath);
        }

        using var trayPen = new Pen(UiTheme.Accent, 2.4f) { StartCap = LineCap.Round, EndCap = LineCap.Round };
        g.DrawLine(trayPen, cx - 10f, cy + 13f, cx + 10f, cy + 13f);
    }

    private Control BuildProgressArea()
    {
        _progressPanel.Dock = DockStyle.Top;
        _progressPanel.Height = 42;
        _progressPanel.BackColor = UiTheme.Background;
        _progressPanel.Padding = new Padding(28, 0, 28, 12);
        _progressPanel.Visible = false;

        _progressTrack.Dock = DockStyle.Bottom;
        _progressTrack.Height = 8;
        _progressTrack.BackColor = UiTheme.Background;
        UiTheme.EnableDoubleBuffer(_progressTrack);
        _progressTrack.Paint += ProgressTrack_Paint;

        _progressLabel.Dock = DockStyle.Fill;
        _progressLabel.ForeColor = UiTheme.Muted;
        _progressLabel.Font = UiTheme.Body(8.5f);
        _progressLabel.TextAlign = ContentAlignment.MiddleLeft;
        _progressLabel.BackColor = UiTheme.Background;

        _progressPanel.Controls.Add(_progressLabel);
        _progressPanel.Controls.Add(_progressTrack);
        return _progressPanel;
    }

    private void ProgressTrack_Paint(object? sender, PaintEventArgs e)
    {
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;

        const int barHeight = 6;
        var width = _progressTrack.Width - 1;
        if (width <= barHeight) return;

        var track = new Rectangle(0, (_progressTrack.Height - barHeight) / 2, width, barHeight);
        using (var path = UiTheme.RoundedRect(track, barHeight / 2))
        using (var brush = new SolidBrush(UiTheme.AccentSoft))
        {
            g.FillPath(brush, path);
        }

        var filled = _progressIndeterminate
            ? IndeterminateSegment(track, barHeight)
            : new Rectangle(track.X, track.Y,
                (int)Math.Round(track.Width * Math.Clamp(_progressValue, 0d, 1d)), barHeight);

        if (filled.Width < barHeight) return;

        using (var path = UiTheme.RoundedRect(filled, barHeight / 2))
        using (var brush = new SolidBrush(UiTheme.Accent))
        {
            g.FillPath(brush, path);
        }
    }

    /// <summary>
    /// A segment sweeping left to right, clipped to the track so it slides in
    /// and out of both ends instead of popping into existence.
    /// </summary>
    private Rectangle IndeterminateSegment(Rectangle track, int barHeight)
    {
        var segment = Math.Max(barHeight * 4, track.Width / 4);
        var travel = track.Width + segment;
        var left = track.X + (int)(travel * _progressPhase) - segment;

        return Rectangle.Intersect(track, new Rectangle(left, track.Y, segment, barHeight));
    }

    private Control BuildLogArea()
    {
        var wrapper = new Panel
        {
            Dock = DockStyle.Fill,
            Padding = new Padding(21, 0, 21, 10),
            BackColor = UiTheme.Background
        };

        var card = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = UiTheme.Background,
            // Left/right room for the shadow, plus the list's own inset.
            Padding = new Padding(18, 16, 18, 16)
        };
        UiTheme.EnableDoubleBuffer(card);
        card.Paint += (_, e) =>
        {
            var rect = Rectangle.Inflate(new Rectangle(0, 0, card.Width - 1, card.Height - 1), -7, -7);
            if (rect.Width <= 8 || rect.Height <= 8) return;

            UiTheme.DrawCard(e.Graphics, rect, UiTheme.CardRadius, UiTheme.Surface);
        };

        // An empty image purely to set the row height - ListView takes it from
        // the image list, and the status dots are drawn by hand below.
        _statusIcons.ColorDepth = ColorDepth.Depth32Bit;
        _statusIcons.ImageSize = new Size(16, 24);
        _statusIcons.Images.Add("spacer", new Bitmap(16, 24));

        _logView.Dock = DockStyle.Fill;
        _logView.View = View.Details;
        _logView.FullRowSelect = true;
        _logView.GridLines = false;
        _logView.BorderStyle = BorderStyle.None;
        _logView.BackColor = UiTheme.Surface;
        _logView.ForeColor = UiTheme.Text;
        // No column headers: the list has an icon, a time and a message, which
        // needs no labelling, and the header band is the single most
        // spreadsheet-looking thing in the window.
        _logView.HeaderStyle = ColumnHeaderStyle.None;
        _logView.SmallImageList = _statusIcons;
        _logView.Columns.Add("", 30);
        _logView.Columns.Add("Čas", 70);
        _logView.Columns.Add("Zpráva", 560);
        _logView.MultiSelect = false;

        // Owner-drawn because the default selection is painted in the system
        // highlight colour, which shows up as a slab of Windows blue in the
        // middle of a purple-on-near-black window. Drawing the rows also gets
        // rid of the dotted focus rectangle and buys a hover state.
        _logView.OwnerDraw = true;
        _logView.DrawItem += LogView_DrawItem;
        _logView.DrawSubItem += LogView_DrawSubItem;
        _logView.MouseMove += LogView_MouseMove;
        _logView.MouseLeave += (_, _) => SetHoveredLogRow(-1);

        _logView.AllowDrop = true;
        _logView.DragEnter += Form_DragEnter;
        _logView.DragDrop += Form_DragDrop;
        _logView.Resize += (_, _) => ResizeLogColumns();
        _logView.DoubleClick += (_, _) => OpenSelectedXml();
        _logView.ContextMenuStrip = BuildLogContextMenu();

        card.Controls.Add(_logView);

        wrapper.Controls.Add(card);
        wrapper.Controls.Add(BuildStatusBar());
        return wrapper;
    }

    private void LogView_DrawItem(object? sender, DrawListViewItemEventArgs e)
    {
        var selected = (e.State & ListViewItemStates.Selected) != 0;
        var background = selected ? UiTheme.AccentSoft
            : e.ItemIndex == _hoveredLogRow ? UiTheme.SurfaceHover
            : UiTheme.Surface;

        using var brush = new SolidBrush(background);
        e.Graphics.FillRectangle(brush, e.Bounds);
    }

    private void LogView_DrawSubItem(object? sender, DrawListViewSubItemEventArgs e)
    {
        var g = e.Graphics;
        var kind = (e.Item?.Tag as LogRow)?.Kind ?? LogKind.Info;

        if (e.ColumnIndex == 0)
        {
            // For the first column e.Bounds can span the whole row, so the dot
            // is placed from the row's left edge rather than from the cell.
            const int size = 8;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            using var dot = new SolidBrush(StatusColor(kind));
            g.FillEllipse(dot, e.Bounds.Left + 11, e.Bounds.Top + (e.Bounds.Height - size) / 2, size, size);
            return;
        }

        g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;

        // Timestamps are metadata and stay muted whatever the row's status is;
        // only the message itself carries the colour.
        var color = e.ColumnIndex == 1 ? UiTheme.Muted : StatusTextColor(kind);

        var bounds = e.Bounds;
        bounds.X += 2;
        bounds.Width -= 6;

        TextRenderer.DrawText(
            g, e.SubItem?.Text ?? "", _logFont, bounds, color,
            TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis | TextFormatFlags.NoPrefix);
    }

    private void LogView_MouseMove(object? sender, MouseEventArgs e) =>
        SetHoveredLogRow(_logView.GetItemAt(e.X, e.Y)?.Index ?? -1);

    private void SetHoveredLogRow(int index)
    {
        if (_hoveredLogRow == index) return;

        var previous = _hoveredLogRow;
        _hoveredLogRow = index;
        InvalidateLogRow(previous);
        InvalidateLogRow(index);
    }

    private void InvalidateLogRow(int index)
    {
        if (index < 0 || index >= _logView.Items.Count) return;
        _logView.Invalidate(_logView.Items[index].Bounds);
    }

    private static Color StatusColor(LogKind kind) => kind switch
    {
        LogKind.Ok => UiTheme.Ok,
        LogKind.Warn => UiTheme.Warn,
        LogKind.Error => UiTheme.Error,
        _ => UiTheme.Muted
    };

    /// <summary>
    /// Successful rows read in plain body text - colouring every one green
    /// would make a normal run look like a wall of alerts - so only warnings
    /// and errors take their status colour.
    /// </summary>
    private static Color StatusTextColor(LogKind kind) => kind switch
    {
        LogKind.Ok => UiTheme.Text,
        LogKind.Warn => UiTheme.Warn,
        LogKind.Error => UiTheme.Error,
        _ => UiTheme.Muted
    };

    private ContextMenuStrip BuildLogContextMenu()
    {
        var openItem = new ToolStripMenuItem("Otevřít XML", null, (_, _) => OpenSelectedXml());
        var showItem = new ToolStripMenuItem("Zobrazit ve složce", null, (_, _) => ShowSelectedInFolder());
        var copyItem = new ToolStripMenuItem("Kopírovat řádek", null, (_, _) => CopySelectedLine());

        var menu = new ContextMenuStrip
        {
            Font = UiTheme.Body(),
            BackColor = UiTheme.Surface,
            ForeColor = UiTheme.Text,
            Renderer = new DarkMenuRenderer()
        };
        menu.Items.Add(openItem);
        menu.Items.Add(showItem);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add(copyItem);

        menu.Opening += (_, _) =>
        {
            var hasFile = SelectedXmlPath() != null;
            openItem.Enabled = hasFile;
            showItem.Enabled = hasFile;
            copyItem.Enabled = _logView.SelectedItems.Count > 0;
        };

        return menu;
    }

    private Control BuildStatusBar()
    {
        var statusBar = new Panel
        {
            Dock = DockStyle.Bottom,
            Height = 32,
            BackColor = UiTheme.Background,
            // Lines up with the card's visual edge, which sits 7px inside its
            // panel to leave room for the shadow.
            Padding = new Padding(7, 0, 7, 0)
        };

        _statusIdleLabel.Dock = DockStyle.Fill;
        _statusIdleLabel.TextAlign = ContentAlignment.MiddleLeft;
        _statusIdleLabel.ForeColor = UiTheme.Muted;
        _statusIdleLabel.Font = UiTheme.Body(8.5f);
        _statusIdleLabel.BackColor = UiTheme.Background;
        _statusIdleLabel.Text = "Připraveno — čekám na PDF soubory.";

        _statusChips.Dock = DockStyle.Fill;
        _statusChips.FlowDirection = FlowDirection.LeftToRight;
        _statusChips.WrapContents = false;
        _statusChips.BackColor = UiTheme.Background;
        _statusChips.Visible = false;

        StyleStatusChip(_statusOkChip);
        StyleStatusChip(_statusWarnChip);
        StyleStatusChip(_statusErrorChip);

        _statusChips.Controls.Add(_statusOkChip);
        _statusChips.Controls.Add(_statusWarnChip);
        _statusChips.Controls.Add(_statusErrorChip);

        var buildLabel = new Label
        {
            Dock = DockStyle.Right,
            Width = 150,
            Text = $"build {BuildTag}",
            Font = UiTheme.Body(8.5f),
            ForeColor = UiTheme.Disabled,
            TextAlign = ContentAlignment.MiddleRight,
            BackColor = UiTheme.Background
        };

        statusBar.Controls.Add(_statusIdleLabel);
        statusBar.Controls.Add(_statusChips);
        // Added last so it claims the right-hand strip before the two
        // Fill-docked controls take the remainder.
        statusBar.Controls.Add(buildLabel);
        return statusBar;
    }

    private static void StyleStatusChip(Label chip)
    {
        chip.AutoSize = true;
        chip.Font = UiTheme.Body(8.5f, FontStyle.Bold);
        chip.Margin = new Padding(0, 5, 8, 0);
        chip.Padding = new Padding(11, 4, 11, 4);
        chip.TextAlign = ContentAlignment.MiddleCenter;
        chip.Resize += (_, _) => UiTheme.ApplyPillRegion(chip);
    }

    private void ResizeLogColumns()
    {
        if (_logView.Columns.Count < 3) return;

        var available = _logView.ClientSize.Width
                        - _logView.Columns[0].Width
                        - _logView.Columns[1].Width
                        - 4;
        _logView.Columns[2].Width = Math.Max(240, available);
    }

    // ------------------------------------------------------------ drag & drop

    private void Form_DragEnter(object? sender, DragEventArgs e)
    {
        e.Effect = !_converting && e.Data?.GetDataPresent(DataFormats.FileDrop) == true
            ? DragDropEffects.Copy
            : DragDropEffects.None;
    }

    private void DropZone_DragEnter(object? sender, DragEventArgs e)
    {
        Form_DragEnter(sender, e);
        if (e.Effect == DragDropEffects.Copy) SetDropActive(true);
    }

    private void DropZone_DragLeave(object? sender, EventArgs e) => SetDropActive(false);

    private void Form_DragDrop(object? sender, DragEventArgs e)
    {
        // DragLeave is not raised when a drop completes, so without this the
        // zone stays highlighted forever after the first file.
        SetDropActive(false);

        if (_converting) return;
        if (e.Data?.GetData(DataFormats.FileDrop) is not string[] paths) return;

        StartConversion(ExpandToPdfFiles(paths));
    }

    private void SetDropActive(bool active)
    {
        if (_dropActive == active) return;
        _dropActive = active;
        _dropZone.Invalidate();
    }

    private void SetDropHover(bool hover)
    {
        if (_dropHover == hover || _converting) return;
        _dropHover = hover;
        _dropZone.Invalidate();
    }

    /// <summary>
    /// Accepts dropped folders as well as files - dragging the folder a batch
    /// of invoices lives in is the obvious thing to try, and silently doing
    /// nothing was the previous behaviour.
    /// </summary>
    private List<string> ExpandToPdfFiles(IEnumerable<string> paths)
    {
        var pdfs = new List<string>();
        var skipped = 0;

        foreach (var path in paths)
        {
            try
            {
                if (Directory.Exists(path))
                {
                    // Subfolders included: invoices are usually filed by month
                    // or by supplier, and dropping the parent folder should
                    // not quietly skip everything inside those.
                    var found = Directory
                        .EnumerateFiles(path, "*.pdf", SearchOption.AllDirectories)
                        .ToList();

                    pdfs.AddRange(found);
                    Log($"Složka {new DirectoryInfo(path).Name}: nalezeno {found.Count} PDF (včetně podsložek).",
                        LogKind.Info);
                }
                else if (path.EndsWith(".pdf", StringComparison.OrdinalIgnoreCase))
                {
                    pdfs.Add(path);
                }
                else
                {
                    skipped++;
                }
            }
            catch (Exception ex)
            {
                Log($"{Path.GetFileName(path)} — nelze přečíst: {ex.Message}", LogKind.Error);
                _errorCount++;
            }
        }

        if (skipped > 0)
        {
            Log($"Přeskočeno {skipped} položek, které nejsou PDF.", LogKind.Warn);
            _warnCount++;
        }

        return pdfs;
    }

    // ------------------------------------------------------------- conversion

    private void PickFiles()
    {
        if (_converting) return;

        using var dialog = new OpenFileDialog
        {
            Title = "Vyberte PDF faktury",
            Filter = "PDF faktury (*.pdf)|*.pdf|Všechny soubory (*.*)|*.*",
            Multiselect = true,
            CheckFileExists = true
        };

        if (dialog.ShowDialog(this) == DialogResult.OK)
        {
            StartConversion(ExpandToPdfFiles(dialog.FileNames));
        }
    }

    private async void StartConversion(IReadOnlyList<string> pdfPaths)
    {
        if (_converting) return;

        if (pdfPaths.Count == 0)
        {
            Log("Nebyl vybrán žádný PDF soubor.", LogKind.Warn);
            _warnCount++;
            UpdateStatusBar();
            return;
        }

        // With several files the bar can show real progress across them; with
        // one there is nothing to report until it finishes, so it animates.
        _progressIndeterminate = pdfPaths.Count == 1;

        SetBusy(true);
        var startedAt = DateTime.Now;

        try
        {
            for (var i = 0; i < pdfPaths.Count; i++)
            {
                var pdfPath = pdfPaths[i];
                _progressLabel.Text = pdfPaths.Count == 1
                    ? $"Zpracovávám {Path.GetFileName(pdfPath)}…"
                    : $"Zpracovávám {Path.GetFileName(pdfPath)} ({i + 1} z {pdfPaths.Count})…";
                SetProgress((double)i / pdfPaths.Count);

                // Parsing plus a possible OCR pass takes seconds per page. On
                // the UI thread that froze the whole window into "Neodpovídá".
                var result = await Task.Run(() => ConvertInvoice(pdfPath));
                ApplyResult(result);
            }

            SetProgress(1d);

            if (pdfPaths.Count > 1)
            {
                var elapsed = DateTime.Now - startedAt;
                Log($"Hotovo — {pdfPaths.Count} souborů za {elapsed.TotalSeconds:F1} s.", LogKind.Info);
            }
        }
        finally
        {
            SetBusy(false);
            UpdateStatusBar();
        }
    }

    private sealed record InvoiceResult(string XmlPath, int VatRateCount, decimal GrandTotal);

    private sealed record ConversionResult(
        string PdfPath,
        IReadOnlyList<InvoiceResult> Invoices,
        bool WasOcr,
        string? Error);

    /// <summary>
    /// Runs entirely on a worker thread, so it must not touch any control -
    /// everything it needs to report comes back through the returned record.
    /// One PDF can hold several invoices, so this writes one XML per document.
    /// </summary>
    private static ConversionResult ConvertInvoice(string pdfPath)
    {
        try
        {
            var invoices = InvoiceParser.ParseAll(pdfPath);

            var directory = Path.GetDirectoryName(pdfPath) ?? ".";
            var baseName = Path.GetFileNameWithoutExtension(pdfPath);

            var settings = new XmlWriterSettings
            {
                Indent = true,
                IndentChars = "  ",
                Encoding = new UTF8Encoding(false)
            };

            var written = new List<InvoiceResult>();
            var usedNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

            for (var i = 0; i < invoices.Count; i++)
            {
                var invoice = invoices[i];
                var name = BuildOutputName(baseName, invoice.DocumentId, i, invoices.Count, usedNames);
                var outputPath = Path.Combine(directory, name + ".xml");

                using (var writer = XmlWriter.Create(outputPath, settings))
                {
                    InvoiceXmlBuilder.Build(invoice).Save(writer);
                }

                written.Add(new InvoiceResult(outputPath, invoice.VatTable.Count, invoice.GrandTotal));
            }

            return new ConversionResult(pdfPath, written, invoices.Any(x => x.WasOcr), null);
        }
        catch (Exception ex)
        {
            return new ConversionResult(pdfPath, Array.Empty<InvoiceResult>(), false, ex.Message);
        }
    }

    /// <summary>
    /// A single invoice keeps the PDF's own name, so the ordinary case is
    /// completely unchanged. Several invoices out of one file get the document
    /// number appended, which is far more use than a running index once the
    /// files are sitting in a folder together.
    /// </summary>
    private static string BuildOutputName(
        string baseName, string documentId, int index, int total, HashSet<string> used)
    {
        var name = baseName;

        if (total > 1)
        {
            var suffix = SanitizeForFileName(documentId);
            name = suffix.Length == 0 ? $"{baseName}_{index + 1}" : $"{baseName}_{suffix}";
        }

        // Two invoices in one file could carry the same number, and the second
        // must not overwrite the first.
        var candidate = name;
        var counter = 2;
        while (!used.Add(candidate))
        {
            candidate = $"{name}_{counter++}";
        }

        return candidate;
    }

    private static string SanitizeForFileName(string value)
    {
        if (string.IsNullOrWhiteSpace(value)) return "";

        // Invoice numbers are written "354/26", and '/' is not a legal file
        // name character on Windows.
        var invalid = Path.GetInvalidFileNameChars();
        return new string(value.Trim().Select(c => invalid.Contains(c) ? '-' : c).ToArray());
    }

    private void ApplyResult(ConversionResult result)
    {
        var pdfName = Path.GetFileName(result.PdfPath);

        if (result.Error != null)
        {
            Log($"{pdfName} — {result.Error}", LogKind.Error);
            _errorCount++;
            return;
        }

        if (result.Invoices.Count == 0)
        {
            Log($"{pdfName} — v souboru se nenašla žádná faktura.", LogKind.Warn);
            _warnCount++;
            return;
        }

        // OCR is a property of the whole file, so it is reported once rather
        // than repeated under every invoice found in it.
        if (result.WasOcr)
        {
            Log($"{pdfName} — PDF nemělo textovou vrstvu, data se četla přes OCR, zkontrolujte prosím čísla ručně.",
                LogKind.Warn);
            _warnCount++;
        }

        if (result.Invoices.Count > 1)
        {
            Log($"{pdfName} — nalezeno {result.Invoices.Count} faktur, každá se ukládá do vlastního XML.",
                LogKind.Info);
        }

        foreach (var invoice in result.Invoices)
        {
            Log($"{pdfName} → {Path.GetFileName(invoice.XmlPath)}  (celkem {invoice.GrandTotal:0.00} Kč)",
                LogKind.Ok, invoice.XmlPath);
            _okCount++;

            // Every amount in the XML is derived from the VAT recap table, so
            // without it the output is structurally valid but worthless.
            if (invoice.VatRateCount == 0)
            {
                Log("    POZOR: nenalezena rekapitulace DPH — toto XML nemá žádné částky, zkontrolujte fakturu.",
                    LogKind.Warn);
                _warnCount++;
            }
        }

        _lastOutputDirectory = Path.GetDirectoryName(result.Invoices[^1].XmlPath);
        _openFolderButton.Enabled = _lastOutputDirectory != null;
    }

    private void SetBusy(bool busy)
    {
        _converting = busy;
        _progressPanel.Visible = busy;
        _pickButton.Enabled = !busy;
        _clearButton.Enabled = !busy;
        _dropZone.Cursor = busy ? Cursors.Default : Cursors.Hand;
        UseWaitCursor = busy;

        if (busy)
        {
            _dropHover = false;
            if (_progressIndeterminate)
            {
                _progressPhase = 0d;
                _progressAnimation.Start();
            }
        }
        else
        {
            _progressAnimation.Stop();
            _progressIndeterminate = false;
            SetProgress(0d);
            _progressLabel.Text = "";
        }

        _dropZone.Invalidate();
    }

    private void SetProgress(double value)
    {
        _progressValue = value;
        _progressTrack.Invalidate();
    }

    // ---------------------------------------------------------------- logging

    private void Log(string message, LogKind kind, string? xmlPath = null)
    {
        // The row's colours come from its Kind at paint time, so the item only
        // needs to carry the kind and the file it refers to.
        var item = new ListViewItem("") { ImageKey = "spacer", Tag = new LogRow(kind, xmlPath) };
        item.SubItems.Add(DateTime.Now.ToString("HH:mm:ss", CultureInfo.InvariantCulture));
        item.SubItems.Add(message);

        _logView.Items.Add(item);
        item.EnsureVisible();
    }

    private void ClearLog()
    {
        if (_converting) return;

        _logView.Items.Clear();
        SetHoveredLogRow(-1);
        _okCount = 0;
        _warnCount = 0;
        _errorCount = 0;

        // Re-state the build straight away: it is the one line worth never
        // losing, and it leaves the panel with content rather than a void.
        LogBuildTag();
        UpdateStatusBar();
    }

    private void LogBuildTag() =>
        Log($"Build {BuildTag} — pokud po opravě chyby vidíte v titulku okna starší datum, aplikaci jste nepřekompilovali.",
            LogKind.Info);

    /// <summary>
    /// Numeric date and time. The project builds with InvariantGlobalization,
    /// so there are no Czech month names to format with - and dd.MM.yyyy is
    /// the Czech numeric convention anyway.
    /// </summary>
    private void UpdateClock() =>
        _clockLabel.Text = DateTime.Now.ToString("dd.MM.yyyy   HH:mm:ss", CultureInfo.InvariantCulture);

    private void UpdateStatusBar()
    {
        var idle = _okCount == 0 && _warnCount == 0 && _errorCount == 0;
        _statusIdleLabel.Visible = idle;
        _statusChips.Visible = !idle;

        SetChip(_statusOkChip, $"Zpracováno: {_okCount}", _okCount > 0, UiTheme.Ok, UiTheme.OkSoft);
        SetChip(_statusWarnChip, $"Upozornění: {_warnCount}", _warnCount > 0, UiTheme.Warn, UiTheme.WarnSoft);
        SetChip(_statusErrorChip, $"Chyby: {_errorCount}", _errorCount > 0, UiTheme.Error, UiTheme.ErrorSoft);
    }

    private static void SetChip(Label chip, string text, bool active, Color activeColor, Color activeBack)
    {
        chip.Text = text;
        chip.ForeColor = active ? activeColor : UiTheme.Muted;
        chip.BackColor = active ? activeBack : UiTheme.MutedSoft;
    }

    // -------------------------------------------------------- opening results

    private string? SelectedXmlPath()
    {
        if (_logView.SelectedItems.Count == 0) return null;
        return (_logView.SelectedItems[0].Tag as LogRow)?.XmlPath;
    }

    private void OpenSelectedXml()
    {
        var path = SelectedXmlPath();
        if (path == null || !File.Exists(path)) return;

        TryStart(new ProcessStartInfo(path) { UseShellExecute = true });
    }

    private void ShowSelectedInFolder()
    {
        var path = SelectedXmlPath();
        if (path == null || !File.Exists(path)) return;

        TryStart(new ProcessStartInfo("explorer.exe", $"/select,\"{path}\""));
    }

    private void OpenOutputFolder()
    {
        if (_lastOutputDirectory == null || !Directory.Exists(_lastOutputDirectory)) return;

        TryStart(new ProcessStartInfo(_lastOutputDirectory) { UseShellExecute = true });
    }

    private void CopySelectedLine()
    {
        if (_logView.SelectedItems.Count == 0) return;

        var item = _logView.SelectedItems[0];
        try
        {
            Clipboard.SetText($"{item.SubItems[1].Text}  {item.SubItems[2].Text}");
        }
        catch (Exception ex)
        {
            Log($"Do schránky se nepodařilo zapsat: {ex.Message}", LogKind.Warn);
        }
    }

    private void TryStart(ProcessStartInfo startInfo)
    {
        try
        {
            Process.Start(startInfo);
        }
        catch (Exception ex)
        {
            Log($"Nepodařilo se otevřít: {ex.Message}", LogKind.Warn);
        }
    }

    // ----------------------------------------------------------------- misc

    private void MainForm_FormClosing(object? sender, FormClosingEventArgs e)
    {
        if (!_converting) return;

        var answer = MessageBox.Show(
            this,
            "Převod ještě běží. Opravdu chcete aplikaci zavřít?",
            "Probíhá převod",
            MessageBoxButtons.YesNo,
            MessageBoxIcon.Warning,
            MessageBoxDefaultButton.Button2);

        if (answer == DialogResult.No) e.Cancel = true;
    }

    private static Icon? TryLoadAppIcon()
    {
        try
        {
            // Pulls the icon straight from the .exe's own embedded resource
            // (set via <ApplicationIcon> in the .csproj) so the taskbar,
            // Alt-Tab and title bar all show the same icon without having to
            // ship and load a separate .ico file at runtime.
            return System.Drawing.Icon.ExtractAssociatedIcon(Application.ExecutablePath);
        }
        catch
        {
            return null;
        }
    }

    protected override void Dispose(bool disposing)
    {
        // Base first: the ListView still references _statusIcons and the drop
        // zone can still repaint with the fonts until the controls are gone.
        base.Dispose(disposing);

        if (disposing)
        {
            _clock.Stop();
            _clock.Dispose();
            _progressAnimation.Stop();
            _progressAnimation.Dispose();
            _tips.Dispose();
            _dropTitleFont.Dispose();
            _dropHintFont.Dispose();
            _logFont.Dispose();
            _tipFont.Dispose();
            _statusIcons.Dispose();
        }
    }
}
