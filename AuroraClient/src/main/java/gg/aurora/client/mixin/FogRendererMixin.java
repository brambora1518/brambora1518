package gg.aurora.client.mixin;

import gg.aurora.client.Aurora;
import gg.aurora.client.module.modules.render.NoFog;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Inflates the view distance the fog is computed from.
 *
 * <p>Fog density is derived from the render distance, so multiplying that one input pushes the
 * fog wall out without touching how much of the world is actually loaded or drawn.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, index = 2)
    private int aurora$modifyViewDistance(int viewDistance) {
        if (Aurora.modules() == null) {
            return viewDistance;
        }

        if (Aurora.modules().byName("NoFog") instanceof NoFog noFog && noFog.isEnabled()) {
            return (int) Math.min(Integer.MAX_VALUE, viewDistance * (long) noFog.multiplier());
        }

        return viewDistance;
    }
}
