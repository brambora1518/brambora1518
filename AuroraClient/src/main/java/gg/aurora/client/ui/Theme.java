package gg.aurora.client.ui;

import com.google.gson.JsonObject;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.ColorSetting;
import gg.aurora.client.setting.IntSetting;
import gg.aurora.client.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Colours and metrics shared by the ClickGUI and the HUD.
 *
 * <p>Kept apart from any one module so a look can be changed in a single place, and so a saved
 * theme survives modules being added or removed. Persisted alongside the module config under its
 * own key.
 */
public final class Theme {

    private final List<Setting<?>> settings = new ArrayList<>();

    public final ColorSetting accent =
            this.add(new ColorSetting("Accent", "Highlight colour for enabled modules and sliders.", 0xFF8B5CF6));
    public final ColorSetting background =
            this.add(new ColorSetting("Background", "Panel background.", 0xE6121218));
    public final ColorSetting header =
            this.add(new ColorSetting("Header", "Category header background.", 0xFF1B1B24));
    public final ColorSetting text =
            this.add(new ColorSetting("Text", "Primary text.", 0xFFE8E8F0));
    public final ColorSetting textDim =
            this.add(new ColorSetting("Dim text", "Secondary text and descriptions.", 0xFF8A8A9E));
    public final ColorSetting outline =
            this.add(new ColorSetting("Outline", "Panel and element borders.", 0xFF2A2A38));

    public final IntSetting panelWidth =
            this.add(new IntSetting("Panel width", "Width of a ClickGUI category panel.", 116, 90, 200));
    public final IntSetting rowHeight =
            this.add(new IntSetting("Row height", "Height of one module or setting row.", 14, 11, 22));

    public final BoolSetting rainbowAccent =
            this.add(new BoolSetting("Rainbow accent", "Cycle the accent colour through the hue wheel.", false));
    public final IntSetting rainbowSpeed =
            this.add(new IntSetting("Rainbow speed", "Hue cycle period, in seconds.", 6, 1, 30));

    private <S extends Setting<?>> S add(S setting) {
        this.settings.add(setting);
        return setting;
    }

    public List<Setting<?>> settings() {
        return this.settings;
    }

    /**
     * The accent colour to draw with right now — the fixed accent, or the current point on the hue
     * wheel when {@link #rainbowAccent} is on.
     */
    public int accentNow() {
        if (!this.rainbowAccent.value()) {
            return this.accent.argb();
        }

        long period = Math.max(1L, this.rainbowSpeed.value()) * 1000L;
        float hue = (System.currentTimeMillis() % period) / (float) period;
        return (this.accent.argb() & 0xFF000000) | (java.awt.Color.HSBtoRGB(hue, 0.75F, 1.0F) & 0x00FFFFFF);
    }

    /** Same as {@link #accentNow()} with the alpha replaced, for fills under an accent outline. */
    public int accentNow(int alpha) {
        return (this.accentNow() & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        for (Setting<?> setting : this.settings) {
            json.add(setting.name(), setting.serialize());
        }
        return json;
    }

    public void deserialize(JsonObject json) {
        if (json == null) {
            return;
        }

        Map<String, Setting<?>> index = new java.util.HashMap<>();
        for (Setting<?> setting : this.settings) {
            index.put(setting.name(), setting);
        }

        for (String key : json.keySet()) {
            Setting<?> setting = index.get(key);
            if (setting != null) {
                setting.deserialize(json.get(key));
            }
        }
    }
}
