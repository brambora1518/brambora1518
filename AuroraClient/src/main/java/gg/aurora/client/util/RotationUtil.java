package gg.aurora.client.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Yaw and pitch arithmetic for modules that aim.
 *
 * <p>Yaw wraps at ±180, so the difference between two headings has to be normalised before it can
 * be compared or scaled — subtracting them directly makes a turn across the wrap point look like
 * an almost full rotation the other way.
 */
public final class RotationUtil {

    private RotationUtil() {
    }

    /** Yaw and pitch, in degrees, that point {@code from} at {@code to}. */
    public static float[] toTarget(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        return new float[] {MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }

    /** Rotation from the player's eyes to a point. */
    public static float[] toTarget(Entity from, Vec3d to) {
        return toTarget(from.getEyePos(), to);
    }

    /**
     * Steps {@code current} towards {@code target} by at most {@code maxStep} degrees.
     *
     * <p>Returns {@code target} outright once it is within one step, so the approach settles
     * instead of oscillating around the goal.
     */
    public static float approach(float current, float target, float maxStep) {
        float difference = MathHelper.wrapDegrees(target - current);

        if (Math.abs(difference) <= maxStep) {
            return target;
        }

        return MathHelper.wrapDegrees(current + Math.copySign(maxStep, difference));
    }

    /** Smallest absolute angle between two yaw values, accounting for the wrap at ±180. */
    public static float difference(float from, float to) {
        return Math.abs(MathHelper.wrapDegrees(to - from));
    }

    /** Unit vector pointing along a yaw/pitch pair. */
    public static Vec3d toVector(float yaw, float pitch) {
        float yawRadians = (float) Math.toRadians(-yaw - 90.0F);
        float pitchRadians = (float) Math.toRadians(-pitch);

        float horizontal = MathHelper.cos(pitchRadians);

        return new Vec3d(
                MathHelper.cos(yawRadians) * horizontal,
                MathHelper.sin(pitchRadians),
                MathHelper.sin(yawRadians) * horizontal
        );
    }

    /**
     * A point inside {@code target}'s hitbox to aim at, chosen by how far up the box to sit.
     *
     * @param heightFraction 0 is the feet, 1 is the top of the head, 0.5 the middle
     */
    public static Vec3d aimPoint(Entity target, double heightFraction) {
        var box = target.getBoundingBox();
        return new Vec3d(
                (box.minX + box.maxX) / 2.0D,
                box.minY + (box.maxY - box.minY) * heightFraction,
                (box.minZ + box.maxZ) / 2.0D
        );
    }
}
