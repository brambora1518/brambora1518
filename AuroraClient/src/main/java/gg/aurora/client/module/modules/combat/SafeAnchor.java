package gg.aurora.client.module.modules.combat;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.setting.IntSetting;
import gg.aurora.client.util.InventoryUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Places, charges and triggers a respawn anchor next to a target.
 *
 * <p>An anchor detonates when used outside the Overworld, which is the whole mechanic here. The
 * module runs the three steps one tick at a time — place, charge, trigger — rather than in a
 * single burst, because each step depends on the block state the previous one produced and the
 * server has to have applied it first.
 *
 * <p>Refuses to act in the Overworld, where an anchor sets spawn instead of exploding, and skips
 * any site whose blast would exceed the self-damage limit.
 */
public final class SafeAnchor extends Module {

    /** Respawn anchor blast power. */
    private static final float BLAST_POWER = 5.0F;

    private enum Stage {
        PLACE,
        CHARGE,
        TRIGGER
    }

    private final DoubleSetting range =
            this.register(new DoubleSetting("Range", "How far to look for targets, in blocks.", 6.0D, 1.0D, 12.0D, 1));
    private final DoubleSetting placeRange =
            this.register(new DoubleSetting("Place range", "How far to place, in blocks.", 4.5D, 1.0D, 6.0D, 1));
    private final DoubleSetting maxSelfDamage =
            this.register(new DoubleSetting("Max self damage", "Never act where you would take more than this.",
                    8.0D, 0.0D, 20.0D, 1));
    private final IntSetting delay =
            this.register(new IntSetting("Delay", "Ticks between steps.", 2, 1, 20));
    private final BoolSetting ignoreTeammates =
            this.register(new BoolSetting("Ignore teammates", "Never target your own team.", true));

    private Stage stage = Stage.PLACE;
    private BlockPos site;
    private int cooldown;

    public SafeAnchor() {
        super("SafeAnchor", "Places, charges and triggers a respawn anchor on a target.", Category.COMBAT);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onEnable() {
        this.reset();
    }

    @Override
    protected void onDisable() {
        this.reset();
    }

    private void reset() {
        this.stage = Stage.PLACE;
        this.site = null;
        this.cooldown = 0;
    }

    private void onTick(TickEvent event) {
        if (!this.inGame() || mc.currentScreen != null) {
            return;
        }

        // In the Overworld an anchor sets your spawn point rather than exploding.
        if (this.world().getRegistryKey() == World.OVERWORLD) {
            return;
        }

        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }

        switch (this.stage) {
            case PLACE -> this.doPlace();
            case CHARGE -> this.doCharge();
            case TRIGGER -> this.doTrigger();
        }
    }

    private void doPlace() {
        PlayerEntity target = this.nearestTarget();
        if (target == null) {
            return;
        }

        BlockPos found = this.findSite(target);
        if (found == null) {
            return;
        }

        int slot = InventoryUtil.findHotbar(this.player(), Items.RESPAWN_ANCHOR);
        if (slot < 0) {
            return;
        }

        this.useOn(found, slot);
        this.site = found;
        this.stage = Stage.CHARGE;
        this.cooldown = this.delay.value();
    }

    private void doCharge() {
        if (!this.siteStillValid()) {
            this.reset();
            return;
        }

        int slot = InventoryUtil.findHotbar(this.player(), Items.GLOWSTONE);
        if (slot < 0) {
            this.reset();
            return;
        }

        this.useOn(this.site, slot);
        this.stage = Stage.TRIGGER;
        this.cooldown = this.delay.value();
    }

    private void doTrigger() {
        if (!this.siteStillValid()) {
            this.reset();
            return;
        }

        // Charging must have landed, or using it does nothing and the sequence stalls here.
        var state = this.world().getBlockState(this.site);
        if (state.get(RespawnAnchorBlock.CHARGES) < 1) {
            this.stage = Stage.CHARGE;
            this.cooldown = this.delay.value();
            return;
        }

        // Any held item works to trigger it; use whatever is already in hand.
        this.useOn(this.site, this.player().getInventory().getSelectedSlot());
        this.reset();
        this.cooldown = this.delay.value();
    }

    private boolean siteStillValid() {
        return this.site != null && this.world().getBlockState(this.site).isOf(Blocks.RESPAWN_ANCHOR);
    }

    private void useOn(BlockPos pos, int slot) {
        int previous = this.player().getInventory().getSelectedSlot();
        InventoryUtil.select(mc, slot);

        mc.interactionManager.interactBlock(this.player(), Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
        this.player().swingHand(Hand.MAIN_HAND);

        InventoryUtil.select(mc, previous);
    }

    /** An air block near the target, in reach, whose blast the player would survive. */
    private BlockPos findSite(PlayerEntity target) {
        BlockPos origin = target.getBlockPos();
        int reach = (int) Math.ceil(this.placeRange.value());

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockPos candidate = origin.add(dx, dy, dz);

                    if (!this.world().getBlockState(candidate).isAir()) {
                        continue;
                    }

                    // Needs something to place against.
                    if (this.world().getBlockState(candidate.down()).isAir()) {
                        continue;
                    }

                    if (!this.world().getOtherEntities(null, new Box(candidate)).isEmpty()) {
                        continue;
                    }

                    Vec3d blast = Vec3d.ofCenter(candidate);

                    if (this.player().getEyePos().distanceTo(blast) > this.placeRange.value()) {
                        continue;
                    }

                    if (this.estimateDamage(blast, this.player()) > this.maxSelfDamage.value()) {
                        continue;
                    }

                    double distance = target.getPos().distanceTo(blast);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }

        return best;
    }

    /** Same falloff shape as the crystal estimate; see {@code AutoCrystal}. */
    private double estimateDamage(Vec3d blast, PlayerEntity victim) {
        double radius = BLAST_POWER * 2.0D;
        double distance = victim.getPos().distanceTo(blast);

        if (distance > radius) {
            return 0.0D;
        }

        double falloff = 1.0D - distance / radius;
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
        return this.site == null ? null : this.stage.name();
    }
}
