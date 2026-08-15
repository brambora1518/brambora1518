package gg.aurora.client.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gg.aurora.client.Aurora;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.ColorSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.setting.EnumSetting;
import gg.aurora.client.setting.IntSetting;
import gg.aurora.client.setting.KeybindSetting;
import gg.aurora.client.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The module browser: one draggable panel per category, each listing its modules and their
 * settings.
 *
 * <p>Panel positions and which rows are expanded live in {@code static} state so the layout the
 * player arranged survives closing and reopening the screen. It is deliberately not persisted to
 * disk — a window layout is cheap to redo and not worth a config migration.
 *
 * <p>Controls: left click toggles a module, right click expands its settings, and dragging a
 * header moves the panel.
 */
public final class ClickGuiScreen extends Screen {

    private static final int PANEL_MARGIN = 8;
    private static final int PANEL_GAP = 6;
    private static final int TEXT_PAD = 4;

    /** Layout survives the screen being closed; see the class note. */
    private static final List<Panel> PANELS = new ArrayList<>();
    private static final Set<Module> EXPANDED = new HashSet<>();

    private final Theme theme = Aurora.theme();

    private Panel dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    /** Slider currently being dragged, so movement keeps affecting it once the cursor leaves it. */
    private Setting<?> activeSlider;
    private int activeSliderX;
    private int activeSliderWidth;

    /** Keybind row waiting for the next key press, or {@code null}. */
    private KeybindSetting listeningBind;

    public ClickGuiScreen() {
        super(Text.literal(Aurora.NAME));
    }

