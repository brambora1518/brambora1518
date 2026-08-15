package gg.aurora.client.event;

import gg.aurora.client.Aurora;
import gg.aurora.client.event.events.Render2DEvent;
import gg.aurora.client.event.events.Render3DEvent;
import gg.aurora.client.event.events.TickEvent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Feeds the Fabric API's own callbacks into the client's {@link EventBus}.
 *
 * <p>Tick and render hooks come from Fabric rather than from mixins of our own: they are
 * maintained against each Minecraft release, which is where hand-written injection points break
 * first. Only what Fabric does not expose — raw key input, packet traffic, field of view — is
 * mixed in directly.
 */
public final class EventBridge {

    private EventBridge() {
    }

    public static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Modules assume a world and a player exist; posting on the menu screen would make
            // every handler repeat the same null checks.
            if (client.player != null && client.world != null) {
                Aurora.EVENTS.post(TickEvent.INSTANCE);
            }
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) {
                return;
            }
            Aurora.EVENTS.post(new Render2DEvent(context, tickCounter.getTickProgress(false)));
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) {
                return;
            }

            Aurora.EVENTS.post(new Render3DEvent(
                    context.matrixStack(),
                    context.consumers(),
                    context.camera(),
                    context.tickCounter().getTickProgress(false)
            ));
        });

        // Leaving a world must not carry module state into the next session.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null && Aurora.modules() != null) {
                for (var module : Aurora.modules().enabled()) {
                    if (module.category() != gg.aurora.client.module.Category.CLIENT) {
                        module.setEnabled(false);
                    }
                }
            }
        });
    }
}
