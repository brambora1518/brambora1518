package gg.aurora.client.module.modules.movement;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.DoubleSetting;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

/**
 * Raises how tall a block the player walks up without jumping.
 *
 * <p>Driven through the vanilla step-height attribute, so movement stays ordinary walking as far
 * as the server is concerned. The attribute is a server-synced value: on a server that has not
 * granted a higher one, the change is corrected away and the module has no visible effect.
 */
public final class Step extends Module {

    /** The value vanilla gives a player; restored on disable. */
    private static final double VANILLA_STEP_HEIGHT = 0.6D;

    private final DoubleSetting height =
            this.register(new DoubleSetting("Height", "Blocks to step up.", 1.0D, 0.6D, 3.0D, 1));

    public Step() {
        super("Step", "Walk up blocks without jumping.", Category.MOVEMENT);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        this.applyHeight(VANILLA_STEP_HEIGHT);
    }

    private void onTick(TickEvent event) {
        if (this.inGame()) {
            this.applyHeight(this.height.value());
        }
    }

    private void applyHeight(double value) {
        if (mc.player == null) {
            return;
        }

        EntityAttributeInstance attribute = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (attribute != null) {
            attribute.setBaseValue(value);
        }
    }

    @Override
    public String hudSuffix() {
        return String.format("%.1f", this.height.value());
    }
}
