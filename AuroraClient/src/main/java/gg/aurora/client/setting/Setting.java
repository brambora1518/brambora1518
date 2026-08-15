package gg.aurora.client.setting;

import java.util.function.BooleanSupplier;

import com.google.gson.JsonElement;

/**
 * One configurable value belonging to a module.
 *
 * <p>Settings are declared as fields on the module and register themselves with it on construction,
 * so the ClickGUI and the config writer both discover them without any per-module bookkeeping.
 *
 * <p>Each subclass owns its own JSON shape. Deserialization is deliberately forgiving: a value that
 * is missing, of the wrong type, or no longer legal leaves the setting at its current value rather
 * than throwing, so one stale entry in a hand-edited profile cannot stop the rest from loading.
 *
 * @param <T> type of the stored value
 */
public abstract class Setting<T> {

    private final String name;
    private final String description;
    private final T defaultValue;
    private T value;
    private BooleanSupplier visibility = () -> true;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String name() {
        return this.name;
    }

    public String description() {
        return this.description;
    }

    public T get() {
        return this.value;
    }

    public void set(T value) {
        this.value = this.clamp(value);
    }

    public T defaultValue() {
        return this.defaultValue;
    }

    public void reset() {
        this.value = this.defaultValue;
    }

    /**
     * Hides this setting in the ClickGUI unless {@code condition} holds. Used to collapse options
     * that only mean something when another setting is on, e.g. hiding a colour picker while its
     * tracer is disabled. Purely cosmetic — a hidden setting is still saved and still read.
     */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
        this.visibility = condition;
        // Self-type, so the call chains onto a field of the concrete setting type rather than
        // widening to Setting<T> and failing to assign.
        return (S) this;
    }

    public boolean isVisible() {
        return this.visibility.getAsBoolean();
    }

    /** Constrains an incoming value to whatever this setting considers legal. */
    protected T clamp(T value) {
        return value;
    }

    public abstract JsonElement serialize();

    public abstract void deserialize(JsonElement element);
}
