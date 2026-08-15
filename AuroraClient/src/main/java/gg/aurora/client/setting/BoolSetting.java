package gg.aurora.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An on/off switch, drawn as a checkbox. */
public final class BoolSetting extends Setting<Boolean> {

    public BoolSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public boolean value() {
        return this.get();
    }

    public void toggle() {
        this.set(!this.get());
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(this.get());
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            this.set(element.getAsBoolean());
        }
    }
}
