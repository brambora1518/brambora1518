package gg.aurora.client.module.modules.combat;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.setting.IntSetting;
import gg.aurora.client.util.InventoryUtil;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Places end crystals next to a target and detonates them.
 *
 * <p>Placement sites are scored by how much the blast should hurt the target relative to the
 * player, and anything that would do more harm than good is skipped — the self-damage check is
 * what separates this from simply spamming crystals into your own feet.
 *
 * <p>Both halves can be run independently: {@code Place} without {@code Break} sets crystals up
 * for something else to detonate, and {@code Break} alone only ever hits crystals already in the
 * world.
 */
public final class AutoCrystal extends Module {

    /** Vanilla end crystal blast power, used to estimate damage. */
    private static final float BLAST_POWER = 6.0F;

    private final DoubleSetting range =
            this.register(new DoubleSetting("Range", "How far to look for targets, in blocks.", 8.0D, 1.0D, 16.0D, 1));
    private final DoubleSetting placeRange =
            this.register(new DoubleSetting("Place range", "How far to place, in blocks.", 4.5D, 1.0D, 8.0D, 1));
    private final BoolSetting place =
            this.register(new BoolSetting("Place", "Place crystals.", true));
    private final BoolSetting breakCrystals =
            this.register(new BoolSetting("Break", "Detonate crystals.", true));
    private final DoubleSetting minDamage =
            this.register(new DoubleSetting("Min damage", "Skip placements below this much damage to the target.",
                    4.0D, 0.0D, 20.0D, 1));
    private final DoubleSetting maxSelfDamage =
            this.register(new DoubleSetting("Max self damage", "Never place where you would take more than this.",
                    6.0D, 0.0D, 20.0D, 1));
    private final IntSetting delay =
            this.register(new IntSetting("Delay", "Ticks between actions.", 2, 0, 20));
    private final BoolSetting ignoreTeammates =
            this.register(new BoolSetting("Ignore teammates", "Never target your own team.", true));

    private int cooldown;
    private PlayerEntity currentTarget;

    public AutoCrystal() {
        super("AutoCrystal", "Places and detonates end crystals on nearby targets.", Category.COMBAT);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onEnable() {
        this.cooldown = 0;
    }

    @Override
    protected void onDisable() {
        this.currentTarget = null;
    }

    private void onTick(TickEvent event) {
        this.currentTarget = null;

        if (!this.inGame() || mc.currentScreen != null) {
            return;
        }

        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }

        PlayerEntity target = this.nearestTarget();
        if (target == null) {
            return;
        }

        this.currentTarget = target;

        // Detonating first clears the way for the next placement, and an existing crystal is
        // already-spent effort that should be cashed in before spending another item.
        if (this.breakCrystals.value() && this.detonateBest(target)) {
            this.cooldown = this.delay.value();
            return;
        }

        if (this.place.value() && this.placeBest(target)) {
            this.cooldown = this.delay.value();
        }
    }

    // ------------------------------------------------------------------
    // Detonating
    // ------------------------------------------------------------------

    /** Attacks the crystal that hurts {@code target} most without over-hurting the player. */
    private boolean detonateBest(PlayerEntity target) {
        EndCrystalEntity best = null;
        double bestDamage = this.minDamage.value();

        for (Entity entity : this.world().getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal) || crystal.isRemoved()) {
                continue;
            }

            if (this.player().distanceTo(crystal) > this.placeRange.value()) {
                continue;
            }

            Vec3d blast = crystal.getPos();

            if (this.estimateDamage(blast, this.player()) > this.maxSelfDamage.value()) {
                continue;
            }

            double damage = this.estimateDamage(blast, target);
            if (damage > bestDamage) {
                bestDamage = damage;
                best = crystal;
            }
        }

        if (best == null) {
            return false;
        }

        mc.interactionManager.attackEntity(this.player(), best);
        this.player().swingHand(Hand.MAIN_HAND);
        return true;
    }

    // ------------------------------------------------------------------
    // Placing
    // ------------------------------------------------------------------

    /** Places a crystal at the best scoring site around {@code target}. */
    private boolean placeBest(PlayerEntity target) {
        int slot = InventoryUtil.findHotbar(this.player(), Items.END_CRYSTAL);
        if (slot < 0) {
            return false;
        }

        BlockPos bestPos = null;
        double bestDamage = this.minDamage.value();

        BlockPos origin = target.getBlockPos();
        int reach = (int) Math.ceil(this.placeRange.value());

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockPos base = origin.add(dx, dy, dz);

                    if (!this.canPlaceOn(base)) {
                        continue;
                    }

                    // The crystal sits on top of the block, which is where the blast originates.
                    Vec3d blast = Vec3d.ofBottomCenter(base.up());

                    if (this.player().getEyePos().distanceTo(blast) > this.placeRange.value()) {
                        continue;
                    }

                    if (this.estimateDamage(blast, this.player()) > this.maxSelfDamage.value()) {
                        continue;
                    }

                    double damage = this.estimateDamage(blast, target);
                    if (damage > bestDamage) {
                        bestDamage = damage;
                        bestPos = base;
                    }
                }
            }
        }

        if (bestPos == null) {
            return false;
        }

        int previous = this.player().getInventory().getSelectedSlot();
        InventoryUtil.select(mc, slot);

        mc.interactionManager.interactBlock(this.player(), Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(bestPos), Direction.UP, bestPos, false));
        this.player().swingHand(Hand.MAIN_HAND);

        InventoryUtil.select(mc, previous);
        return true;
    }

    /** A crystal needs obsidian or bedrock underneath and clear air above it. */
    private boolean canPlaceOn(BlockPos pos) {
        var state = this.world().getBlockState(pos);

        if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) {
            return false;
        }

        if (!this.world().getBlockState(pos.up()).isAir()) {
            return false;
        }

        // Entities block placement, including a crystal that is already there.
        Box space = new Box(pos.up());
        return this.world().getOtherEntities(null, space).isEmpty();
    }

    // ------------------------------------------------------------------
    // Damage estimation
    // ------------------------------------------------------------------

    /**
     * Rough blast damage from an explosion at {@code blast} to {@code victim}.
     *
     * <p>Follows vanilla's shape — damage falls off with distance and vanishes past the blast
     * radius — without modelling block occlusion or armour. That makes it an over-estimate behind
     * cover, which is the safe direction for the self-damage check: it declines placements that
     * would in fact have been survivable rather than taking ones that would not.
     */
    private double estimateDamage(Vec3d blast, Entity victim) {
        double radius = BLAST_POWER * 2.0D;
        double distance = victim.getPos().distanceTo(blast);

        if (distance > radius) {
            return 0.0D;
        }

        double falloff = (1.0D - distance / radius);
        return (falloff * falloff * 7.0D + falloff) * BLAST_POWER;
    }

    private PlayerEntity nearestTarget() {
        PlayerEntity best = null;
        double bestDistance = this.range.value();

        for (PlayerEntity other : this.world().getPlayers()) {
            if (other == this.player() || other.isSpectator() || !other.isAlive()) {
                continue;
            }

            if (this.ignoreTeammates.value() && this.player().isTeammate(other)) {
                continue;
            }

            double distance = this.player().distanceTo(other);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }

        return best;
    }

    @Override
    public String hudSuffix() {
        return this.currentTarget == null ? null : this.currentTarget.getName().getString();
    }
}
