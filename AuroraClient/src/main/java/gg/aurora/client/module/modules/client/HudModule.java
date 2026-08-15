package gg.aurora.client.module.modules.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import gg.aurora.client.Aurora;
import gg.aurora.client.event.events.Render2DEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.EnumSetting;
import gg.aurora.client.ui.Theme;
import net.minecraft.client.gui.DrawContext;

/**
 * Draws the on-screen overlay: the list of enabled modules, plus optional readouts.
 *
 * <p>The module list is sorted by rendered width so the ragged edge falls on the inside, which is
 * the convention players expect and is much easier to read than alphabetical order.
 */
public final class HudModule extends Module {

    public enum ListPosition {
        TOP_LEFT,
        TOP_RIGHT
    }

    private static final int EDGE_PAD = 2;

    private final BoolSetting moduleList =
            this.register(new BoolSetting("Module list", "List the enabled modules.", true));
    private final EnumSetting<ListPosition> position =
            this.register(new EnumSetting<>("Position", "Corner for the module list.", ListPosition.TOP_RIGHT))
                    .visibleWhen(() -> this.moduleList.value());
    private final BoolSetting showSuffixes =
            this.register(new BoolSetting("Show details", "Append each module's mode or count.", true))
                    .visibleWhen(() -> this.moduleList.value());
    private final BoolSetting watermark =
            this.register(new BoolSetting("Watermark", "Show the client name and version.", true));
    private final BoolSetting coordinates =
            this.register(new BoolSetting("Coordinates", "Show your position.", true));
    private final BoolSetting fps =
            this.register(new BoolSetting("FPS", "Show the frame rate.", true));

    public HudModule() {
        super("HUD", "On-screen overlay.", Category.CLIENT);
        this.listen(Render2DEvent.class, this::onRender);
    }

    @Override
    protected void onEnable() {
        // Nothing to set up; the overlay is drawn straight from live state.
    }

    private void onRender(Render2DEvent event) {
        DrawContext context = event.context();
        Theme theme = Aurora.theme();

        if (this.watermark.value()) {
            context.drawText(mc.textRenderer, Aurora.NAME + " " + Aurora.VERSION,
                    EDGE_PAD + 1, EDGE_PAD + 1, theme.accentNow(), true);
        }

        this.drawBottomLeft(context, theme);

        if (this.moduleList.value()) {
            this.drawModuleList(context, theme);
        }
    }

    private void drawBottomLeft(DrawContext context, Theme theme) {
        List<String> lines = new ArrayList<>();

        if (this.fps.value()) {
            lines.add(mc.getCurrentFps() + " fps");
        }

        if (this.coordinates.value() && mc.player != null) {
            lines.add(String.format("%.0f, %.0f, %.0f",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        }

        if (lines.isEmpty()) {
            return;
        }

        int lineHeight = mc.textRenderer.fontHeight + 1;
        int y = context.getScaledWindowHeight() - EDGE_PAD - lines.size() * lineHeight;

        for (String line : lines) {
            context.drawText(mc.textRenderer, line, EDGE_PAD + 1, y, theme.text.argb(), true);
            y += lineHeight;
        }
    }

    private void drawModuleList(DrawContext context, Theme theme) {
        List<String> entries = new ArrayList<>();

        for (Module module : Aurora.modules().enabled()) {
            // The HUD listing itself, and the GUI that is open on top of it, are noise here.
            if (module.category() == Category.CLIENT) {
                continue;
            }

            String suffix = this.showSuffixes.value() ? module.hudSuffix() : null;
            entries.add(suffix == null || suffix.isEmpty()
                    ? module.name()
                    : module.name() + " " + suffix);
        }

        if (entries.isEmpty()) {
            return;
        }

        entries.sort(Comparator.comparingInt((String entry) -> mc.textRenderer.getWidth(entry)).reversed());

        boolean right = this.position.value() == ListPosition.TOP_RIGHT;
        int lineHeight = mc.textRenderer.fontHeight + 1;
        int y = this.watermark.value() ? EDGE_PAD + lineHeight + 1 : EDGE_PAD;

        // Only offset past the watermark on the side the watermark is actually on.
        if (right) {
            y = EDGE_PAD;
        }

        for (String entry : entries) {
            int width = mc.textRenderer.getWidth(entry);
            int x = right
                    ? context.getScaledWindowWidth() - EDGE_PAD - width
                    : EDGE_PAD + 1;

            context.drawText(mc.textRenderer, entry, x, y, theme.accentNow(), true);
            y += lineHeight;
        }
    }
}
