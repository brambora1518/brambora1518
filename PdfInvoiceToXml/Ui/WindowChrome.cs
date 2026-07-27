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
    private const int DwmwaWindowCornerPreference = 33;
    private const int DwmwaBorderColor = 34;
    private const int DwmwaCaptionColor = 35;
    private const int DwmwaTextColor = 36;

    private const int DwmwcpRound = 2;

    [DllImport("dwmapi.dll", PreserveSig = true)]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);

    /// <summary>Rounds the window's outer corners and tints the caption to match the header.</summary>
    public static void Apply(IntPtr handle, Color caption, Color captionText, Color border)
    {
        if (handle == IntPtr.Zero) return;

        TrySet(handle, DwmwaWindowCornerPreference, DwmwcpRound);
        TrySet(handle, DwmwaCaptionColor, ToColorRef(caption));
        TrySet(handle, DwmwaTextColor, ToColorRef(captionText));
        TrySet(handle, DwmwaBorderColor, ToColorRef(border));
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
