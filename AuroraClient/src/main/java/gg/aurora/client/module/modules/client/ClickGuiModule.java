package gg.aurora.client.module.modules.client;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.ui.ClickGuiScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Opens the module browser.
 *
 * <p>Modelled as a module so it gets a rebindable key for free. It stays enabled for as long as
 * the screen is up and turns itself off once the player closes it, so the bind reopens the screen
 * rather than toggling a flag nothing reads.
 */
public final class ClickGuiModule extends Module {

    public ClickGuiModule() {
        super("ClickGUI", "Opens the module browser.", Category.CLIENT, GLFW.GLFW_KEY_RIGHT_SHIFT);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onEnable() {
        mc.setScreen(new ClickGuiScreen());
    }

    private void onTick(TickEvent event) {
        if (!(mc.currentScreen instanceof ClickGuiScreen)) {
            this.setEnabled(false);
        }
    }
}
