package gg.aurora.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import gg.aurora.client.Aurora;
import gg.aurora.client.event.events.KeyEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Owns every module instance and routes key binds to them.
 *
 * <p>The registry is filled once during client init and never changes afterwards, so lookups need
 * no synchronisation. Names are matched case-insensitively because they arrive from chat commands
 * and hand-edited config files as much as from the GUI.
 */
public final class ModuleManager {

    private final List<Module> modules = new ArrayList<>();
    private final Map<String, Module> byName = new LinkedHashMap<>();
    private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);

    public ModuleManager() {
        for (Category category : Category.values()) {
            this.byCategory.put(category, new ArrayList<>());
        }

        Aurora.EVENTS.subscribe(KeyEvent.class, this::onKey, () -> true);
    }

    /** Adds modules to the registry. Called once, from client init. */
    public void register(Module... toRegister) {
        for (Module module : toRegister) {
            String key = module.name().toLowerCase(Locale.ROOT);
            Module previous = this.byName.put(key, module);
            if (previous != null) {
                // Two modules answering to one name would make binds and commands ambiguous.
                throw new IllegalStateException("Duplicate module name: " + module.name());
            }

            this.modules.add(module);
            this.byCategory.get(module.category()).add(module);
        }

        this.modules.sort(Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER));
        for (List<Module> list : this.byCategory.values()) {
            list.sort(Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER));
        }
    }

    private void onKey(KeyEvent event) {
        if (event.action() != KeyEvent.Action.PRESS || event.key() == GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }

        for (Module module : this.modules) {
            if (module.keybind().matches(event.key())) {
                module.toggle();
            }
        }
    }

    public List<Module> all() {
        return Collections.unmodifiableList(this.modules);
    }

    public List<Module> byCategory(Category category) {
        return Collections.unmodifiableList(this.byCategory.get(category));
    }

    /** Looks up a module by name, case-insensitively. Returns {@code null} if there is none. */
    public Module byName(String name) {
        return this.byName.get(name.toLowerCase(Locale.ROOT));
    }

    /** All currently enabled modules, in registration (alphabetical) order. */
    public List<Module> enabled() {
        List<Module> result = new ArrayList<>();
        for (Module module : this.modules) {
            if (module.isEnabled()) {
                result.add(module);
            }
        }
        return result;
    }

    /**
     * Turns everything off. Used when leaving a world, so no module carries state — a restored
     * field, a held key — across into the next session.
     */
    public void disableAll() {
        for (Module module : this.modules) {
            module.setEnabled(false);
        }
    }
}
