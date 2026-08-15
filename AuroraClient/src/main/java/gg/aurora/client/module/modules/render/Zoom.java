package gg.aurora.client.module.modules.render;

import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.event.events.TickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Narrows the field of view while held, for reading distant terrain.
 *
 * <p>The divisor eases towards its target rather than snapping, because an instant field-of-view
 * change is disorienting and makes it hard to keep track of what you were looking at.
 */
public final class Zoom extends Module {

    private final DoubleSetting amount =
            this.register(new DoubleSetting("Amount", "How far to zoom in. Higher is closer.", 4.0D, 1.5D, 12.0D, 1));
    private final BoolSetting smooth =
            this.register(new BoolSetting("Smooth", "Ease in and out instead of snapping.", true));
    private final DoubleSetting speed =
            this.register(new DoubleSetting("Speed", "How quickly the ease settles.", 0.35D, 0.05D, 1.0D, 2))
                    .visibleWhen(() -> this.smooth.value());

    /** Eased value the renderer actually divides by; 1.0 means no zoom. */
    private double currentDivisor = 1.0D;

    public Zoom() {
        super("Zoom", "Narrows the field of view while the bind is held.", Category.RENDER, GLFW.GLFW_KEY_C);
        this.listen(TickEvent.class, this::onTick);
    }

    private void onTick(TickEvent event) {
        this.advance(this.amount.value());
    }

    @Override
    protected void onDisable() {
        // The renderer stops consulting us the moment we are off, so reset rather than ease out;
        // otherwise the next zoom would start from a stale divisor.
        this.currentDivisor = 1.0D;
    }

    private void advance(double target) {
        if (!this.smooth.value()) {
            this.currentDivisor = target;
            return;
        }

        this.currentDivisor += (target - this.currentDivisor) * this.speed.value();
    }

    /** Divisor applied to the vanilla field of view. Read from the render thread each frame. */
    public float currentDivisor() {
        // Guard against a divisor that has not been ticked yet, which would divide by zero.
        return (float) Math.max(1.0D, this.currentDivisor);
    }

    @Override
    public String hudSuffix() {
        return String.format("%.1fx", this.amount.value());
    }
}
