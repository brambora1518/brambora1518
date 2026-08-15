package gg.aurora.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A whole number constrained to {@code [min, max]}, drawn as a slider. */
public final class IntSetting extends Setting<Integer> {

    private final int min;
    private final int max;

    public IntSetting(String name, String description, int defaultValue, int min, int max) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        // The superclass stored the default before min/max existed, so re-apply the bounds now.
        this.set(defaultValue);
    }

    public int value() {
        return this.get();
    }

    public int min() {
        return this.min;
    }

    public int max() {
        return this.max;
    }

    /** Position of the current value within the range, as {@code 0..1}, for drawing the slider. */
    public double fraction() {
        return this.max == this.min ? 0.0D : (double) (this.get() - this.min) / (this.max - this.min);
    }

    /** Sets the value from a {@code 0..1} slider position. */
    public void setFraction(double fraction) {
        this.set((int) Math.round(this.min + fraction * (this.max - this.min)));
    }

    @Override
    protected Integer clamp(Integer value) {
        return Math.max(this.min, Math.min(this.max, value));
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(this.get());
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            try {
                this.set(element.getAsInt());
            } catch (NumberFormatException ignored) {
                // Leave the current value in place; a malformed entry should not break the profile.
            }
        }
    }
}
