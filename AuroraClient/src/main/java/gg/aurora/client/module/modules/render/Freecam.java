package gg.aurora.client.module.modules.render;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.util.RotationUtil;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;

/**
 * Detaches the camera and flies it around while the player stands still.
 *
 * <p>The player's own movement input is zeroed every tick, so the body stays put and nothing
 * unusual is sent to the server — this changes what you see, not where you are.
 *
 * <p>Steering reuses the player's yaw and pitch rather than intercepting the mouse, which keeps
 * the module free of input-pipeline hooks. The visible consequence is that the body turns on the
 * spot while you look around; the rotation you started with is restored on disable.
 */
public final class Freecam extends Module {

    private final DoubleSetting speed =
            this.register(new DoubleSetting("Speed", "Blocks per tick.", 1.0D, 0.1D, 5.0D, 1));
    private final BoolSetting freezePlayer =
            this.register(new BoolSetting("Freeze player", "Stop your body moving while detached.", true));

    private Vec3d position = Vec3d.ZERO;
    private Vec3d lastPosition = Vec3d.ZERO;

    private float savedYaw;
    private float savedPitch;

    public Freecam() {
        super("Freecam", "Detaches the camera from your body.", Category.RENDER);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onEnable() {
        if (!this.inGame()) {
            this.setEnabled(false);
            return;
        }

        this.position = this.player().getEyePos();
        this.lastPosition = this.position;
        this.savedYaw = this.player().getYaw();
        this.savedPitch = this.player().getPitch();
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            mc.player.setYaw(this.savedYaw);
            mc.player.setPitch(this.savedPitch);
        }
    }

    private void onTick(TickEvent event) {
        if (!this.inGame()) {
            return;
        }

        this.lastPosition = this.position;
        this.position = this.position.add(this.desiredMovement());

        if (this.freezePlayer.value() && mc.currentScreen == null) {
            // Replace the input the player just produced, before the movement tick consumes it.
            this.player().input.playerInput =
                    new PlayerInput(false, false, false, false, false, false, false);
        }
    }

    /** Movement for this tick, derived from the movement keys and the current view direction. */
    private Vec3d desiredMovement() {
        if (mc.currentScreen != null) {
            return Vec3d.ZERO;
        }

        double forward = 0.0D;
        double strafe = 0.0D;
        double vertical = 0.0D;

        if (mc.options.forwardKey.isPressed()) {
            forward += 1.0D;
        }
        if (mc.options.backKey.isPressed()) {
            forward -= 1.0D;
        }
        if (mc.options.rightKey.isPressed()) {
            strafe += 1.0D;
        }
        if (mc.options.leftKey.isPressed()) {
            strafe -= 1.0D;
        }
        if (mc.options.jumpKey.isPressed()) {
            vertical += 1.0D;
        }
        if (mc.options.sneakKey.isPressed()) {
            vertical -= 1.0D;
        }

        if (forward == 0.0D && strafe == 0.0D && vertical == 0.0D) {
            return Vec3d.ZERO;
        }

        // Forward follows the full look direction, so looking up and holding forward climbs.
        Vec3d look = RotationUtil.toVector(this.player().getYaw(), this.player().getPitch());
        Vec3d right = RotationUtil.toVector(this.player().getYaw() + 90.0F, 0.0F);

        Vec3d movement = look.multiply(forward)
                .add(right.multiply(strafe))
                .add(0.0D, vertical, 0.0D);

        if (movement.lengthSquared() > 1.0E-6D) {
            movement = movement.normalize().multiply(this.speed.value());
        }

        return movement;
    }

    // ------------------------------------------------------------------
    // Read from the camera mixin, on the render thread
    // ------------------------------------------------------------------

    /** Camera position interpolated for the current frame, so flight looks smooth at any FPS. */
    public Vec3d cameraPos(float tickDelta) {
        return this.lastPosition.lerp(this.position, tickDelta);
    }

    public float cameraYaw() {
        return mc.player == null ? this.savedYaw : mc.player.getYaw();
    }

    public float cameraPitch() {
        return mc.player == null ? this.savedPitch : mc.player.getPitch();
    }
}
