package gg.aurora.client.module.modules.combat;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.setting.EnumSetting;
import gg.aurora.client.util.RotationUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Nudges the crosshair towards the nearest target.
 *
 * <p>It steers rather than snaps: each tick the view turns by at most {@code Speed} degrees, and
 * only once a target is already inside the {@code FOV} cone. It also stops helping once the
 * crosshair is within {@code Deadzone} of the target, so the last fraction of a degree is left to
 * the player and the view does not jitter around the aim point.
 */
public final class AimAssist extends Module {

    public enum Priority {
        DISTANCE,
        HEALTH,
        ANGLE
    }

    private final DoubleSetting range =
            this.register(new DoubleSetting("Range", "Maximum distance to assist, in blocks.", 4.5D, 1.0D, 8.0D, 1));
    private final DoubleSetting fov =
            this.register(new DoubleSetting("FOV", "Only assist within this cone, in degrees.", 60.0D, 5.0D, 180.0D, 0));
    private final DoubleSetting speed =
            this.register(new DoubleSetting("Speed", "Maximum turn per tick, in degrees.", 8.0D, 0.5D, 45.0D, 1));
    private final DoubleSetting deadzone =
            this.register(new DoubleSetting("Deadzone", "Stop assisting inside this angle, in degrees.",
                    1.5D, 0.0D, 10.0D, 1));
    private final DoubleSetting aimHeight =
            this.register(new DoubleSetting("Aim height", "Where on the hitbox to aim. 0 feet, 1 head.",
                    0.75D, 0.0D, 1.0D, 2));
    private final EnumSetting<Priority> priority =
            this.register(new EnumSetting<>("Priority", "How to choose between several targets.", Priority.ANGLE));
    private final BoolSetting playersOnly =
            this.register(new BoolSetting("Players only", "Ignore mobs.", false));
    private final BoolSetting ignoreTeammates =
            this.register(new BoolSetting("Ignore teammates", "Never assist onto your own team.", true));
    private final BoolSetting requireAttack =
            this.register(new BoolSetting("While attacking", "Only assist while the attack key is held.", true));

    private Entity current;

    public AimAssist() {
        super("AimAssist", "Steers your aim towards a nearby target.", Category.COMBAT);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        this.current = null;
    }

    private void onTick(TickEvent event) {
        if (!this.inGame() || mc.currentScreen != null) {
            this.current = null;
            return;
        }

        if (this.requireAttack.value() && !mc.options.attackKey.isPressed()) {
            this.current = null;
            return;
        }

        Entity target = this.pickTarget();
        this.current = target;

        if (target == null) {
            return;
        }

        Vec3d aimPoint = RotationUtil.aimPoint(target, this.aimHeight.value());
        float[] wanted = RotationUtil.toTarget(this.player(), aimPoint);

        float yawGap = RotationUtil.difference(this.player().getYaw(), wanted[0]);
        float pitchGap = Math.abs(wanted[1] - this.player().getPitch());

        // Already on target — leave the view alone rather than fighting the player's own aim.
        if (yawGap <= this.deadzone.value() && pitchGap <= this.deadzone.value()) {
            return;
        }

        float step = (float) this.speed.value();
        this.player().setYaw(RotationUtil.approach(this.player().getYaw(), wanted[0], step));
        this.player().setPitch(RotationUtil.approach(this.player().getPitch(), wanted[1], step));
    }

    /** The best target inside range and inside the cone, or {@code null}. */
    private Entity pickTarget() {
        double maxDistance = this.range.value();
        Entity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : this.world().getEntities()) {
            if (!this.isValid(entity)) {
                continue;
            }

            double distance = this.player().distanceTo(entity);
            if (distance > maxDistance) {
                continue;
            }

            Vec3d aimPoint = RotationUtil.aimPoint(entity, this.aimHeight.value());
            float[] wanted = RotationUtil.toTarget(this.player(), aimPoint);
            double angle = RotationUtil.difference(this.player().getYaw(), wanted[0]);

            if (angle > this.fov.value() / 2.0D) {
                continue;
            }

            double score = switch (this.priority.value()) {
                case DISTANCE -> distance;
                case HEALTH -> entity instanceof LivingEntity living ? living.getHealth() : Double.MAX_VALUE;
                case ANGLE -> angle;
            };

            if (score < bestScore) {
                bestScore = score;
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
