package gg.aurora.client.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Ballistics for the thrown and fired projectiles the client needs to predict.
 *
 * <p>Minecraft integrates projectiles one tick at a time — position advances by velocity, velocity
 * is scaled by drag and then loses a fixed amount to gravity — so the honest way to predict a
 * flight path is to run the same loop rather than solve a parabola. The closed form would be wrong
 * because drag is applied per tick, not continuously.
 */
public final class TrajectoryUtil {

    /** Per-tick multiplier applied to velocity in air. */
    public static final double AIR_DRAG = 0.99D;

    /** Per-tick multiplier applied to velocity in water. */
    public static final double WATER_DRAG = 0.6D;

    /** Velocity lost to gravity each tick, in blocks, for an arrow. */
    public static final double ARROW_GRAVITY = 0.05D;

    /** Velocity lost to gravity each tick for a thrown item such as a pearl or a snowball. */
    public static final double THROWN_GRAVITY = 0.03D;

    private TrajectoryUtil() {
    }

    /**
     * Walks a projectile forward and returns the points it passes through.
     *
     * @param origin   launch position, normally the thrower's eyes
     * @param velocity initial velocity, in blocks per tick
     * @param gravity  velocity lost per tick
     * @param maxTicks how far ahead to simulate before giving up
     */
    public static List<Vec3d> simulate(Vec3d origin, Vec3d velocity, double gravity, int maxTicks) {
        List<Vec3d> path = new ArrayList<>(maxTicks + 1);

        Vec3d position = origin;
        Vec3d current = velocity;

        path.add(position);

        for (int tick = 0; tick < maxTicks; tick++) {
            position = position.add(current);
            current = current.multiply(AIR_DRAG).subtract(0.0D, gravity, 0.0D);
            path.add(position);
        }

        return path;
    }

    /**
     * Initial velocity of a projectile launched along a yaw/pitch pair.
     *
     * @param power launch speed, in blocks per tick; a fully drawn bow is 3.0
     */
    public static Vec3d launchVelocity(float yaw, float pitch, double power) {
        return RotationUtil.toVector(yaw, pitch).multiply(power);
    }

    /**
     * Pitch that lands a projectile of the given launch speed on {@code target}.
     *
     * <p>Solves the drag-free ballistic equation, which lands close enough to then be walked in by
     * the caller, and returns {@code null} when the target is out of reach at that speed — beyond
     * the maximum range there is no angle that reaches it, and returning a "best effort" pitch
     * there would aim at nothing.
     *
     * @param flat     horizontal distance to the target
     * @param rise     how far above the launch point the target sits
     * @param power    launch speed, in blocks per tick
     * @param gravity  velocity lost per tick
     */
    public static Float solvePitch(double flat, double rise, double power, double gravity) {
        double speedSquared = power * power;
        double discriminant = speedSquared * speedSquared
                - gravity * (gravity * flat * flat + 2.0D * rise * speedSquared);

        if (discriminant < 0.0D) {
            return null;
        }

        // The lower of the two arcs: it arrives sooner and is far easier to lead a moving target
        // with than the lofted one.
        double angle = Math.atan2(speedSquared - Math.sqrt(discriminant), gravity * flat);
        return (float) -Math.toDegrees(angle);
    }

    /**
     * Where an entity is expected to be in {@code ticks}, extrapolating its current velocity.
     *
     * <p>A straight-line guess: it is right for a player who keeps walking and wrong for one who
     * turns, which is why callers lead by the flight time and no further.
     */
    public static Vec3d predictPosition(Entity entity, int ticks) {
        Vec3d velocity = entity.getPos().subtract(entity.lastX, entity.lastY, entity.lastZ);
        return entity.getPos().add(velocity.multiply(ticks));
    }
}
