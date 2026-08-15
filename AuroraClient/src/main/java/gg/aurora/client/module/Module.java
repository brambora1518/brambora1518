package gg.aurora.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import gg.aurora.client.Aurora;
import gg.aurora.client.event.Event;
import gg.aurora.client.setting.KeybindSetting;
import gg.aurora.client.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.glfw.GLFW;

/**
 * One toggleable feature.
 *
 * <p>Subclasses declare their options as fields wrapped in {@link #register}, and subscribe to the
 * events they need with {@link #listen} from their constructor. Both happen once, at construction;
 * enabling and disabling only flips a flag, which the bus consults per dispatch. That keeps
 * toggling free of allocation and makes it safe for a module to toggle itself from inside a
 * handler.
 *
 * <p>Modules are constructed during client init, before a world exists, so a constructor must not
 * touch {@link #mc}'s world or player. Use {@link #onEnable()} or an event handler for that.
 */
public abstract class Module {

    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();

    private final KeybindSetting keybind;

    private boolean enabled;

    protected Module(String name, String description, Category category) {
        this(name, description, category, GLFW.GLFW_KEY_UNKNOWN);
    }

    protected Module(String name, String description, Category category, int defaultKey) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keybind = this.register(new KeybindSetting("Keybind", "Key that toggles " + name + ".", defaultKey));
    }

    // ------------------------------------------------------------------
    // Declaration helpers, for use from subclass constructors
    // ------------------------------------------------------------------

    /** Adds a setting to this module and returns it, so it can be assigned to a field inline. */
    protected <S extends Setting<?>> S register(S setting) {
        this.settings.add(setting);
        return setting;
    }

    /** Subscribes a handler that runs only while this module is enabled. */
    protected <T extends Event> void listen(Class<T> type, Consumer<T> handler) {
        Aurora.EVENTS.subscribe(type, handler, this::isEnabled);
    }

    /**
     * Subscribes a handler that runs only while enabled, at a given priority (higher runs first).
     * Needed where two modules touch the same packet and the order decides the outcome.
     */
    protected <T extends Event> void listen(Class<T> type, Consumer<T> handler, int priority) {
        Aurora.EVENTS.subscribe(type, handler, this::isEnabled, priority);
    }

    /**
     * Subscribes a handler that runs whether or not this module is enabled — for the few modules
     * that must keep observing state while off, such as one recording positions to replay later.
     */
    protected <T extends Event> void listenAlways(Class<T> type, Consumer<T> handler) {
        Aurora.EVENTS.subscribe(type, handler, () -> true);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public void toggle() {
        this.setEnabled(!this.enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }

        this.enabled = enabled;

        try {
            if (enabled) {
                this.onEnable();
            } else {
                this.onDisable();
            }
        } catch (Throwable throwable) {
            // A module that fails to start must not stay half-on: force it off and keep the client
            // alive rather than propagating into the tick or render path that toggled it.
            Aurora.LOGGER.error("{} threw while being {}", this.name, enabled ? "enabled" : "disabled", throwable);
            this.enabled = false;
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    /** Called when the module turns on. Safe to touch the world and player if they exist. */
    protected void onEnable() {
    }

    /**
     * Called when the module turns off. Must undo anything that would otherwise outlive the module
     * — restored fields, released key binds, cleared render state — because it also runs on world
     * unload and on disconnect.
     */
    protected void onDisable() {
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public String name() {
        return this.name;
    }

    public String description() {
        return this.description;
    }

    public Category category() {
        return this.category;
    }

    public KeybindSetting keybind() {
        return this.keybind;
    }

    public List<Setting<?>> settings() {
        return Collections.unmodifiableList(this.settings);
    }

    /**
     * Extra text shown after the name in the HUD module list, e.g. a mode or a target count.
     * Return {@code null} for none.
     */
    public String hudSuffix() {
        return null;
    }

    // ------------------------------------------------------------------
    // Convenience for subclasses
    // ------------------------------------------------------------------

    protected ClientPlayerEntity player() {
        return mc.player;
    }

    protected ClientWorld world() {
        return mc.world;
    }

    /** True when a world and player exist, i.e. when it is safe for a handler to act. */
    protected boolean inGame() {
        return mc.player != null && mc.world != null;
    }
}
