package gg.aurora.client.module.modules.render;

import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.IntSetting;

/**
 * Pushes the fog wall out so distant terrain stays visible.
 *
 * <p>Only the distance the fog is computed from changes — the world still loads and draws exactly
 * as far as the render distance allows, so this reveals terrain that was already being drawn
 * rather than extending how much is.
 */
public final class NoFog extends Module {

    private final IntSetting multiplier =
            this.register(new IntSetting("Multiplier", "How far to push the fog out.", 8, 2, 64));

    public NoFog() {
        super("NoFog", "Removes distance fog.", Category.RENDER);
    }

    /** Read from the fog mixin each frame. */
    public int multiplier() {
        return this.multiplier.value();
    }

    @Override
    public String hudSuffix() {
        return this.multiplier.value() + "x";
    }
}
