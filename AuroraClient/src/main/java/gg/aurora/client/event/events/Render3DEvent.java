package gg.aurora.client.event.events;

import gg.aurora.client.event.Event;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Fired each frame while the world is being drawn, after entities and before translucency.
 *
 * <p>The matrix stack arrives in <em>camera space</em>: the origin is the camera, not the world
 * origin. Anything positioned in world coordinates has to be shifted by {@link #cameraPos()}
 * first, which {@code RenderUtil} does for you. Drawing without that subtraction is the usual
 * reason a box renders far from the block it belongs to.
 */
public final class Render3DEvent implements Event {

    private final MatrixStack matrices;
    private final VertexConsumerProvider consumers;
    private final Camera camera;
    private final float tickDelta;

    public Render3DEvent(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, float tickDelta) {
        this.matrices = matrices;
        this.consumers = consumers;
        this.camera = camera;
        this.tickDelta = tickDelta;
    }

    public MatrixStack matrices() {
        return this.matrices;
    }

    public VertexConsumerProvider consumers() {
        return this.consumers;
    }

    public Camera camera() {
        return this.camera;
    }

    /** World-space position of the camera; subtract it from world coordinates before drawing. */
    public Vec3d cameraPos() {
        return this.camera.getPos();
    }

    public float tickDelta() {
        return this.tickDelta;
    }
}
