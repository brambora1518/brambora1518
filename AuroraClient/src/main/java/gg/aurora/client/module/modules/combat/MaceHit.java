package gg.aurora.client.module.modules.combat;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.util.InventoryUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Lands a mace strike at the bottom of a fall, when the bonus damage is highest.
 *
 * <p>A mace scales its damage with how far the attacker has fallen, so the whole trick is timing
 * the swing for the last moment before landing. This waits until the player is both falling and
 * within {@code Trigger height} of the ground, then hits the nearest target below.
 */
public final class MaceHit extends Module {

    private final DoubleSetting minFall =
            this.register(new DoubleSetting("Min fall", "Only strike after falling at least this far.",
                    3.0D, 0.5D, 30.0D, 1));
    private final DoubleSetting triggerHeight =
            this.register(new DoubleSetting("Trigger height", "Strike within this distance of the target.",
                    3.0D, 0.5D, 8.0D, 1));
    private final DoubleSetting range =
            this.register(new DoubleSetting("Range", "Maximum distance to a target, in blocks.", 4.0D, 1.0D, 8.0D, 1));
    private final BoolSetting autoSwap =
            this.register(new BoolSetting("Auto swap", "Swap to the mace for the strike.", true));
    private final BoolSetting ignoreTeammates =
            this.register(new BoolSetting("Ignore teammates", "Never target your own team.", true));

    public MaceHit() {
        super("MaceHit", "Times a mace strike for the end of a fall.", Category.COMBAT);
        this.listen(TickEvent.class, this::onTick);
    }

    private void onTick(TickEvent event) {
        if (!this.inGame() || mc.currentScreen != null) {
            return;
        }

        // The bonus comes from fall distance, so there is nothing to time while grounded.
        if (this.player().isOnGround() || this.player().fallDistance < this.minFall.value()) {
            return;
        }

        // Only swing on the way down; on the way up there is no fall bonus yet.
        if (this.player().getVelocity().y >= 0.0D) {
            return;
        }

        int maceSlot = InventoryUtil.findHotbar(this.player(), Items.MACE);
        if (this.autoSwap.value() && maceSlot < 0) {
            return;
        }

        Entity target = this.targetBelow();
        if (target == null) {
            return;
        }

        int previous = this.player().getInventory().getSelectedSlot();
        if (this.autoSwap.value()) {
            InventoryUtil.select(mc, maceSlot);
        }

        mc.interactionManager.attackEntity(this.player(), target);
        this.player().swingHand(Hand.MAIN_HAND);

        if (this.autoSwap.value()) {
            InventoryUtil.select(mc, previous);
        }
    }

    /** The nearest living target that is below the player and close enough to strike. */
    private Entity targetBelow() {
        Entity best = null;
        double bestDistance = this.range.value();

        for (Entity entity : this.world().getEntities()) {
            if (entity == this.player() || entity.isRemoved()) {
                continue;
            }

            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }

            if (entity instanceof PlayerEntity other) {
                if (other.isSpectator()) {
                    continue;
                }
                if (this.ignoreTeammates.value() && this.player().isTeammate(other)) {
                    continue;
                }
            }

            // Must be underneath, and close enough vertically that the landing is imminent.
            double drop = this.player().getY() - entity.getY();
            if (drop <= 0.0D || drop > this.triggerHeight.value()) {
                continue;
            }

            double distance = this.player().distanceTo(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }

        return best;
    }

    @Override
    public String hudSuffix() {
        return String.format("%.1f", this.player() == null ? 0.0F : this.player().fallDistance);
    }
}
