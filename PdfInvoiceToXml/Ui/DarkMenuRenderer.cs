namespace PdfInvoiceToXml.Ui;

/// <summary>
/// Renders context menus in the app's dark palette. Setting BackColor and
/// ForeColor on a ContextMenuStrip is not enough: the default professional
/// renderer paints the drop-down border, the image margin and the highlight
/// from its own light colour table, so a "dark" menu still comes out with a
/// white gutter down its left edge and a grey border.
/// </summary>
internal sealed class DarkMenuRenderer : ToolStripProfessionalRenderer
{
    public DarkMenuRenderer() : base(new DarkColorTable())
    {
    }

    protected override void OnRenderItemText(ToolStripItemTextRenderEventArgs e)
    {
        e.TextColor = e.Item?.Enabled == false ? UiTheme.Disabled : UiTheme.Text;
        base.OnRenderItemText(e);
    }

    private sealed class DarkColorTable : ProfessionalColorTable
    {
        public override Color ToolStripDropDownBackground => UiTheme.Surface;
        public override Color MenuBorder => UiTheme.Border;
        public override Color MenuItemBorder => UiTheme.AccentFill;

        // AccentFill rather than Accent: the item's label stays near-white
        // while highlighted, so the blue underneath has to carry white text.
        public override Color MenuItemSelected => UiTheme.AccentFill;
        public override Color MenuItemSelectedGradientBegin => UiTheme.AccentFill;
        public override Color MenuItemSelectedGradientEnd => UiTheme.AccentFill;

        // The gutter the menu reserves for icons - left light by default.
        public override Color ImageMarginGradientBegin => UiTheme.Surface;
        public override Color ImageMarginGradientMiddle => UiTheme.Surface;
        public override Color ImageMarginGradientEnd => UiTheme.Surface;

        public override Color SeparatorDark => UiTheme.Separator;
        public override Color SeparatorLight => UiTheme.Surface;
    }
}
