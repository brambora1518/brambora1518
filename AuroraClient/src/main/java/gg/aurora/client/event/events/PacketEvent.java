package gg.aurora.client.event.events;

import gg.aurora.client.event.CancellableEvent;
import net.minecraft.network.packet.Packet;

/**
 * Fired for traffic in either direction between the client and the server.
 *
 * <p>Cancelling a {@link Send} drops the packet before it reaches the network thread; cancelling a
 * {@link Receive} stops the client from applying it. Both are posted on the netty thread, not the
 * client thread, so a handler must not touch the world or the player directly — queue the work and
 * let {@link TickEvent} pick it up.
 */
public abstract class PacketEvent extends CancellableEvent {

    private final Packet<?> packet;

    protected PacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> packet() {
        return this.packet;
    }

    /** Client to server, posted before the packet is written to the channel. */
    public static final class Send extends PacketEvent {

        public Send(Packet<?> packet) {
            super(packet);
        }
    }

    /** Server to client, posted before the packet is applied to the client's world state. */
    public static final class Receive extends PacketEvent {

        public Receive(Packet<?> packet) {
            super(packet);
        }
    }
}
