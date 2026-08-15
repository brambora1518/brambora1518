package gg.aurora.client.mixin;

import gg.aurora.client.Aurora;
import gg.aurora.client.module.modules.movement.NoSlow;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Replaces the movement penalty applied while an item is in use.
 *
 * <p>Vanilla multiplies the movement vector by {@code 0.2} in
 * {@code applyMovementSpeedFactors} whenever the player is using an item and not riding. The
 * NoSlow module supplies its own multiplier in place of that constant.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @ModifyConstant(method = "applyMovementSpeedFactors", constant = @Constant(floatValue = 0.2F))
    private float aurora$modifyUseSlowdown(float original) {
        if (Aurora.modules() == null) {
            return original;
        }

        if (Aurora.modules().byName("NoSlow") instanceof NoSlow noSlow && noSlow.isEnabled()) {
            return noSlow.multiplier();
        }

        return original;
    }
}
