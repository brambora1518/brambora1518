package gg.aurora.client.schematic;

import java.util.Optional;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

/**
 * Turns the two ways schematics name a block state back into a {@link BlockState}.
 *
 * <p>Litematica and vanilla structures store a compound with a {@code Name} and a {@code
 * Properties} map; Sponge schematics store one flat string such as
 * {@code minecraft:oak_stairs[facing=north,half=bottom]}. Both end up here.
 *
 * <p>A property that no longer exists, or a value the block no longer accepts, is skipped rather
 * than failing the state: schematics outlive the versions that wrote them, and losing one property
 * off a stair is far better than losing the whole build.
 */
public final class BlockStateParser {

    private BlockStateParser() {
    }

    /** Parses the {@code {Name, Properties}} form used by Litematica and vanilla structures. */
    public static BlockState fromCompound(NbtCompound compound) {
        String name = compound.getString("Name").orElse(null);
        if (name == null) {
            return Blocks.AIR.getDefaultState();
        }

        BlockState state = defaultStateOf(name);

        Optional<NbtCompound> properties = compound.getCompound("Properties");
        if (properties.isEmpty()) {
            return state;
        }

        NbtCompound values = properties.get();
        for (String key : values.getKeys()) {
            String value = values.getString(key).orElse(null);
            if (value != null) {
                state = withProperty(state, key, value);
            }
        }

        return state;
    }

    /** Parses the flat {@code namespace:id[key=value,...]} form used by Sponge schematics. */
    public static BlockState fromString(String descriptor) {
        int bracket = descriptor.indexOf('[');

        if (bracket < 0) {
            return defaultStateOf(descriptor);
        }

        BlockState state = defaultStateOf(descriptor.substring(0, bracket));

        int close = descriptor.lastIndexOf(']');
        if (close <= bracket) {
            return state;
        }

        String body = descriptor.substring(bracket + 1, close);
        for (String pair : body.split(",")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                state = withProperty(state,
                        pair.substring(0, equals).trim(),
                        pair.substring(equals + 1).trim());
            }
        }

        return state;
    }

    private static BlockState defaultStateOf(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return Blocks.AIR.getDefaultState();
        }

        // A DefaultedRegistry answers with minecraft:air for anything it does not know, so an
        // unrecognised block becomes air and is dropped rather than throwing.
        Block block = Registries.BLOCK.get(identifier);
        return block.getDefaultState();
    }

    /** Applies one property by name, leaving the state untouched if it does not apply. */
    private static BlockState withProperty(BlockState state, String name, String value) {
        Property<?> property = state.getBlock().getStateManager().getProperty(name);
        if (property == null) {
            return state;
        }

        return applyParsed(state, property, value);
    }

    /**
     * Captures the property's value type so {@code with} can be called with matching generics —
     * the wildcard from {@code getProperty} cannot be passed to it directly.
     */
    private static <T extends Comparable<T>> BlockState applyParsed(BlockState state, Property<T> property,
                                                                    String value) {
        return property.parse(value)
                .map(parsed -> state.with(property, parsed))
                .orElse(state);
    }
}
