using System.Runtime.InteropServices;

namespace PdfInvoiceToXml.Ui;

/// <summary>
/// Applies the Windows 11 window-manager attributes that let a plain WinForms
/// form look like a modern native app: rounded outer corners, and a title bar
/// tinted to match the app's own header so the two read as one surface instead
/// of a coloured window glued under a grey system bar.
///
/// Every call is best-effort. These attributes only exist on Windows 11
/// (build 22000+); on Windows 10 DwmSetWindowAttribute simply returns a
/// failure HRESULT, which is ignored, and the window keeps square corners and
/// the default caption. Nothing here is required for the app to work.
/// </summary>
internal static class WindowChrome
{
    // 19 was the pre-release attribute id, replaced by 20 in build 18985. Both
    // are set so dark mode also works on the older Windows 10 builds that
    // support it at all.
    private const int DwmwaUseImmersiveDarkModeOld = 19;
    private const int DwmwaUseImmersiveDarkMode = 20;
    private const int DwmwaWindowCornerPreference = 33;
    private const int DwmwaBorderColor = 34;
    private const int DwmwaCaptionColor = 35;
    private const int DwmwaTextColor = 36;

    private const int DwmwcpRound = 2;

    [DllImport("dwmapi.dll", PreserveSig = true)]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);

    [DllImport("uxtheme.dll", CharSet = CharSet.Unicode, PreserveSig = true)]
    private static extern int SetWindowTheme(IntPtr hwnd, string? subAppName, string? subIdList);

    /// <summary>
    /// Rounds the window's outer corners, switches the caption to dark mode and
    /// tints it to match the header.
    /// </summary>
    public static void Apply(IntPtr handle, Color caption, Color captionText, Color border)
    {
        if (handle == IntPtr.Zero) return;

        TrySet(handle, DwmwaUseImmersiveDarkMode, 1);
        TrySet(handle, DwmwaUseImmersiveDarkModeOld, 1);
        TrySet(handle, DwmwaWindowCornerPreference, DwmwcpRound);
        TrySet(handle, DwmwaCaptionColor, ToColorRef(caption));
        TrySet(handle, DwmwaTextColor, ToColorRef(captionText));
        TrySet(handle, DwmwaBorderColor, ToColorRef(border));
    }

    /// <summary>
    /// Switches a common control to the dark visual style. Setting BackColor on
    /// a ListView leaves its scroll bars stubbornly light, because those are
    /// drawn by the theme engine rather than by WinForms; this is the only way
    /// to reach them. Available from Windows 10 1809 onwards.
    /// </summary>
    public static void ApplyDarkControlTheme(Control control)
    {
        if (!control.IsHandleCreated) return;

        try
        {
            SetWindowTheme(control.Handle, "DarkMode_Explorer", null);
        }
        catch (DllNotFoundException)
        {
        }
        catch (EntryPointNotFoundException)
        {
        }
    }

    private static void TrySet(IntPtr handle, int attribute, int value)
    {
        try
        {
            DwmSetWindowAttribute(handle, attribute, ref value, sizeof(int));
        }
        catch (DllNotFoundException)
        {
            // dwmapi.dll is always present on supported Windows versions, but
            // never let window dressing take the app down if it is not.
        }
        catch (EntryPointNotFoundException)
        {
        }
    }

    /// <summary>DWM wants a COLORREF (0x00BBGGRR), not .NET's ARGB ordering.</summary>
    private static int ToColorRef(Color c) => c.R | (c.G << 8) | (c.B << 16);
}