    @Override
    protected void init() {
        if (PANELS.isEmpty()) {
            int x = PANEL_MARGIN;
            for (Category category : Category.values()) {
                PANELS.add(new Panel(category, x, PANEL_MARGIN));
                x += this.theme.panelWidth.value() + PANEL_GAP;
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderInGameBackground(context);

        for (Panel panel : PANELS) {
            this.renderPanel(context, panel, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPanel(DrawContext context, Panel panel, int mouseX, int mouseY) {
        int width = this.theme.panelWidth.value();
        int rowHeight = this.theme.rowHeight.value();

        // Header
        context.fill(panel.x, panel.y, panel.x + width, panel.y + rowHeight, this.theme.header.argb());
        context.fill(panel.x, panel.y + rowHeight - 1, panel.x + width, panel.y + rowHeight,
                this.theme.accentNow());
        this.drawLabel(context, panel.category.displayName(), panel.x + TEXT_PAD, panel.y + rowHeight,
                this.theme.text.argb());

        String chevron = panel.open ? "-" : "+";
        int chevronWidth = this.textRenderer.getWidth(chevron);
        this.drawLabel(context, chevron, panel.x + width - TEXT_PAD - chevronWidth, panel.y + rowHeight,
                this.theme.textDim.argb());

        if (!panel.open) {
            return;
        }

        int y = panel.y + rowHeight;
        List<Module> modules = Aurora.modules().byCategory(panel.category);

        // Body background, drawn in one pass behind the rows.
        int bodyHeight = this.measureBody(modules, rowHeight);
        context.fill(panel.x, y, panel.x + width, y + bodyHeight, this.theme.background.argb());

        for (Module module : modules) {
            boolean hovered = this.isOver(mouseX, mouseY, panel.x, y, width, rowHeight);

            if (hovered) {
                context.fill(panel.x, y, panel.x + width, y + rowHeight, this.theme.accentNow(40));
            }

            int nameColor = module.isEnabled() ? this.theme.accentNow() : this.theme.text.argb();
            this.drawLabel(context, module.name(), panel.x + TEXT_PAD, y + rowHeight, nameColor);

            String suffix = module.hudSuffix();
            if (suffix != null && !suffix.isEmpty()) {
                int suffixWidth = this.textRenderer.getWidth(suffix);
                this.drawLabel(context, suffix, panel.x + width - TEXT_PAD - suffixWidth, y + rowHeight,
                        this.theme.textDim.argb());
            }

            y += rowHeight;

            if (EXPANDED.contains(module)) {
                y = this.renderSettings(context, panel, module, y, mouseX, mouseY);
            }
        }
    }

    private int renderSettings(DrawContext context, Panel panel, Module module, int y, int mouseX, int mouseY) {
        int width = this.theme.panelWidth.value();
        int rowHeight = this.theme.rowHeight.value();
        int indent = TEXT_PAD + 6;

        for (Setting<?> setting : module.settings()) {
            if (!setting.isVisible()) {
                continue;
            }

            context.fill(panel.x, y, panel.x + width, y + rowHeight, 0x30000000);

            if (setting instanceof BoolSetting bool) {
                this.drawLabel(context, setting.name(), panel.x + indent, y + rowHeight, this.theme.textDim.argb());
                this.drawCheckbox(context, panel.x + width - TEXT_PAD - 7, y + (rowHeight - 7) / 2, bool.value());

            } else if (setting instanceof IntSetting integer) {
                this.drawSlider(context, panel, y, setting.name(),
                        String.valueOf(integer.value()), integer.fraction(), indent);

            } else if (setting instanceof DoubleSetting decimal) {
                String formatted = String.format("%." + decimal.decimals() + "f", decimal.value());
                this.drawSlider(context, panel, y, setting.name(), formatted, decimal.fraction(), indent);

            } else if (setting instanceof EnumSetting<?> choice) {
                this.drawLabel(context, setting.name(), panel.x + indent, y + rowHeight, this.theme.textDim.argb());
                String value = choice.value().name();
                int valueWidth = this.textRenderer.getWidth(value);
                this.drawLabel(context, value, panel.x + width - TEXT_PAD - valueWidth, y + rowHeight,
                        this.theme.accentNow());

            } else if (setting instanceof KeybindSetting bind) {
                this.drawLabel(context, setting.name(), panel.x + indent, y + rowHeight, this.theme.textDim.argb());
                String value = this.listeningBind == bind ? "..." : bind.displayName();
                int valueWidth = this.textRenderer.getWidth(value);
                this.drawLabel(context, value, panel.x + width - TEXT_PAD - valueWidth, y + rowHeight,
                        this.listeningBind == bind ? this.theme.accentNow() : this.theme.text.argb());

            } else if (setting instanceof ColorSetting color) {
                this.drawLabel(context, setting.name(), panel.x + indent, y + rowHeight, this.theme.textDim.argb());
                int swatchX = panel.x + width - TEXT_PAD - 12;
                int swatchY = y + (rowHeight - 7) / 2;
                context.fill(swatchX, swatchY, swatchX + 12, swatchY + 7, color.argb());
                context.drawBorder(swatchX, swatchY, 12, 7, this.theme.outline.argb());
            }

            y += rowHeight;
        }

        return y;
    }

    private void drawSlider(DrawContext context, Panel panel, int y, String name, String value,
                            double fraction, int indent) {
        int width = this.theme.panelWidth.value();
        int rowHeight = this.theme.rowHeight.value();

        this.drawLabel(context, name, panel.x + indent, y + rowHeight, this.theme.textDim.argb());

        int valueWidth = this.textRenderer.getWidth(value);
        this.drawLabel(context, value, panel.x + width - TEXT_PAD - valueWidth, y + rowHeight,
                this.theme.text.argb());

        // Track sits along the bottom edge of the row so it never collides with the label.
        int trackX = panel.x + indent;
        int trackWidth = width - indent - TEXT_PAD;
        int trackY = y + rowHeight - 2;

        context.fill(trackX, trackY, trackX + trackWidth, trackY + 1, this.theme.outline.argb());
        context.fill(trackX, trackY, trackX + (int) Math.round(trackWidth * fraction), trackY + 1,
                this.theme.accentNow());
    }

    private void drawCheckbox(DrawContext context, int x, int y, boolean checked) {
        context.fill(x, y, x + 7, y + 7, checked ? this.theme.accentNow() : 0x00000000);
        context.drawBorder(x, y, 7, 7, checked ? this.theme.accentNow() : this.theme.outline.argb());
    }

    /**
     * Draws text vertically centred in a row whose bottom edge is at {@code rowBottom}, so callers
     * can pass the same y they use for the row's geometry.
     */
    private void drawLabel(DrawContext context, String text, int x, int rowBottom, int color) {
        int rowHeight = this.theme.rowHeight.value();
        int y = rowBottom - rowHeight + (rowHeight - this.textRenderer.fontHeight) / 2 + 1;
        context.drawText(this.textRenderer, text, x, y, color, false);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int width = this.theme.panelWidth.value();
        int rowHeight = this.theme.rowHeight.value();

        // Front to back, so a panel drawn on top wins a click over one beneath it.
        for (int index = PANELS.size() - 1; index >= 0; index--) {
            Panel panel = PANELS.get(index);

            if (this.isOver(mouseX, mouseY, panel.x, panel.y, width, rowHeight)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    this.dragging = panel;
                    this.dragOffsetX = (int) mouseX - panel.x;
                    this.dragOffsetY = (int) mouseY - panel.y;
                    this.bringToFront(index);
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    panel.open = !panel.open;
                }
                return true;
            }

            if (panel.open && this.clickBody(panel, mouseX, mouseY, button)) {
                this.bringToFront(index);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickBody(Panel panel, double mouseX, double mouseY, int button) {
        int width = this.theme.panelWidth.value();
        int rowHeight = this.theme.rowHeight.value();
        int y = panel.y + rowHeight;

        for (Module module : Aurora.modules().byCategory(panel.category)) {
            if (this.isOver(mouseX, mouseY, panel.x, y, width, rowHeight)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    module.toggle();
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (!EXPANDED.remove(module)) {
                        EXPANDED.add(module);
                    }
                }
                return true;
            }

            y += rowHeight;

            if (!EXPANDED.contains(module)) {
                continue;
            }

            for (Setting<?> setting : module.settings()) {
                if (!setting.isVisible()) {
                    continue;
                }

                if (this.isOver(mouseX, mouseY, panel.x, y, width, rowHeight)) {
                    this.clickSetting(setting, panel, mouseX, button);
                    return true;
                }

                y += rowHeight;
            }
        }

        return false;
    }

    private void clickSetting(Setting<?> setting, Panel panel, double mouseX, int button) {
        int width = this.theme.panelWidth.value();
        int indent = TEXT_PAD + 6;

        if (setting instanceof BoolSetting bool) {
            bool.toggle();

        } else if (setting instanceof EnumSetting<?> choice) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                choice.cycleBack();
            } else {
                choice.cycle();
            }

        } else if (setting instanceof KeybindSetting bind) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                bind.clear();
            } else {
                this.listeningBind = bind;
            }

        } else if (setting instanceof IntSetting || setting instanceof DoubleSetting) {
            this.activeSlider = setting;
            this.activeSliderX = panel.x + indent;
            this.activeSliderWidth = width - indent - TEXT_PAD;
            this.applySlider(mouseX);

        } else if (setting instanceof ColorSetting color) {
            // A full picker is a screen of its own; stepping the hue covers the common case of
            // telling two modules apart at a glance.
            float[] hsb = color.hsb();
            float step = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -0.05F : 0.05F;
            color.setHsb((hsb[0] + step + 1.0F) % 1.0F, Math.max(0.4F, hsb[1]), Math.max(0.5F, hsb[2]));
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.dragging != null) {
            this.dragging.x = (int) mouseX - this.dragOffsetX;
            this.dragging.y = (int) mouseY - this.dragOffsetY;
            return true;
        }

        if (this.activeSlider != null) {
            this.applySlider(mouseX);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = null;
        this.activeSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void applySlider(double mouseX) {
        double fraction = (mouseX - this.activeSliderX) / this.activeSliderWidth;
        fraction = Math.max(0.0D, Math.min(1.0D, fraction));

        if (this.activeSlider instanceof IntSetting integer) {
            integer.setFraction(fraction);
        } else if (this.activeSlider instanceof DoubleSetting decimal) {
            decimal.setFraction(fraction);
        }
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (this.listeningBind != null) {
            // Escape cancels the rebind rather than binding escape, which would be unrecoverable.
            this.listeningBind.set(key == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : key);
            this.listeningBind = null;
            return true;
        }

        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public void close() {
        // The layout is worth keeping, but the settings the player just changed are worth more.
        Aurora.config().save();
        super.close();
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    private int measureBody(List<Module> modules, int rowHeight) {
        int rows = 0;

        for (Module module : modules) {
            rows++;
            if (EXPANDED.contains(module)) {
                for (Setting<?> setting : module.settings()) {
                    if (setting.isVisible()) {
                        rows++;
                    }
                }
            }
        }

        return rows * rowHeight;
    }

    private boolean isOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void bringToFront(int index) {
        PANELS.add(PANELS.remove(index));
    }

    /** One category's window. */
    private static final class Panel {

        private final Category category;
        private int x;
        private int y;
        private boolean open = true;

        private Panel(Category category, int x, int y) {
            this.category = category;
            this.x = x;
            this.y = y;
        }
    }
}
