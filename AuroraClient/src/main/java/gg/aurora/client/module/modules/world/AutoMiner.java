package gg.aurora.client.module.modules.world;

import java.util.ArrayList;
import java.util.List;

import gg.aurora.client.event.events.Render3DEvent;
import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.ColorSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.setting.EnumSetting;
import gg.aurora.client.setting.IntSetting;
import gg.aurora.client.util.RenderUtil;
import gg.aurora.client.util.RotationUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Clears blocks within a radius, nearest first.
 *
 * <p>Breaking is driven the same way holding the attack key is: {@code updateBlockBreakingProgress}
 * once per tick on the same position until it gives way. Switching target mid-break throws away
 * the accumulated progress, so the current target is held until it breaks or leaves reach.
 *
 * <p>Bedrock and liquids are always skipped; {@code Ores only} narrows it further to blocks tagged
 * as ores, which is what makes it useful in a tunnel rather than a strip miner.
 */
public final class AutoMiner extends Module {

    public enum Shape {
        /** Everything inside a sphere of the radius. */
        SPHERE,
        /** Everything inside a cube of the radius. */
        CUBE,
        /** Only the layer the player is standing on. */
        LAYER
    }

    private final DoubleSetting radius =
            this.register(new DoubleSetting("Radius", "How far to mine, in blocks.", 4.0D, 1.0D, 6.0D, 1));
    private final EnumSetting<Shape> shape =
            this.register(new EnumSetting<>("Shape", "Region to clear.", Shape.SPHERE));
    private final BoolSetting oresOnly =
            this.register(new BoolSetting("Ores only", "Only break blocks tagged as ores.", false));
    private final IntSetting delay =
            this.register(new IntSetting("Delay", "Ticks between starting new blocks.", 0, 0, 20));
    private final BoolSetting rotate =
            this.register(new BoolSetting("Rotate", "Look at the block being broken.", true));
    private final BoolSetting highlight =
            this.register(new BoolSetting("Highlight", "Draw the current target.", true));
    private final ColorSetting color =
            this.register(new ColorSetting("Colour", "Highlight colour.", 0xFFFB923C))
                    .visibleWhen(() -> this.highlight.value());

    private BlockPos target;
    private int cooldown;
    private int broken;

    public AutoMiner() {
        super("AutoMiner", "Clears blocks around you.", Category.WORLD);
        this.listen(TickEvent.class, this::onTick);
        this.listen(Render3DEvent.class, this::onRender);
    }

    @Override
    protected void onEnable() {
        this.target = null;
        this.cooldown = 0;
        this.broken = 0;
    }

    @Override
    protected void onDisable() {
        if (mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
        this.target = null;
    }

    private void onTick(TickEvent event) {
        if (!this.inGame() || mc.currentScreen != null) {
            return;
        }

        // Drop a target that has already broken or drifted out of reach.
        if (this.target != null && !this.isMineable(this.target)) {
            this.broken++;
            this.target = null;
            this.cooldown = this.delay.value();
        }

        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }

        if (this.target == null) {
            this.target = this.findNearest();
            if (this.target == null) {
                return;
            }
        }

        if (this.rotate.value()) {
            float[] rotation = RotationUtil.toTarget(this.player(), Vec3d.ofCenter(this.target));
            this.player().setYaw(rotation[0]);
            this.player().setPitch(rotation[1]);
        }

        // Same call the game makes while the attack key is held; progress accumulates server-side
        // as long as the position does not change.
        mc.interactionManager.updateBlockBreakingProgress(this.target, Direction.UP);
        this.player().swingHand(net.minecraft.util.Hand.MAIN_HAND);
    }

    /** The closest mineable block inside the configured region, or {@code null}. */
    private BlockPos findNearest() {
        int reach = (int) Math.ceil(this.radius.value());
        BlockPos origin = this.player().getBlockPos();

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (this.shape.value() == Shape.LAYER && dy != 0) {
                        continue;
                    }

                    BlockPos candidate = origin.add(dx, dy, dz);

                    double distance = this.player().getEyePos().distanceTo(Vec3d.ofCenter(candidate));

                    if (this.shape.value() == Shape.SPHERE && distance > this.radius.value()) {
                        continue;
                    }

                    if (!this.isMineable(candidate)) {
                        continue;
                    }

                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }

        return best;
    }

    private boolean isMineable(BlockPos pos) {
        BlockState state = this.world().getBlockState(pos);

        if (state.isAir() || state.isOf(Blocks.BEDROCK)) {
            return false;
        }

        // Liquids have no breaking progress; targeting one stalls the loop forever.
        if (!state.getFluidState().isEmpty()) {
            return false;
        }

        if (this.oresOnly.value() && !isOre(state)) {
            return false;
        }

        if (this.player().getEyePos().distanceTo(Vec3d.ofCenter(pos)) > this.radius.value()) {
            return false;
        }

        return true;
    }

    /**
     * Whether a block counts as ore.
     *
     * <p>Checked against the per-material tags rather than one blanket tag, because vanilla has no
     * single "all ores" tag — and going through the tags rather than matching on the name picks up
     * the deepslate variants and ancient debris without listing them.
     */
    private static boolean isOre(BlockState state) {
        return state.isIn(BlockTags.COAL_ORES)
                || state.isIn(BlockTags.IRON_ORES)
                || state.isIn(BlockTags.COPPER_ORES)
                || state.isIn(BlockTags.GOLD_ORES)
                || state.isIn(BlockTags.REDSTONE_ORES)
                || state.isIn(BlockTags.LAPIS_ORES)
                || state.isIn(BlockTags.DIAMOND_ORES)
                || state.isIn(BlockTags.EMERALD_ORES)
                || state.isOf(Blocks.ANCIENT_DEBRIS)
                || state.isOf(Blocks.NETHER_QUARTZ_ORE)
                || state.isOf(Blocks.NETHER_GOLD_ORE);
    }

    private void onRender(Render3DEvent event) {
        if (this.highlight.value() && this.target != null) {
            RenderUtil.drawBox(event, new Box(this.target),
                    RenderUtil.withAlpha(this.color.argb(), 50), this.color.argb());
        }
    }

    @Override
    public String hudSuffix() {
        return String.valueOf(this.broken);
    }

    /** Positions currently inside the mining region, for other modules to consult. */
    public List<BlockPos> region() {
        List<BlockPos> result = new ArrayList<>();
        if (this.target != null) {
            result.add(this.target);
        }
        return result;
    }
}
