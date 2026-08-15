package gg.aurora.client.module.modules.combat;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.setting.IntSetting;
import gg.aurora.client.util.InventoryUtil;
import gg.aurora.client.util.RotationUtil;
import gg.aurora.client.util.TrajectoryUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Throws a trident at the nearest target, correcting for drop.
 *
 * <p>A trident has to be charged before it can be thrown, so this holds use for the wind-up and
 * releases once it is ready — releasing early just lowers the trident without throwing it.
 * Riptide tridents are skipped: they carry the thrower instead of leaving the hand.
 */
public final class AutoSpear extends Module {

    /** Ticks of charge a trident needs before it can be thrown. */
    private static final int CHARGE_TICKS = 10;

    /** Launch speed of a thrown trident, in blocks per tick. */
    private static final double TRIDENT_POWER = 2.5D;

    private final DoubleSetting range =
            this.register(new DoubleSetting("Range", "Maximum distance to throw, in blocks.", 24.0D, 4.0D, 64.0D, 0));
    private final DoubleSetting aimHeight =
            this.register(new DoubleSetting("Aim height", "Where on the hitbox to aim. 0 feet, 1 head.",
                    0.6D, 0.0D, 1.0D, 2));
    private final IntSetting delay =
            this.register(new IntSetting("Delay", "Ticks between throws.", 20, 0, 100));
    private final BoolSetting predict =
            this.register(new BoolSetting("Predict", "Lead moving targets by the flight time.", true));
    private final BoolSetting playersOnly =
            this.register(new BoolSetting("Players only", "Ignore mobs.", true));
    private final BoolSetting ignoreTeammates =
            this.register(new BoolSetting("Ignore teammates", "Never target your own team.", true));

    private int cooldown;
    private boolean charging;

    public AutoSpear() {
        super("AutoSpear", "Throws a trident at nearby targets.", Category.COMBAT);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onEnable() {
        this.cooldown = 0;
        this.charging = false;
    }

    @Override
    protected void onDisable() {
        if (this.charging && mc.interactionManager != null) {
            mc.interactionManager.stopUsingItem(this.player());
        }
        this.charging = false;
    }

    private void onTick(TickEvent event) {
        if (!this.inGame() || mc.currentScreen != null) {
            return;
        }

        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }

        int slot = InventoryUtil.findHotbar(this.player(), Items.TRIDENT);
        if (slot < 0) {
            this.charging = false;
            return;
        }

        // Riptide moves the thrower rather than the trident; it is not a ranged attack.
        if (this.hasRiptide(slot)) {
            return;
        }

        Entity target = this.nearestTarget();
        if (target == null) {
            if (this.charging) {
                mc.interactionManager.stopUsingItem(this.player());
                this.charging = false;
            }
            return;
        }

        InventoryUtil.select(mc, slot);

        Vec3d aim = this.solveAim(target);
        if (aim != null) {
            float[] rotation = RotationUtil.toTarget(this.player(), aim);
            this.player().setYaw(rotation[0]);
            this.player().setPitch(rotation[1]);
        }

        if (!this.charging) {
            mc.interactionManager.interactItem(this.player(), Hand.MAIN_HAND);
            this.charging = true;
            return;
        }

        // Release only once the wind-up is complete.
        if (this.player().getItemUseTime() >= CHARGE_TICKS) {
            mc.interactionManager.stopUsingItem(this.player());
            this.charging = false;
            this.cooldown = this.delay.value();
        }
    }

    private boolean hasRiptide(int slot) {
        var stack = this.player().getInventory().getStack(slot);
        return stack.getEnchantments().getEnchantments().stream()
                .anyMatch(entry -> entry.getIdAsString().endsWith("riptide"));
    }

    /** The point to aim at, corrected for drop and optionally led, or {@code null} if unreachable. */
    private Vec3d solveAim(Entity target) {
        Vec3d eyes = this.player().getEyePos();
        Vec3d aim = RotationUtil.aimPoint(target, this.aimHeight.value());

        if (this.predict.value()) {
            int flightTicks = (int) Math.min(40.0D, Math.max(1.0D, eyes.distanceTo(aim) / TRIDENT_POWER));
            Vec3d led = TrajectoryUtil.predictPosition(target, flightTicks);
            aim = new Vec3d(led.x, aim.y + (led.y - target.getY()), led.z);
        }

        double dx = aim.x - eyes.x;
        double dz = aim.z - eyes.z;
        double flat = Math.sqrt(dx * dx + dz * dz);

        Float pitch = TrajectoryUtil.solvePitch(
                flat, aim.y - eyes.y, TRIDENT_POWER, TrajectoryUtil.ARROW_GRAVITY);
        if (pitch == null) {
            return null;
        }

        double horizontal = Math.cos(Math.toRadians(pitch));
        double vertical = -Math.sin(Math.toRadians(pitch));

        return new Vec3d(
                eyes.x + dx,
                eyes.y + (flat / Math.max(1.0E-4D, horizontal)) * vertical,
                eyes.z + dz
        );
    }

    private Entity nearestTarget() {
        Entity best = null;
        double bestDistance = this.range.value();

        for (Entity entity : this.world().getEntities()) {
            if (entity == this.player() || entity.isRemoved()) {
                continue;
            }

            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }

            if (this.playersOnly.value() && !(entity instanceof PlayerEntity)) {
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
        return this.charging ? "charging" : null;
    }
}
