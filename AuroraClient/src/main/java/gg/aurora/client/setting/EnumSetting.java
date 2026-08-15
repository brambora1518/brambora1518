package gg.aurora.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A choice among the constants of an enum, drawn as a cycling button.
 *
 * <p>Persisted by constant name rather than ordinal, so reordering or inserting constants in a
 * later version does not silently reinterpret everyone's saved profiles.
 */
public final class EnumSetting<E extends Enum<E>> extends Setting<E> {

    private final E[] values;

    public EnumSetting(String name, String description, E defaultValue) {
        super(name, description, defaultValue);
        this.values = defaultValue.getDeclaringClass().getEnumConstants();
    }

    public E value() {
        return this.get();
    }

    public E[] values() {
        return this.values;
    }

    /** Advances to the next constant, wrapping at the end. */
    public void cycle() {
        this.set(this.values[(this.get().ordinal() + 1) % this.values.length]);
    }

    /** Steps back to the previous constant, wrapping at the start. */
    public void cycleBack() {
        this.set(this.values[(this.get().ordinal() - 1 + this.values.length) % this.values.length]);
    }

    public boolean is(E other) {
        return this.get() == other;
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(this.get().name());
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return;
        }

        String name = element.getAsString();
        for (E candidate : this.values) {
            if (candidate.name().equals(name)) {
                this.set(candidate);
                return;
            }
        }
        // Unknown constant — a renamed or removed option. Leave the default in place.
    }
}
