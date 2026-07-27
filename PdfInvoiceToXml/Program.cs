using System.Windows.Forms;

namespace PdfInvoiceToXml;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        Application.SetHighDpiMode(HighDpiMode.SystemAware);
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);

        // Without these an unexpected exception drops the user into the raw
        // .NET crash dialog (or kills the process silently), which looks like
        // the app simply vanished.
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (_, e) => ReportFatal(e.Exception);
        AppDomain.CurrentDomain.UnhandledException += (_, e) => ReportFatal(e.ExceptionObject as Exception);

        Application.Run(new MainForm());
    }

    private static void ReportFatal(Exception? ex)
    {
        MessageBox.Show(
            "V aplikaci došlo k neočekávané chybě.\n\n" +
            (ex?.Message ?? "Neznámá chyba.") +
            "\n\nPokud se chyba opakuje, pošlete prosím tuto hlášku spolu s PDF fakturou, u které nastala.",
            "Neočekávaná chyba",
            MessageBoxButtons.OK,
            MessageBoxIcon.Error);
    }
}
