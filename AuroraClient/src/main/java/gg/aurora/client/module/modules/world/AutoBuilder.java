package gg.aurora.client.module.modules.world;

import java.util.ArrayList;
import java.util.List;

import gg.aurora.client.event.events.Render3DEvent;
import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.ColorSetting;
import gg.aurora.client.setting.EnumSetting;
import gg.aurora.client.setting.IntSetting;
import gg.aurora.client.util.InventoryUtil;
import gg.aurora.client.util.RenderUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Fills a simple shape around the player with whatever block is held.
 *
 * <p>The shape is recomputed from the player's position each tick, so walking drags the pattern
 * along — a floor lays itself as you go, a tunnel bores ahead of you. Positions that already hold
 * a block are skipped, which makes the module idempotent: running it twice over the same ground
 * places nothing the second time.
 */
public final class AutoBuilder extends Module {

    public enum Pattern {
        /** A flat platform under the player. */
        FLOOR,
        /** A wall across the direction the player faces. */
        WALL,
        /** A ring at the player's feet. */
        PERIMETER,
        /** A hollow box around the player, floor and walls. */
        NEST
    }

    private final EnumSetting<Pattern> pattern =
            this.register(new EnumSetting<>("Pattern", "Shape to fill.", Pattern.FLOOR));
    private final IntSetting size =
            this.register(new IntSetting("Size", "Radius of the pattern, in blocks.", 2, 1, 5));
    private final IntSetting height =
            this.register(new IntSetting("Height", "How tall to build.", 2, 1, 5))
                    .visibleWhen(() -> this.pattern.value() == Pattern.WALL
                            || this.pattern.value() == Pattern.NEST);
    private final IntSetting perTick =
            this.register(new IntSetting("Blocks per tick", "How many placements to attempt each tick.", 1, 1, 8));
    private final BoolSetting preview =
            this.register(new BoolSetting("Preview", "Draw where blocks will go.", true));
    private final ColorSetting color =
            this.register(new ColorSetting("Colour", "Preview colour.", 0x6034D399))
                    .visibleWhen(() -> this.preview.value());

    private int placed;

    public AutoBuilder() {
        super("AutoBuilder", "Fills a shape around you with the held block.", Category.WORLD);
        this.listen(TickEvent.class, this::onTick);
        this.listen(Render3DEvent.class, this::onRender);
    }

    @Override
    protected void onEnable() {
        this.placed = 0;
    }

    private void onTick(TickEvent event) {
        if (!this.inGame() || mc.currentScreen != null) {
            return;
        }

        int slot = InventoryUtil.findHotbar(this.player(), stack -> stack.getItem() instanceof BlockItem);
        if (slot < 0) {
            return;
        }

        int attempts = 0;

        for (BlockPos pos : this.targets()) {
            if (attempts >= this.perTick.value()) {
                break;
            }

            if (!this.world().getBlockState(pos).isAir()) {
                continue;
            }

            Direction against = this.supportingSide(pos);
            if (against == null) {
                continue;
            }

            int previous = this.player().getInventory().getSelectedSlot();
            InventoryUtil.select(mc, slot);

            BlockPos neighbour = pos.offset(against);
            mc.interactionManager.interactBlock(this.player(), Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(neighbour), against.getOpposite(), neighbour, false));
            this.player().swingHand(Hand.MAIN_HAND);

            InventoryUtil.select(mc, previous);

            this.placed++;
            attempts++;
        }
    }

    /** Positions the current pattern wants filled, relative to where the player is now. */
    private List<BlockPos> targets() {
        List<BlockPos> result = new ArrayList<>();

        BlockPos feet = this.player().getBlockPos();
        int radius = this.size.value();

        switch (this.pattern.value()) {
            case FLOOR -> {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        result.add(feet.add(dx, -1, dz));
                    }
                }
            }

            case PERIMETER -> {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        // Edge only.
                        if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                            result.add(feet.add(dx, 0, dz));
                        }
                    }
                }
            }

            case WALL -> {
                Direction facing = this.player().getHorizontalFacing();
                // Build across the facing direction, one block ahead of the player.
                Direction across = facing.rotateYClockwise();

                for (int offset = -radius; offset <= radius; offset++) {
                    for (int y = 0; y < this.height.value(); y++) {
                        result.add(feet.offset(facing).offset(across, offset).up(y));
                    }
                }
            }

            case NEST -> {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        result.add(feet.add(dx, -1, dz));

                        if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                            for (int y = 0; y < this.height.value(); y++) {
                                result.add(feet.add(dx, y, dz));
                            }
                        }
                    }
                }
            }
        }

        // Nearest first, so the player is never asked to place something out of reach while a
        // closer gap is still open.
        Vec3d eyes = this.player().getEyePos();
        result.sort(java.util.Comparator.comparingDouble(pos -> eyes.squaredDistanceTo(Vec3d.ofCenter(pos))));

        return result;
    }

    private Direction supportingSide(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!this.world().getBlockState(pos.offset(direction)).isAir()) {
                return direction;
            }
        }
        return null;
    }

    private void onRender(Render3DEvent event) {
        if (!this.preview.value() || !this.inGame()) {
            return;
        }

        for (BlockPos pos : this.targets()) {
            if (this.world().getBlockState(pos).isAir()) {
                RenderUtil.drawBoxFilled(event, new Box(pos), this.color.argb());
            }
        }
    }

    @Override
    public String hudSuffix() {
        return this.pattern.value().name() + " " + this.placed;
    }
}
