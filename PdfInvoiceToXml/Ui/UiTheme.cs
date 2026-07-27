using System.Drawing.Drawing2D;
using System.Reflection;

namespace PdfInvoiceToXml.Ui;

/// <summary>
/// Single source of truth for the app's colours, fonts, corner radii and the
/// GDI+ helpers the custom-drawn controls share.
///
/// The palette follows Apple's dark-mode system colours rather than the stock
/// WinForms grey: a near-black page tint with lighter cards floating on it,
/// hairline separators instead of hard borders, and elevation carried by the
/// surface getting lighter rather than by outlines. That layering - plus
/// generous padding and large corner radii - is most of what makes a window
/// read as "designed".
/// </summary>
internal static class UiTheme
{
    // The accent exists at two levels, and mixing them up is the classic
    // dark-theme contrast bug. Accent is Apple's dark-mode blue and is used
    // where the blue is the *foreground* on a dark surface - the upload glyph,
    // the progress bar, focus rings. AccentFill is a darker blue used where
    // white text sits *on top* of the blue; Apple's own #0A84FF only reaches
    // 3.65:1 against white, which is below the 4.5:1 needed for body text.
    public static readonly Color Accent = Color.FromArgb(10, 132, 255);
    public static readonly Color AccentFill = Color.FromArgb(10, 111, 214);
    public static readonly Color AccentFillHover = Color.FromArgb(34, 118, 216);
    public static readonly Color AccentFillPressed = Color.FromArgb(11, 92, 175);
    // A dark blue wash, not a pale one - a light tint would glare on this bg.
    public static readonly Color AccentSoft = Color.FromArgb(22, 46, 76);
    // Deeper, not lighter. When the drop zone highlights, the card itself turns
    // AccentSoft, so the glyph's disc has to go darker to stay distinct - and
    // darker also buys the bright blue glyph more contrast, where a lighter
    // disc would have squeezed it down to 2.9:1.
    public static readonly Color AccentSoftDeep = Color.FromArgb(12, 26, 44);

    // Apple's dark system backgrounds: the page sits at #1C1C1E and each level
    // of elevation gets lighter instead of gaining a border.
    public static readonly Color Background = Color.FromArgb(28, 28, 30);
    public static readonly Color Surface = Color.FromArgb(44, 44, 46);
    public static readonly Color SurfaceHover = Color.FromArgb(54, 54, 56);
    public static readonly Color Fill = Color.FromArgb(58, 58, 60);
    public static readonly Color FillHover = Color.FromArgb(72, 72, 74);
    public static readonly Color Border = Color.FromArgb(58, 58, 60);
    public static readonly Color Separator = Color.FromArgb(52, 52, 54);

    public static readonly Color Text = Color.FromArgb(245, 245, 247);
    public static readonly Color Muted = Color.FromArgb(152, 152, 157);
    public static readonly Color Disabled = Color.FromArgb(138, 138, 142);

    // Apple's dark-mode status colours - lighter and more saturated than their
    // light-mode counterparts so they stay legible on a dark surface.
    public static readonly Color Ok = Color.FromArgb(48, 209, 88);
    public static readonly Color Warn = Color.FromArgb(255, 159, 10);
    // Lightened from Apple's #FF453A, which only reaches 4.09:1 on the card.
    public static readonly Color Error = Color.FromArgb(255, 107, 97);

    public static readonly Color OkSoft = Color.FromArgb(21, 51, 30);
    public static readonly Color WarnSoft = Color.FromArgb(58, 42, 10);
    public static readonly Color ErrorSoft = Color.FromArgb(58, 26, 24);
    // Darker than the other chip fills on purpose: an inactive chip carries
    // Muted text, which needs the extra contrast the lighter fill denied it.
    public static readonly Color MutedSoft = Color.FromArgb(44, 44, 46);

    public const int CardRadius = 16;
    public const int DropZoneRadius = 18;

    private const string FallbackFamily = "Segoe UI";

    // Segoe UI Semibold is a real, separately installed family on Windows -
    // the closest thing to the weight Apple uses for headings. Deliberately
    // NOT using "Segoe UI Variable": it is a variable font and GDI+ predates
    // those, so it renders unpredictably in WinForms.
    private static readonly string SemiboldFamily =
        FamilyExists("Segoe UI Semibold") ? "Segoe UI Semibold" : FallbackFamily;

    public static Font Body(float size = 9.5f, FontStyle style = FontStyle.Regular) =>
        new(FallbackFamily, size, style);

    /// <summary>Heading weight - real Semibold where available, Bold as a fallback.</summary>
    public static Font Heading(float size) =>
        SemiboldFamily == FallbackFamily
            ? new Font(FallbackFamily, size, FontStyle.Bold)
            : new Font(SemiboldFamily, size, FontStyle.Regular);

    private static bool FamilyExists(string name)
    {
        try
        {
            // The Font constructor silently substitutes a missing family,
            // so availability has to be probed through FontFamily instead.
            using var family = new FontFamily(name);
            return true;
        }
        catch (ArgumentException)
        {
            return false;
        }
    }

    /// <summary>Rounded-rectangle path behind every card, chip and button in the UI.</summary>
    public static GraphicsPath RoundedRect(Rectangle bounds, int radius)
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

    /// <summary>
    /// Approximates a soft drop shadow by stroking concentric rounded outlines
    /// at decreasing opacity. GDI+ has no blur, and this is far cheaper than
    /// compositing a blurred bitmap on every repaint.
    /// </summary>
    public static void DrawSoftShadow(Graphics g, Rectangle card, int radius, int spread = 6)
    {
        for (var i = spread; i >= 1; i--)
        {
            // Stronger than a light theme would need: black on a near-black
            // page barely registers, and without it the cards lose their
            // grounding now that elevation is carried by surface lightness.
            var alpha = 28 - (i * 24 / spread);
            if (alpha <= 0) continue;

            var ring = Rectangle.Inflate(card, i, i);
            ring.Offset(0, 1);

            using var path = RoundedRect(ring, radius + i);
            using var pen = new Pen(Color.FromArgb(alpha, 0, 0, 0), 1.8f);
            g.DrawPath(pen, path);
        }
    }

    /// <summary>Fills a card with its shadow, background and hairline border in one call.</summary>
    public static void DrawCard(Graphics g, Rectangle card, int radius, Color fill, bool shadow = true)
    {
        g.SmoothingMode = SmoothingMode.AntiAlias;
        if (shadow) DrawSoftShadow(g, card, radius);

        using var path = RoundedRect(card, radius);
        using var brush = new SolidBrush(fill);
        g.FillPath(brush, path);

        using var pen = new Pen(Border);
        g.DrawPath(pen, path);
    }

    /// <summary>Clips a control to a pill shape. Used for the BETA badge and status chips.</summary>
    public static void ApplyPillRegion(Control control)
    {
        if (control.Width <= 0 || control.Height <= 0) return;
        control.Region = new Region(RoundedRect(new Rectangle(0, 0, control.Width, control.Height), control.Height / 2));
    }

    /// <summary>
    /// Turns on double buffering for a stock control. DoubleBuffered is
    /// protected, so reflection is the only way to reach it from outside -
    /// without it the custom-painted panels flicker badly while resizing.
    /// </summary>
    public static void EnableDoubleBuffer(Control control)
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
}
