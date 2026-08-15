package gg.aurora.client.util;

import gg.aurora.client.event.events.Render3DEvent;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * World-space drawing helpers.
 *
 * <p>Every method takes world coordinates and does the camera-relative shift itself. The matrix
 * stack handed to a {@link Render3DEvent} has its origin at the camera, so drawing raw world
 * coordinates into it puts the geometry thousands of blocks away — that translation is the one
 * thing every caller would otherwise have to remember.
 */
public final class RenderUtil {

    private RenderUtil() {
    }

    /** Draws the twelve edges of {@code box}, in world coordinates. */
    public static void drawBoxOutline(Render3DEvent event, Box box, int argb) {
        MatrixStack matrices = event.matrices();
        Vec3d camera = event.cameraPos();

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer consumer = event.consumers().getBuffer(RenderLayer.getLines());
        VertexRendering.drawBox(
                matrices, consumer,
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                red(argb), green(argb), blue(argb), alpha(argb)
        );

        matrices.pop();
    }

    /** Draws {@code box} as solid faces, in world coordinates. */
    public static void drawBoxFilled(Render3DEvent event, Box box, int argb) {
        MatrixStack matrices = event.matrices();
        Vec3d camera = event.cameraPos();

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer consumer = event.consumers().getBuffer(RenderLayer.getDebugFilledBox());
        VertexRendering.drawFilledBox(
                matrices, consumer,
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                red(argb), green(argb), blue(argb), alpha(argb)
        );

        matrices.pop();
    }

    /** Draws a filled box with an outline of the same hue, the usual ESB/ESP treatment. */
    public static void drawBox(Render3DEvent event, Box box, int fillArgb, int outlineArgb) {
        drawBoxFilled(event, box, fillArgb);
        drawBoxOutline(event, box, outlineArgb);
    }

    // ------------------------------------------------------------------
    // Colour channel extraction, as the 0..1 floats the vertex consumers take
    // ------------------------------------------------------------------

    public static float alpha(int argb) {
        return ((argb >>> 24) & 0xFF) / 255.0F;
    }

    public static float red(int argb) {
        return ((argb >>> 16) & 0xFF) / 255.0F;
    }

    public static float green(int argb) {
        return ((argb >>> 8) & 0xFF) / 255.0F;
    }

    public static float blue(int argb) {
        return (argb & 0xFF) / 255.0F;
    }

    /** Packs a colour with the alpha channel replaced. */
    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /**
     * Interpolates an entity's position for the current frame.
     *
     * <p>Entities move once per tick but the world draws many times per tick; using the raw
     * position makes a box visibly stutter behind the entity it is tracking.
     */
    public static Vec3d lerpPosition(net.minecraft.entity.Entity entity, float tickDelta) {
        return new Vec3d(
                lerp(entity.lastX, entity.getX(), tickDelta),
                lerp(entity.lastY, entity.getY(), tickDelta),
                lerp(entity.lastZ, entity.getZ(), tickDelta)
        );
    }

    /** An entity's bounding box, interpolated to the current frame. */
    public static Box interpolatedBox(net.minecraft.entity.Entity entity, float tickDelta) {
        Vec3d position = lerpPosition(entity, tickDelta);
        Box box = entity.getBoundingBox();
        return box.offset(position.subtract(entity.getPos()));
    }

    private static double lerp(double from, double to, float delta) {
        return from + (to - from) * delta;
    }
}
