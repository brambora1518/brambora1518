package gg.aurora.client.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Writes an option's backing field directly, bypassing the callbacks and the range check that
 * {@link SimpleOption#setValue} applies.
 *
 * <p>Needed for Fullbright: gamma is clamped to {@code 0..1} through the normal setter, and full
 * brightness needs a value well past that. Writing the field skips the change callback too, so the
 * value is not written back to {@code options.txt} and the player's real setting survives.
 */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor<T> {

    @Accessor("value")
    void aurora$setValue(T value);
}
