package gg.aurora.client.module.modules.render;

import gg.aurora.client.mixin.SimpleOptionAccessor;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.event.events.TickEvent;

/**
 * Lights the world uniformly by driving gamma past the value the options screen allows.
 *
 * <p>The player's real gamma is captured on enable and put back on disable. Writing through the
 * accessor skips the option's change callback, so nothing is written to {@code options.txt} and
 * the setting survives even if the game exits while this module is on.
 */
public final class Fullbright extends Module {

    private final DoubleSetting brightness =
            this.register(new DoubleSetting("Brightness", "Gamma to apply. 1.0 is the vanilla maximum.",
                    15.0D, 1.0D, 20.0D, 1));

    private Double savedGamma;

    public Fullbright() {
        super("Fullbright", "Removes darkness without touching your gamma setting.", Category.RENDER);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onEnable() {
        if (mc.options == null) {
            return;
        }

        this.savedGamma = mc.options.getGamma().getValue();
        this.apply(this.brightness.value());
    }

    @Override
    protected void onDisable() {
        if (mc.options == null || this.savedGamma == null) {
            return;
        }

        this.apply(this.savedGamma);
        this.savedGamma = null;
    }

    private void onTick(TickEvent event) {
        // Reapplied every tick: the options screen and some resource-pack reloads rewrite gamma
        // from the stored value, which would otherwise quietly undo this mid-session.
        this.apply(this.brightness.value());
    }

    @SuppressWarnings("unchecked")
    private void apply(double gamma) {
        ((SimpleOptionAccessor<Double>) (Object) mc.options.getGamma()).aurora$setValue(gamma);
    }
}
