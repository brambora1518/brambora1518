package gg.aurora.client.event;

/**
 * Marker for anything that can travel through the {@link EventBus}.
 *
 * <p>Events are plain mutable objects. Handlers may read and write their fields; the code that
 * posted the event inspects it afterwards to decide what the game should do. Instances are
 * generally short-lived and must not be retained past the {@code post} call that delivered them.
 */
public interface Event {
}
