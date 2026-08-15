package gg.aurora.client.mixin;

import gg.aurora.client.Aurora;
import gg.aurora.client.module.Module;
import gg.aurora.client.module.modules.render.Zoom;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the Zoom module's divisor to the camera's field of view. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void aurora$onGetFov(Camera camera, float tickDelta, boolean changingFov,
                                 CallbackInfoReturnable<Float> info) {
        // The renderer is constructed before modules are registered, so this can fire during
        // startup with no manager yet.
        if (Aurora.modules() == null) {
            return;
        }

        Module module = Aurora.modules().byName("Zoom");
        if (module instanceof Zoom zoom && zoom.isEnabled()) {
            info.setReturnValue(info.getReturnValueF() / zoom.currentDivisor());
        }
    }
}
