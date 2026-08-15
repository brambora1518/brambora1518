package gg.aurora.client.mixin;

import gg.aurora.client.Aurora;
import gg.aurora.client.event.events.KeyEvent;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Turns raw GLFW keyboard callbacks into {@link KeyEvent}s. */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void aurora$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo info) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Only the game window's own events, and only while nothing has focus — otherwise a bind
        // would fire while the player is typing in chat or rebinding a key in the ClickGUI.
        if (window != client.getWindow().getHandle() || client.currentScreen != null) {
            return;
        }

        KeyEvent.Action mapped = switch (action) {
            case GLFW.GLFW_PRESS -> KeyEvent.Action.PRESS;
            case GLFW.GLFW_RELEASE -> KeyEvent.Action.RELEASE;
            case GLFW.GLFW_REPEAT -> KeyEvent.Action.REPEAT;
            default -> null;
        };

        if (mapped != null) {
            Aurora.EVENTS.post(new KeyEvent(key, scancode, modifiers, mapped));
        }
    }
}
