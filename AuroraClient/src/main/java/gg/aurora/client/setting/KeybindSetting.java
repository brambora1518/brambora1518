package gg.aurora.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.lwjgl.glfw.GLFW;

/**
 * A single GLFW key code, or {@link GLFW#GLFW_KEY_UNKNOWN} for "not bound".
 *
 * <p>Persisted by numeric code. GLFW codes are layout-independent — they name the physical key —
 * so a profile written on QWERTY binds the same physical key on AZERTY, which is what a player
 * moving between machines expects.
 */
public final class KeybindSetting extends Setting<Integer> {

    public KeybindSetting(String name, String description, int defaultKey) {
        super(name, description, defaultKey);
    }

    public int key() {
        return this.get();
    }

    public boolean isBound() {
        return this.get() != GLFW.GLFW_KEY_UNKNOWN;
    }

    public void clear() {
        this.set(GLFW.GLFW_KEY_UNKNOWN);
    }

    public boolean matches(int key) {
        return this.isBound() && this.get() == key;
    }

    /** Human-readable label for the bound key, for the ClickGUI and the bind list. */
    public String displayName() {
        if (!this.isBound()) {
            return "NONE";
        }

        String name = GLFW.glfwGetKeyName(this.get(), 0);
        if (name != null && !name.isEmpty()) {
            return name.toUpperCase(java.util.Locale.ROOT);
        }

        // Keys with no printable name (F-keys, modifiers, the keypad) come back null from GLFW.
        return switch (this.get()) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_INSERT -> "INSERT";
            case GLFW.GLFW_KEY_DELETE -> "DELETE";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_PAGE_UP -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN";
            default -> {
                if (this.get() >= GLFW.GLFW_KEY_F1 && this.get() <= GLFW.GLFW_KEY_F25) {
                    yield "F" + (this.get() - GLFW.GLFW_KEY_F1 + 1);
                }
                yield "KEY " + this.get();
            }
        };
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
                // Keep the current bind.
            }
        }
    }
}
