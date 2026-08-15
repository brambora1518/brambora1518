package gg.aurora.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.aurora.client.Aurora;
import gg.aurora.client.module.Module;
import gg.aurora.client.module.ModuleManager;
import gg.aurora.client.setting.Setting;
import gg.aurora.client.ui.Theme;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Reads and writes named profiles under {@code .minecraft/config/aurora/}.
 *
 * <p>Writes go to a temporary file that is then moved over the real one, so a crash mid-save
 * cannot leave a half-written profile behind — the previous one stays intact until the new file is
 * complete. Loads never throw: a missing, malformed, or partially-recognised profile leaves the
 * affected settings at their defaults and logs, because losing a config should not stop the client
 * from starting.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_PROFILE = "default";

    private final ModuleManager modules;
    private final Theme theme;
    private final Path directory;

    private String activeProfile = DEFAULT_PROFILE;

    public ConfigManager(ModuleManager modules, Theme theme) {
        this.modules = modules;
        this.theme = theme;
        this.directory = FabricLoader.getInstance().getConfigDir().resolve("aurora");
    }

    public String activeProfile() {
        return this.activeProfile;
    }

    public Path directory() {
        return this.directory;
    }

    /** Profile names present on disk, without the {@code .json} suffix. */
    public List<String> profiles() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(this.directory)) {
            return names;
        }

        try (var stream = Files.list(this.directory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        String file = path.getFileName().toString();
                        names.add(file.substring(0, file.length() - ".json".length()));
                    });
        } catch (IOException exception) {
            Aurora.LOGGER.error("Could not list profiles in {}", this.directory, exception);
        }

        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public void load() {
        this.load(this.activeProfile);
    }

    /** Applies a profile to every module and to the theme. Unknown entries are ignored. */
    public void load(String profile) {
        Path file = this.fileFor(profile);
        if (!Files.isRegularFile(file)) {
            Aurora.LOGGER.info("No profile '{}' on disk; using defaults", profile);
            this.activeProfile = profile;
            return;
        }

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            Aurora.LOGGER.error("Could not read profile '{}'; keeping defaults", profile, exception);
            return;
        }

        if (root.has("theme") && root.get("theme").isJsonObject()) {
            this.theme.deserialize(root.getAsJsonObject("theme"));
        }

        JsonObject moduleSection = root.has("modules") && root.get("modules").isJsonObject()
                ? root.getAsJsonObject("modules")
                : new JsonObject();

        for (Module module : this.modules.all()) {
            String key = module.name().toLowerCase(Locale.ROOT);
            if (!moduleSection.has(key) || !moduleSection.get(key).isJsonObject()) {
                continue;
            }

            JsonObject entry = moduleSection.getAsJsonObject(key);

            if (entry.has("settings") && entry.get("settings").isJsonObject()) {
                JsonObject settings = entry.getAsJsonObject("settings");
                for (Setting<?> setting : module.settings()) {
                    if (settings.has(setting.name())) {
                        setting.deserialize(settings.get(setting.name()));
                    }
                }
            }

            // Applied last, so a module that reads its own settings in onEnable sees the loaded
            // values rather than the defaults.
            if (entry.has("enabled")) {
                try {
                    module.setEnabled(entry.get("enabled").getAsBoolean());
                } catch (Exception ignored) {
                    // Leave it off.
                }
            }
        }

        this.activeProfile = profile;
        Aurora.LOGGER.info("Loaded profile '{}'", profile);
    }

    public void save() {
        this.save(this.activeProfile);
    }

    public void save(String profile) {
        JsonObject root = new JsonObject();
        root.addProperty("version", Aurora.VERSION);
        root.add("theme", this.theme.serialize());

        JsonObject moduleSection = new JsonObject();
        for (Module module : this.modules.all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());

            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.settings()) {
                settings.add(setting.name(), setting.serialize());
            }
            entry.add("settings", settings);

            moduleSection.add(module.name().toLowerCase(Locale.ROOT), entry);
        }
        root.add("modules", moduleSection);

        Path file = this.fileFor(profile);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");

        try {
            Files.createDirectories(this.directory);

            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            this.activeProfile = profile;
        } catch (IOException exception) {
            Aurora.LOGGER.error("Could not save profile '{}'", profile, exception);
        }
    }

    /** Removes a profile from disk. The active profile cannot be deleted. */
    public boolean delete(String profile) {
        if (profile.equals(this.activeProfile)) {
            return false;
        }

        try {
            return Files.deleteIfExists(this.fileFor(profile));
        } catch (IOException exception) {
            Aurora.LOGGER.error("Could not delete profile '{}'", profile, exception);
            return false;
        }
    }

    private Path fileFor(String profile) {
        // Profile names reach here from a text field, so strip anything that could escape the
        // config directory or produce an illegal filename.
        String safe = profile.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) {
            safe = DEFAULT_PROFILE;
        }
        return this.directory.resolve(safe + ".json");
    }
}
