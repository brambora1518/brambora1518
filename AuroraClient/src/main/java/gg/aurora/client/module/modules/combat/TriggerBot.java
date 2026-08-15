package gg.aurora.client.module.modules.combat;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.IntSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Attacks whatever the crosshair is already on.
 *
 * <p>It does not aim — it only decides when to click, using the entity the vanilla ray trace has
 * already picked. By default it waits for the attack cooldown to recharge, because swinging early
 * costs most of the damage.
 */
public final class TriggerBot extends Module {

    private final IntSetting delay =
            this.register(new IntSetting("Delay", "Extra ticks between attacks.", 0, 0, 20));
    private final BoolSetting waitForCooldown =
            this.register(new BoolSetting("Wait for cooldown", "Only hit at full attack charge.", true));
    private final BoolSetting playersOnly =
            this.register(new BoolSetting("Players only", "Ignore mobs and other entities.", false));
    private final BoolSetting ignoreTeammates =
            this.register(new BoolSetting("Ignore teammates", "Never hit players on your team.", true));

    private int cooldown;

    public TriggerBot() {
        super("TriggerBot", "Attacks the entity under your crosshair.", Category.COMBAT);
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

        if (this.waitForCooldown.value() && this.player().getAttackCooldownProgress(0.0F) < 1.0F) {
            return;
        }

        Entity target = this.target();
        if (target == null) {
            return;
        }

        mc.interactionManager.attackEntity(this.player(), target);
        this.player().swingHand(Hand.MAIN_HAND);
        this.cooldown = this.delay.value();
    }

    /** The entity the crosshair is on, or {@code null} if it is not a valid target. */
    private Entity target() {
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof EntityHitResult entityHit)) {
            return null;
        }

        Entity entity = entityHit.getEntity();

        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return null;
        }

        if (this.playersOnly.value() && !(entity instanceof PlayerEntity)) {
            return null;
        }

        if (entity instanceof PlayerEntity other) {
            if (other == this.player() || other.isSpectator()) {
                return null;
            }
            if (this.ignoreTeammates.value() && this.player().isTeammate(other)) {
                return null;
            }
        }

        return entity;
    }
}
