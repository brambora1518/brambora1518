package gg.aurora.client.event.events;

import gg.aurora.client.event.Event;
import net.minecraft.client.gui.DrawContext;

/**
 * Fired each frame after the vanilla HUD has drawn, in scaled screen coordinates.
 *
 * <p>Handlers draw overlays — the module list, coordinates, armour status — straight onto
 * {@link #context}. Coordinates are GUI-scaled pixels, not physical ones, so a layout written
 * against these numbers stays put across GUI-scale changes.
 */
public final class Render2DEvent implements Event {

    private final DrawContext context;
    private final float tickDelta;

    public Render2DEvent(DrawContext context, float tickDelta) {
        this.context = context;
        this.tickDelta = tickDelta;
    }

    public DrawContext context() {
        return this.context;
    }

    /** Fraction of the way through the current tick, for interpolating animated overlays. */
    public float tickDelta() {
        return this.tickDelta;
    }
}
