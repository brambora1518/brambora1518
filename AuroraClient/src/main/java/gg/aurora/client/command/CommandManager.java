package gg.aurora.client.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import gg.aurora.client.Aurora;
import gg.aurora.client.event.events.PacketEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.module.modules.world.SchematicBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * Chat commands, for the things a mouse-driven GUI is bad at — naming a file, typing a number.
 *
 * <p>Messages beginning with the prefix are swallowed on their way out, so a mistyped command
 * lands in the client rather than in front of everyone on the server. That interception happens on
 * the netty thread, so the command itself is queued and run on the next client tick, where
 * touching the world and the module list is safe.
 */
public final class CommandManager {

    public static final String PREFIX = ".";

    private final List<String> pending = new ArrayList<>();

    public CommandManager() {
        Aurora.EVENTS.subscribe(PacketEvent.Send.class, this::onSend, () -> true);
        Aurora.EVENTS.subscribe(gg.aurora.client.event.events.TickEvent.class, event -> this.drain(), () -> true);
    }

    private void onSend(PacketEvent.Send event) {
        if (!(event.packet() instanceof ChatMessageC2SPacket chat)) {
            return;
        }

        String message = chat.chatMessage();
        if (!message.startsWith(PREFIX)) {
            return;
        }

        // Cancel first: whatever the command turns out to be, it must not reach the server.
        event.cancel();

        synchronized (this.pending) {
            this.pending.add(message.substring(PREFIX.length()));
        }
    }

