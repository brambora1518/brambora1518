package gg.aurora.client.schematic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

/**
 * A loaded structure, in memory.
 *
 * <p>Positions are relative to the structure's own origin; the builder adds the world anchor when
 * it places. Air is not stored — the formats disagree about whether it is even present, and a
 * builder has nothing to do with it either way, so dropping it up front keeps the block count
 * honest and the map small.
 */
public final class Schematic {

    private final String name;
    private final Vec3i size;
    private final Map<BlockPos, BlockState> blocks;

    public Schematic(String name, Vec3i size, Map<BlockPos, BlockState> blocks) {
        this.name = name;
        this.size = size;
        this.blocks = blocks;
    }

    public String name() {
        return this.name;
    }

    public Vec3i size() {
        return this.size;
    }

    /** Non-air blocks, keyed by position relative to the structure origin. */
    public Map<BlockPos, BlockState> blocks() {
        return Collections.unmodifiableMap(this.blocks);
    }

    public int blockCount() {
        return this.blocks.size();
    }

    /**
     * Returns the blocks ordered so that nothing is placed before what it rests on.
     *
     * <p>Sorted bottom layer first, because a block placed in mid-air has nothing to click
     * against — the server rejects the placement. Within a layer the order is by distance from the
     * structure's horizontal centre, which keeps the builder working outward from one place rather
     * than jumping across the build and running out of reach every other block.
     */
    public java.util.List<Map.Entry<BlockPos, BlockState>> placementOrder() {
        double centerX = this.size.getX() / 2.0D;
        double centerZ = this.size.getZ() / 2.0D;

        return this.blocks.entrySet().stream()
                .sorted(java.util.Comparator
                        .comparingInt((Map.Entry<BlockPos, BlockState> entry) -> entry.getKey().getY())
                        .thenComparingDouble(entry -> {
                            double dx = entry.getKey().getX() - centerX;
                            double dz = entry.getKey().getZ() - centerZ;
                            return dx * dx + dz * dz;
                        }))
                .toList();
    }

    /** Builder used by the format parsers. */
    public static final class Builder {

        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

        public void set(int x, int y, int z, BlockState state) {
            if (state == null || state.isAir()) {
                return;
            }
            this.blocks.put(new BlockPos(x, y, z), state);
        }

        public Schematic build(String name, Vec3i size) {
            return new Schematic(name, size, this.blocks);
        }
    }
}
