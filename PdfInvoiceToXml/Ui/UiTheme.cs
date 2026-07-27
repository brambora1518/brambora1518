using System.Drawing.Drawing2D;
using System.Reflection;

namespace PdfInvoiceToXml.Ui;

/// <summary>
/// Single source of truth for the app's colours, fonts and the handful of
/// GDI+ helpers the custom-drawn controls share. Keeping them here rather
/// than as literals scattered across the form is what makes the window read
/// as one designed product instead of a pile of default WinForms controls.
/// </summary>
internal static class UiTheme
{
    public static readonly Color Accent = Color.FromArgb(79, 70, 229);
    public static readonly Color AccentHover = Color.FromArgb(91, 82, 235);
    public static readonly Color AccentDark = Color.FromArgb(67, 56, 202);
    public static readonly Color AccentSoft = Color.FromArgb(238, 236, 254);

    public static readonly Color Background = Color.FromArgb(246, 247, 250);
    public static readonly Color Surface = Color.White;
    public static readonly Color SurfaceHover = Color.FromArgb(249, 250, 252);
    public static readonly Color Border = Color.FromArgb(226, 232, 240);

    public static readonly Color Text = Color.FromArgb(30, 41, 59);
    public static readonly Color Muted = Color.FromArgb(100, 110, 125);
    public static readonly Color Disabled = Color.FromArgb(163, 172, 184);

    public static readonly Color Ok = Color.FromArgb(21, 128, 61);
    public static readonly Color Warn = Color.FromArgb(161, 98, 7);
    public static readonly Color Error = Color.FromArgb(185, 28, 28);

    public static readonly Color OkSoft = Color.FromArgb(220, 252, 231);
    public static readonly Color WarnSoft = Color.FromArgb(254, 243, 199);
    public static readonly Color ErrorSoft = Color.FromArgb(254, 226, 226);
    public static readonly Color MutedSoft = Color.FromArgb(241, 245, 249);

    private const string FamilyName = "Segoe UI";

    public static Font Body(float size = 9.5f, FontStyle style = FontStyle.Regular) =>
        new(FamilyName, size, style);

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
