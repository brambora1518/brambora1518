package gg.aurora.client;

import gg.aurora.client.command.CommandManager;
import gg.aurora.client.config.ConfigManager;
import gg.aurora.client.event.EventBridge;
import gg.aurora.client.event.EventBus;
import gg.aurora.client.module.ModuleManager;
import gg.aurora.client.module.modules.client.ClickGuiModule;
import gg.aurora.client.module.modules.client.HudModule;
import gg.aurora.client.module.modules.combat.AimAssist;
import gg.aurora.client.module.modules.combat.AutoClicker;
import gg.aurora.client.module.modules.combat.AutoCrystal;
import gg.aurora.client.module.modules.combat.AutoShieldBreaker;
import gg.aurora.client.module.modules.combat.AutoSpear;
import gg.aurora.client.module.modules.combat.AutoTotem;
import gg.aurora.client.module.modules.combat.AutoWindCharge;
import gg.aurora.client.module.modules.combat.BowAimbot;
import gg.aurora.client.module.modules.combat.MaceHit;
import gg.aurora.client.module.modules.combat.Reach;
import gg.aurora.client.module.modules.combat.SafeAnchor;
import gg.aurora.client.module.modules.combat.TriggerBot;
import gg.aurora.client.module.modules.movement.NoSlow;
import gg.aurora.client.module.modules.movement.Sprint;
import gg.aurora.client.module.modules.movement.Step;
import gg.aurora.client.module.modules.player.AutoRefill;
import gg.aurora.client.module.modules.player.FastPlace;
import gg.aurora.client.module.modules.render.Freecam;
import gg.aurora.client.module.modules.render.Fullbright;
import gg.aurora.client.module.modules.render.Hitboxes;
import gg.aurora.client.module.modules.render.NoFog;
import gg.aurora.client.module.modules.render.PearlPrediction;
import gg.aurora.client.module.modules.render.PlayerEsp;
import gg.aurora.client.module.modules.render.Zoom;
import gg.aurora.client.module.modules.world.AutoBuilder;
import gg.aurora.client.module.modules.world.AutoMiner;
import gg.aurora.client.module.modules.world.ChunkFinder;
import gg.aurora.client.module.modules.world.SchematicBuilder;
import gg.aurora.client.ui.Theme;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point and service locator.
 *
 * <p>{@link #EVENTS} is a class-level constant rather than an instance field because modules
 * subscribe from their own constructors, which run before {@link #onInitializeClient()} returns.
 * Everything else is created in init order and exposed through a getter.
 */
public final class Aurora implements ClientModInitializer {

    public static final String NAME = "Aurora";
    public static final String VERSION = "0.1.0";

    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    /** Available before any module is constructed; see the class note. */
    public static final EventBus EVENTS = new EventBus();

    private static ModuleManager modules;
    private static ConfigManager config;
    private static CommandManager commands;
    private static Theme theme;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Starting {} v{}", NAME, VERSION);

        theme = new Theme();
        modules = new ModuleManager();

        modules.register(
                // Combat
                new AimAssist(),
                new AutoClicker(),
                new AutoCrystal(),
                new AutoShieldBreaker(),
                new AutoSpear(),
                new AutoTotem(),
                new AutoWindCharge(),
                new BowAimbot(),
                new MaceHit(),
                new Reach(),
                new SafeAnchor(),
                new TriggerBot(),
                // Movement
                new NoSlow(),
                new Sprint(),
                new Step(),
                // Render
                new Freecam(),
                new Fullbright(),
                new Hitboxes(),
                new NoFog(),
                new PearlPrediction(),
                new PlayerEsp(),
                new Zoom(),
                // Player
                new AutoRefill(),
                new FastPlace(),
                // World
                new AutoBuilder(),
                new AutoMiner(),
                new ChunkFinder(),
                new SchematicBuilder(),
                // Client
                new ClickGuiModule(),
                new HudModule()
        );

        commands = new CommandManager();
        EventBridge.install();

        config = new ConfigManager(modules, theme);
        config.load();

        // The game gives no callback for a clean shutdown, so persist on exit instead of trying to
        // catch every path that could close the window.
        Runtime.getRuntime().addShutdownHook(new Thread(config::save, "Aurora-Save"));

        LOGGER.info("Loaded {} modules", modules.all().size());
    }

    public static ModuleManager modules() {
        return modules;
    }

    public static CommandManager commands() {
        return commands;
    }

    public static ConfigManager config() {
        return config;
    }

    public static Theme theme() {
        return theme;
    }
}
