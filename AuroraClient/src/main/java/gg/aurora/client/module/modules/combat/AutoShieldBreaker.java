package gg.aurora.client.module.modules.combat;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.setting.IntSetting;
import gg.aurora.client.util.InventoryUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.Hand;

/**
 * Swaps to an axe and hits a blocking opponent, which disables their shield.
 *
 * <p>Only acts while the target is actually blocking and in range, and returns to the previous
 * hotbar slot afterwards so the swap does not cost the player their weapon on the next swing.
 */
public final class AutoShieldBreaker extends Module {

    private final DoubleSetting range =
            this.register(new DoubleSetting("Range", "Maximum distance, in blocks.", 3.5D, 1.0D, 6.0D, 1));
    private final IntSetting delay =
            this.register(new IntSetting("Delay", "Ticks between attempts.", 10, 0, 60));
    private final BoolSetting swapBack =
            this.register(new BoolSetting("Swap back", "Return to your previous slot after hitting.", true));
    private final BoolSetting ignoreTeammates =
            this.register(new BoolSetting("Ignore teammates", "Never target your own team.", true));

    private int cooldown;

    public AutoShieldBreaker() {
        super("ShieldBreaker", "Disables a blocking opponent's shield with an axe.", Category.COMBAT);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onEnable() {
        this.cooldown = 0;
    }

    private void onTick(TickEvent event) {
        if (!this.inGame() || mc.currentScreen != null) {
            return;
        }

        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }

        int axeSlot = InventoryUtil.findHotbar(this.player(), stack -> stack.getItem() instanceof AxeItem);
        if (axeSlot < 0) {
            return;
        }

        PlayerEntity target = this.blockingTarget();
        if (target == null) {
            return;
        }

        int previous = this.player().getInventory().getSelectedSlot();
        InventoryUtil.select(mc, axeSlot);

        mc.interactionManager.attackEntity(this.player(), target);
        this.player().swingHand(Hand.MAIN_HAND);

        if (this.swapBack.value()) {
            InventoryUtil.select(mc, previous);
        }

        this.cooldown = this.delay.value();
    }

    /** The nearest player in range who is currently blocking, or {@code null}. */
    private PlayerEntity blockingTarget() {
        PlayerEntity best = null;
        double bestDistance = this.range.value();

        for (PlayerEntity other : this.world().getPlayers()) {
            if (other == this.player() || other.isSpectator() || !other.isAlive() || !other.isBlocking()) {
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
}