    private void drain() {
        List<String> batch;

        synchronized (this.pending) {
            if (this.pending.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(this.pending);
            this.pending.clear();
        }

        for (String command : batch) {
            try {
                this.execute(command.trim());
            } catch (Exception exception) {
                Aurora.LOGGER.error("Command failed: {}", command, exception);
                error("Command failed: " + exception.getMessage());
            }
        }
    }

    private void execute(String line) {
        if (line.isEmpty()) {
            return;
        }

        String[] parts = line.split("\\s+");
        String name = parts[0].toLowerCase(Locale.ROOT);

        switch (name) {
            case "help" -> this.help();
            case "toggle", "t" -> this.toggle(parts);
            case "bind" -> this.bind(parts);
            case "list" -> this.list(parts);
            case "schem", "schematic" -> this.schematic(parts);
            case "profile" -> this.profile(parts);
            default -> error("Unknown command: " + name + ". Try " + PREFIX + "help");
        }
    }

    private void help() {
        info("Commands:");
        info("  " + PREFIX + "toggle <module>          turn a module on or off");
        info("  " + PREFIX + "bind <module> <key|none>  rebind a module");
        info("  " + PREFIX + "list [category]           list modules");
        info("  " + PREFIX + "schem list                list schematic files");
        info("  " + PREFIX + "schem load <file>         load a schematic");
        info("  " + PREFIX + "schem materials           what the current build still needs");
        info("  " + PREFIX + "profile save|load <name>  manage config profiles");
    }

    private void toggle(String[] parts) {
        if (parts.length < 2) {
            error("Usage: " + PREFIX + "toggle <module>");
            return;
        }

        Module module = Aurora.modules().byName(parts[1]);
        if (module == null) {
            error("No such module: " + parts[1]);
            return;
        }

        module.toggle();
        info(module.name() + " is now " + (module.isEnabled() ? "on" : "off"));
    }

    private void bind(String[] parts) {
        if (parts.length < 3) {
            error("Usage: " + PREFIX + "bind <module> <key|none>");
            return;
        }

        Module module = Aurora.modules().byName(parts[1]);
        if (module == null) {
            error("No such module: " + parts[1]);
            return;
        }

        String key = parts[2].toLowerCase(Locale.ROOT);

        if (key.equals("none") || key.equals("clear")) {
            module.keybind().clear();
            info(module.name() + " unbound");
            return;
        }

        // Only single letters and digits are accepted here; function keys and modifiers are
        // easier to set by clicking the bind row in the GUI and pressing the key.
        if (key.length() != 1) {
            error("Use a single letter or digit, or rebind from the ClickGUI for special keys");
            return;
        }

        char character = key.charAt(0);
        int code;

        if (character >= 'a' && character <= 'z') {
            code = GLFW.GLFW_KEY_A + (character - 'a');
        } else if (character >= '0' && character <= '9') {
            code = GLFW.GLFW_KEY_0 + (character - '0');
        } else {
            error("Unsupported key: " + key);
            return;
        }

        module.keybind().set(code);
        info(module.name() + " bound to " + module.keybind().displayName());
    }

    private void list(String[] parts) {
        if (parts.length >= 2) {
            for (Category category : Category.values()) {
                if (category.name().equalsIgnoreCase(parts[1])) {
                    this.listCategory(category);
                    return;
                }
            }
            error("No such category: " + parts[1]);
            return;
        }

        for (Category category : Category.values()) {
            this.listCategory(category);
        }
    }

    private void listCategory(Category category) {
        List<Module> modules = Aurora.modules().byCategory(category);
        if (modules.isEmpty()) {
            return;
        }

        StringBuilder line = new StringBuilder(category.displayName()).append(": ");
        for (int index = 0; index < modules.size(); index++) {
            Module module = modules.get(index);
            line.append(module.isEnabled() ? "§a" : "§7").append(module.name()).append("§r");
            if (index < modules.size() - 1) {
                line.append(", ");
            }
        }

        info(line.toString());
    }

    private void schematic(String[] parts) {
        if (!(Aurora.modules().byName("SchematicBuilder") instanceof SchematicBuilder builder)) {
            error("SchematicBuilder is not registered");
            return;
        }

        String action = parts.length >= 2 ? parts[1].toLowerCase(Locale.ROOT) : "help";

        switch (action) {
            case "list" -> {
                List<String> files = SchematicBuilder.available();
                if (files.isEmpty()) {
                    info("No schematics in " + SchematicBuilder.directory());
                    return;
                }
                info("Schematics:");
                files.forEach(file -> info("  " + file));
            }

            case "load" -> {
                if (parts.length < 3) {
                    error("Usage: " + PREFIX + "schem load <file>");
                    return;
                }
                // Re-join, so file names containing spaces survive the split.
                String file = String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length));
                info(builder.load(file));
            }

            case "materials" -> {
                List<String> materials = builder.remainingMaterials();
                if (materials.isEmpty()) {
                    info("Nothing pending — load a schematic and enable SchematicBuilder");
                    return;
                }
                info("Still needed:");
                materials.stream().limit(25).forEach(line -> info("  " + line));
            }

            default -> {
                info("Usage: " + PREFIX + "schem list|load <file>|materials");
            }
        }
    }

    private void profile(String[] parts) {
        if (parts.length < 2) {
            info("Active profile: " + Aurora.config().activeProfile());
            info("Available: " + String.join(", ", Aurora.config().profiles()));
            return;
        }

        String action = parts[1].toLowerCase(Locale.ROOT);
        String name = parts.length >= 3 ? parts[2] : Aurora.config().activeProfile();

        switch (action) {
            case "save" -> {
                Aurora.config().save(name);
                info("Saved profile '" + name + "'");
            }
            case "load" -> {
                Aurora.config().load(name);
                info("Loaded profile '" + name + "'");
            }
            default -> error("Usage: " + PREFIX + "profile save|load <name>");
        }
    }

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------

    private static void info(String message) {
        send(Text.literal("[" + Aurora.NAME + "] ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal(message).formatted(Formatting.WHITE)));
    }

    private static void error(String message) {
        send(Text.literal("[" + Aurora.NAME + "] ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal(message).formatted(Formatting.RED)));
    }

    private static void send(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(text, false);
        }
    }
}
