package gg.aurora.client.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Typed publish/subscribe bus connecting the game's mixin hooks to the modules.
 *
 * <p>Subscriptions are made once, when a module is constructed, and stay for the lifetime of the
 * process. Whether a handler actually runs is decided per dispatch by the {@code active} predicate
 * it was registered with — for modules that is "is this module enabled". Keeping the subscription
 * list immutable at runtime avoids the concurrent-modification hazard of a module toggling itself
 * (or another module) from inside a handler.
 *
 * <p>A handler that throws is logged and unsubscribed rather than allowed to propagate; one broken
 * module must not take the game down with it, and a handler that failed once on a hot path such as
 * render or tick would otherwise spam the log thousands of times per minute.
 */
public final class EventBus {

    private static final Logger LOGGER = LoggerFactory.getLogger("Aurora/EventBus");

    private final Map<Class<? extends Event>, List<Subscription<? extends Event>>> subscriptions =
            new ConcurrentHashMap<>();

    /**
     * Registers a handler that runs on every dispatch of {@code type}.
     *
     * @param priority higher runs first; use it when one module must observe or veto before another
     */
    public <T extends Event> void subscribe(Class<T> type, Consumer<T> handler, BooleanSupplier active, int priority) {
        List<Subscription<? extends Event>> list =
                this.subscriptions.computeIfAbsent(type, key -> new ArrayList<>());

        synchronized (list) {
            list.add(new Subscription<>(handler, active, priority));
            list.sort(Comparator.comparingInt((Subscription<?> sub) -> sub.priority).reversed());
        }
    }

    public <T extends Event> void subscribe(Class<T> type, Consumer<T> handler, BooleanSupplier active) {
        this.subscribe(type, handler, active, 0);
    }

    /**
     * Delivers {@code event} to every active handler and returns it so callers can inspect the
     * result inline, e.g. {@code if (bus.post(new FooEvent()).isCancelled()) return;}
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> T post(T event) {
        List<Subscription<? extends Event>> list = this.subscriptions.get(event.getClass());
        if (list == null) {
            return event;
        }

        // Snapshot under the lock: a handler is free to subscribe during dispatch, and reading the
        // backing list directly while that happens would throw.
        Subscription<? extends Event>[] snapshot;
        synchronized (list) {
            snapshot = list.toArray(new Subscription[0]);
        }

        for (Subscription<? extends Event> raw : snapshot) {
            Subscription<T> subscription = (Subscription<T>) raw;
            if (subscription.dead || !subscription.active.getAsBoolean()) {
                continue;
            }

            try {
                subscription.handler.accept(event);
            } catch (Throwable throwable) {
                subscription.dead = true;
                LOGGER.error("Disabling a handler for {} after it threw", event.getClass().getSimpleName(), throwable);
            }
        }

        return event;
    }

    private static final class Subscription<T extends Event> {

        private final Consumer<T> handler;
        private final BooleanSupplier active;
        private final int priority;
        private volatile boolean dead;

        private Subscription(Consumer<T> handler, BooleanSupplier active, int priority) {
            this.handler = handler;
            this.active = active;
            this.priority = priority;
        }
    }
}
