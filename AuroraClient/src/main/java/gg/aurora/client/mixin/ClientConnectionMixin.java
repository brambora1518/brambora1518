package gg.aurora.client.mixin;

import gg.aurora.client.Aurora;
import gg.aurora.client.event.events.PacketEvent;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Posts every packet in both directions and lets a handler drop it.
 *
 * <p>Both hooks run on netty's event loop, not the client thread. Handlers therefore must not
 * touch the world or the player from here — see {@link PacketEvent}.
 */
@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void aurora$onSend(Packet<?> packet, CallbackInfo info) {
        if (Aurora.EVENTS.post(new PacketEvent.Send(packet)).isCancelled()) {
            info.cancel();
        }
    }

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aurora$onReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo info) {
        if (Aurora.EVENTS.post(new PacketEvent.Receive(packet)).isCancelled()) {
            info.cancel();
        }
    }
}
