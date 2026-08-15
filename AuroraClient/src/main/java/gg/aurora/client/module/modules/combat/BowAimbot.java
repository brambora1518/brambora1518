package gg.aurora.client.module.modules.combat;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.util.RotationUtil;
import gg.aurora.client.util.TrajectoryUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/**
 * Aims a drawn bow at the nearest target, compensating for drop.
 *
 * <p>Pitch comes from the ballistic solution for the current draw strength, so the arc is correct
 * for a half-drawn bow as well as a full one. Targets are led by the projected flight time, which
 * is what makes a moving target hittable at range.
 *
 * <p>Only aims while the bow is actually being drawn — it does not draw, hold or release for you.
 */
public final class BowAimbot extends Module {

    /** Ticks of draw needed for a bow to reach full power. */
    private static final int FULL_DRAW_TICKS = 20;

    /** Launch speed of a fully drawn bow, in blocks per tick. */
    private static final double MAX_BOW_POWER = 3.0D;

    /** Crossbows fire at a fixed speed regardless of how long they were held. */
    private static final double CROSSBOW_POWER = 3.15D;

    private final DoubleSetting range =
            this.register(new DoubleSetting("Range", "Maximum distance to aim at, in blocks.", 48.0D, 4.0D, 128.0D, 0));
    private final DoubleSetting aimHeight =
            this.register(new DoubleSetting("Aim height", "Where on the hitbox to aim. 0 feet, 1 head.",
                    0.6D, 0.0D, 1.0D, 2));
    private final BoolSetting predict =
            this.register(new BoolSetting("Predict", "Lead moving targets by the flight time.", true));
    private final BoolSetting playersOnly =
            this.register(new BoolSetting("Players only", "Ignore mobs.", false));
    private final BoolSetting ignoreTeammates =
            this.register(new BoolSetting("Ignore teammates", "Never aim at your own team.", true));

    private Entity current;

    public BowAimbot() {
        super("BowAimbot", "Aims a drawn bow, correcting for arrow drop.", Category.COMBAT);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        this.current = null;
    }

    private void onTick(TickEvent event) {
        this.current = null;

        if (!this.inGame() || mc.currentScreen != null) {
            return;
        }

        double power = this.drawPower();
        if (power <= 0.0D) {
            return;
        }

        Entity target = this.nearestTarget();
        if (target == null) {
            return;
        }

        Vec3d aimPoint = this.solveAimPoint(target, power);
        if (aimPoint == null) {
            return;
        }

        float[] rotation = RotationUtil.toTarget(this.player(), aimPoint);
        this.player().setYaw(rotation[0]);
        this.player().setPitch(rotation[1]);
        this.current = target;
    }

    /**
     * Launch speed for the item currently being drawn, or {@code 0} if nothing is.
     *
     * <p>Bow power ramps with draw time on the same curve vanilla uses, so aiming early gives the
     * arc that matches an early release.
     */
    private double drawPower() {
        if (!this.player().isUsingItem()) {
            return 0.0D;
        }

        ItemStack active = this.player().getActiveItem();

        if (active.getItem() instanceof CrossbowItem) {
            return CROSSBOW_POWER;
        }

        if (!(active.getItem() instanceof BowItem)) {
            return 0.0D;
        }

        int drawn = this.player().getItemUseTime();
        float progress = Math.min(1.0F, drawn / (float) FULL_DRAW_TICKS);

        // Vanilla's charge curve, not a straight ramp.
        float charge = (progress * progress + progress * 2.0F) / 3.0F;
        return charge * MAX_BOW_POWER;
    }

    /**
     * The point to aim at so the arrow lands on {@code target}, or {@code null} when the target is
     * out of ballistic reach.
     */
    private Vec3d solveAimPoint(Entity target, double power) {
        Vec3d eyes = this.player().getEyePos();
        Vec3d aim = RotationUtil.aimPoint(target, this.aimHeight.value());

        if (this.predict.value()) {
            // Lead by roughly the flight time, then re-derive the arc for the led point. One pass
            // is enough: the correction is small relative to the target's own speed.
            int flightTicks = this.estimateFlightTicks(eyes, aim, power);
            Vec3d led = TrajectoryUtil.predictPosition(target, flightTicks);
            aim = new Vec3d(led.x, aim.y + (led.y - target.getY()), led.z);
        }

        double dx = aim.x - eyes.x;
        double dz = aim.z - eyes.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        double rise = aim.y - eyes.y;

        Float pitch = TrajectoryUtil.solvePitch(flat, rise, power, TrajectoryUtil.ARROW_GRAVITY);
        if (pitch == null) {
            return null;
        }

        // Re-express the solved pitch as a point, so the caller can aim at it the same way it aims
        // at anything else.
        double horizontal = Math.cos(Math.toRadians(pitch));
        double vertical = -Math.sin(Math.toRadians(pitch));

        return new Vec3d(
                eyes.x + dx,
                eyes.y + (flat / Math.max(1.0E-4D, horizontal)) * vertical,
                eyes.z + dz
        );
    }

    private int estimateFlightTicks(Vec3d from, Vec3d to, double power) {
        double distance = from.distanceTo(to);
        return (int) Math.min(60.0D, Math.max(1.0D, distance / Math.max(0.1D, power)));
    }

    private Entity nearestTarget() {
        double maxDistance = this.range.value();
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : this.world().getEntities()) {
            if (!this.isValid(entity)) {
                continue;
            }

            double distance = this.player().distanceTo(entity);
            if (distance <= maxDistance && distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }

        return best;
    }

    private boolean isValid(Entity entity) {
        if (entity == this.player() || entity.isRemoved()) {
            return false;
        }

        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }

        if (this.playersOnly.value() && !(entity instanceof PlayerEntity)) {
            return false;
        }

        if (entity instanceof PlayerEntity other) {
            if (other.isSpectator()) {
                return false;
            }
            if (this.ignoreTeammates.value() && this.player().isTeammate(other)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String hudSuffix() {
        return this.current == null ? null : this.current.getName().getString();
    }
}
