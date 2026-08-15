package gg.aurora.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the private cooldown that gates how often the use key may act. */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Accessor("itemUseCooldown")
    void aurora$setItemUseCooldown(int cooldown);

    @Accessor("itemUseCooldown")
    int aurora$getItemUseCooldown();
}
