package gg.aurora.client.module.modules.movement;

import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.DoubleSetting;

/**
 * Reduces or removes the movement penalty for using an item.
 *
 * <p>The penalty is client-side only: the server derives your speed from the positions you send,
 * so removing it changes how fast you actually travel while eating or drawing a bow. A server
 * that checks movement speed sees the difference.
 */
public final class NoSlow extends Module {

    private final DoubleSetting factor =
            this.register(new DoubleSetting("Factor",
                    "Movement kept while using an item. 0.2 is vanilla, 1.0 is no penalty.",
                    1.0D, 0.2D, 1.0D, 2));

    public NoSlow() {
        super("NoSlow", "Removes the slowdown from using items.", Category.MOVEMENT);
    }

    /** Read from the movement mixin each tick the player is using an item. */
    public float multiplier() {
        return (float) this.factor.value();
    }

    @Override
    public String hudSuffix() {
        return String.format("%.2f", this.factor.value());
    }
}
