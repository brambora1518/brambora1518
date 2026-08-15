package gg.aurora.client.event.events;

import gg.aurora.client.event.Event;

/**
 * Fired once per client tick (20 Hz), from the head of {@code MinecraftClient.tick}.
 *
 * <p>This is the right place for any logic whose rate should match the server's, such as scanning
 * for targets or deciding to swap an item. Logic that must look smooth to the eye belongs in
 * {@link Render3DEvent} instead, which runs per frame.
 *
 * <p>The world and player are guaranteed non-null: the hook does not post while either is missing.
 */
public final class TickEvent implements Event {

    public static final TickEvent INSTANCE = new TickEvent();

    private TickEvent() {
    }
}
