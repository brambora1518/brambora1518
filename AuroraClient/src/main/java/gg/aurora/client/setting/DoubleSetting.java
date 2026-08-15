package gg.aurora.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A decimal number constrained to {@code [min, max]}, drawn as a slider. */
public final class DoubleSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final int decimals;

    public DoubleSetting(String name, String description, double defaultValue, double min, double max) {
        this(name, description, defaultValue, min, max, 2);
    }

    public DoubleSetting(String name, String description, double defaultValue, double min, double max, int decimals) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.decimals = decimals;
        this.set(defaultValue);
    }

    public double value() {
        return this.get();
    }

    public double min() {
        return this.min;
    }

    public double max() {
        return this.max;
    }

    public int decimals() {
        return this.decimals;
    }

    public double fraction() {
        return this.max == this.min ? 0.0D : (this.get() - this.min) / (this.max - this.min);
    }

    public void setFraction(double fraction) {
        this.set(this.min + fraction * (this.max - this.min));
    }

    @Override
    protected Double clamp(Double value) {
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
                this.set(element.getAsDouble());
            } catch (NumberFormatException ignored) {
                // Keep the current value rather than failing the whole profile load.
            }
        }
    }
}
