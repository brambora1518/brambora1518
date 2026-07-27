using System.Drawing.Drawing2D;

namespace PdfInvoiceToXml.Ui;

/// <summary>
/// A flat, pill-shaped button drawn by hand, with hover/pressed/disabled
/// states and a focus ring.
///
/// Both variants are filled, the way Apple's controls are: solid blue for the
/// primary action, a soft grey fill for everything else. Outlined buttons -
/// what the stock WinForms button gives you - are the main thing that makes a
/// toolbar look like a 2005 dialog.
/// </summary>
internal sealed class AccentButton : Control
{
    private bool _hover;
    private bool _pressed;

    public AccentButton()
    {
        SetStyle(
            ControlStyles.AllPaintingInWmPaint |
            ControlStyles.OptimizedDoubleBuffer |
            ControlStyles.ResizeRedraw |
            ControlStyles.UserPaint,
            true);

        Cursor = Cursors.Hand;
        TabStop = true;
        Height = 38;
        Font = UiTheme.Body(9.5f, FontStyle.Bold);
    }

    /// <summary>Solid accent fill for the primary action; soft grey for the rest.</summary>
    public bool Primary { get; set; }

    protected override void OnMouseEnter(EventArgs e)
    {
        _hover = true;
        Invalidate();
        base.OnMouseEnter(e);
    }

    protected override void OnMouseLeave(EventArgs e)
    {
        _hover = false;
        _pressed = false;
        Invalidate();
        base.OnMouseLeave(e);
    }

    protected override void OnMouseDown(MouseEventArgs e)
    {
        _pressed = true;
        Focus();
        Invalidate();
        base.OnMouseDown(e);
    }

    protected override void OnMouseUp(MouseEventArgs e)
    {
        _pressed = false;
        Invalidate();
        base.OnMouseUp(e);
    }

    protected override void OnEnabledChanged(EventArgs e)
    {
        // A button disabled mid-hover would otherwise keep that hover state and
        // look stuck once it is enabled again.
        _hover = false;
        _pressed = false;
        Invalidate();
        base.OnEnabledChanged(e);
    }

    protected override void OnGotFocus(EventArgs e)
    {
        Invalidate();
        base.OnGotFocus(e);
    }

    protected override void OnLostFocus(EventArgs e)
    {
        Invalidate();
        base.OnLostFocus(e);
    }

    protected override bool IsInputKey(Keys keyData) =>
        keyData is Keys.Space or Keys.Enter || base.IsInputKey(keyData);

    protected override void OnKeyDown(KeyEventArgs e)
    {
        if (e.KeyCode is Keys.Space or Keys.Enter)
        {
            OnClick(EventArgs.Empty);
            e.Handled = true;
        }

        base.OnKeyDown(e);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;

        // UserPaint means nothing draws the background for us, and the pill's
        // corners have to show the parent's colour rather than black.
        g.Clear(Parent?.BackColor ?? UiTheme.Background);

        var rect = new Rectangle(0, 0, Width - 1, Height - 1);
        var radius = Math.Max(2, Height / 2);
        using var path = UiTheme.RoundedRect(rect, radius);

        Color fill;
        Color textColor;

        if (!Enabled)
        {
            fill = Primary ? Color.FromArgb(178, 213, 246) : UiTheme.Fill;
            textColor = Primary ? Color.White : UiTheme.Disabled;
        }
        else if (Primary)
        {
            fill = _pressed ? UiTheme.AccentDark : _hover ? UiTheme.AccentHover : UiTheme.Accent;
            textColor = Color.White;
        }
        else
        {
            fill = _pressed ? UiTheme.Border : _hover ? UiTheme.FillHover : UiTheme.Fill;
            textColor = UiTheme.Text;
        }

        using (var brush = new SolidBrush(fill))
        {
            g.FillPath(brush, path);
        }

        if (Focused && Enabled)
        {
            var ring = Rectangle.Inflate(rect, -3, -3);
            using var ringPath = UiTheme.RoundedRect(ring, Math.Max(2, radius - 3));
            using var ringPen = new Pen(Primary ? Color.White : UiTheme.Accent) { DashStyle = DashStyle.Dot };
            g.DrawPath(ringPen, ringPath);
        }

        TextRenderer.DrawText(
            g, Text, Font, rect, textColor,
            TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
    }
}
