package gg.aurora.client.event;

/**
 * An event whose originating game action can be suppressed by a handler.
 *
 * <p>Cancelling does not stop delivery: every remaining handler still sees the event and can read
 * {@link #isCancelled()}, which lets a higher-priority module veto a lower-priority one's decision.
 * Only the code that posted the event acts on the final flag.
 */
public abstract class CancellableEvent implements Event {

    private boolean cancelled;

    public void cancel() {
        this.cancelled = true;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }
}
