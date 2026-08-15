package gg.aurora.client.module.modules.player;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.mixin.MinecraftClientAccessor;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.IntSetting;

/**
 * Shortens the pause between repeated right-clicks.
 *
 * <p>Vanilla waits four ticks between uses while the key is held. Lowering that speeds up
 * placing blocks and eating; a delay of zero acts every tick.
 */
public final class FastPlace extends Module {

    /** What vanilla puts back after each use. */
    private static final int VANILLA_COOLDOWN = 4;

    private final IntSetting delay =
            this.register(new IntSetting("Delay", "Ticks between uses. 0 is every tick.", 0, 0, VANILLA_COOLDOWN));

    public FastPlace() {
        super("FastPlace", "Removes the delay between repeated right-clicks.", Category.PLAYER);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        this.setCooldown(VANILLA_COOLDOWN);
    }

    private void onTick(TickEvent event) {
        if (!this.inGame() || mc.currentScreen != null) {
            return;
        }

        // Vanilla resets the counter after every use, so it has to be clamped again each tick
        // rather than set once on enable.
        if (this.accessor().aurora$getItemUseCooldown() > this.delay.value()) {
            this.setCooldown(this.delay.value());
        }
    }

    private void setCooldown(int ticks) {
        this.accessor().aurora$setItemUseCooldown(ticks);
    }

    private MinecraftClientAccessor accessor() {
        return (MinecraftClientAccessor) mc;
    }
}
