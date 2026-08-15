package gg.aurora.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A packed ARGB colour, drawn as a hue/saturation/brightness picker with an alpha strip.
 *
 * <p>Stored as {@code 0xAARRGGBB} because that is what {@code DrawContext} and the vertex
 * consumers take directly, and persisted as a hex string so a hand-edited profile stays readable.
 */
public final class ColorSetting extends Setting<Integer> {

    public ColorSetting(String name, String description, int defaultArgb) {
        super(name, description, defaultArgb);
    }

    public int argb() {
        return this.get();
    }

    /** Same colour with the alpha channel replaced by {@code alpha} ({@code 0..255}). */
    public int withAlpha(int alpha) {
        return (this.get() & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /** Same colour scaled to a fraction of its own alpha, for fills derived from an outline. */
    public int withAlphaFraction(double fraction) {
        int alpha = (int) Math.round(((this.get() >>> 24) & 0xFF) * Math.max(0.0D, Math.min(1.0D, fraction)));
        return this.withAlpha(alpha);
    }

    public int alpha() {
        return (this.get() >>> 24) & 0xFF;
    }

    public int red() {
        return (this.get() >>> 16) & 0xFF;
    }

    public int green() {
        return (this.get() >>> 8) & 0xFF;
    }

    public int blue() {
        return this.get() & 0xFF;
    }

    public void setRgb(int red, int green, int blue) {
        this.set((this.get() & 0xFF000000)
                | ((red & 0xFF) << 16)
                | ((green & 0xFF) << 8)
                | (blue & 0xFF));
    }

    public void setAlpha(int alpha) {
        this.set(this.withAlpha(alpha));
    }

    /** Builds a packed colour from hue/saturation/brightness in {@code 0..1}, keeping the alpha. */
    public void setHsb(float hue, float saturation, float brightness) {
        this.set((this.get() & 0xFF000000) | (java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF));
    }

    /** Current colour as {@code {hue, saturation, brightness}}, each in {@code 0..1}. */
    public float[] hsb() {
        return java.awt.Color.RGBtoHSB(this.red(), this.green(), this.blue(), null);
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(String.format("#%08X", this.get()));
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return;
        }

        String raw = element.getAsString().trim();
        if (raw.startsWith("#")) {
            raw = raw.substring(1);
        }

        try {
            // parseUnsignedLong, not parseInt: 0xFF...... overflows a signed int.
            this.set((int) Long.parseUnsignedLong(raw, 16));
        } catch (NumberFormatException ignored) {
            // Keep the current colour.
        }
    }
}
