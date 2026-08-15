package gg.aurora.client.mixin;

import gg.aurora.client.Aurora;
import gg.aurora.client.module.modules.render.Freecam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves the camera away from the player while Freecam is on. */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Invoker("setPos")
    abstract void aurora$setPos(Vec3d pos);

    @Invoker("setRotation")
    abstract void aurora$setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("RETURN"))
    private void aurora$onUpdate(BlockView area, Entity focused, boolean thirdPerson, boolean inverse,
                                 float tickDelta, CallbackInfo info) {
        if (Aurora.modules() == null) {
            return;
        }

        // Applied after vanilla has finished positioning the camera, so the override is not
        // immediately overwritten by the normal follow logic.
        if (Aurora.modules().byName("Freecam") instanceof Freecam freecam && freecam.isEnabled()) {
            this.aurora$setPos(freecam.cameraPos(tickDelta));
            this.aurora$setRotation(freecam.cameraYaw(), freecam.cameraPitch());
        }
    }
}
