package gg.aurora.client.module.modules.render;

import java.util.List;

import gg.aurora.client.event.events.Render3DEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.ColorSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.setting.IntSetting;
import gg.aurora.client.util.RenderUtil;
import gg.aurora.client.util.TrajectoryUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Draws the flight path of the throwable you are holding, and marks where it lands.
 *
 * <p>The path is stepped one tick at a time using the same drag and gravity the server applies,
 * and each step is ray-traced against the world so the arc stops at the first block it would hit
 * rather than passing through terrain.
 */
public final class PearlPrediction extends Module {

    /** Launch speed of a thrown item, in blocks per tick. */
    private static final double THROW_POWER = 1.5D;

    private final IntSetting maxTicks =
            this.register(new IntSetting("Max ticks", "How far ahead to simulate.", 80, 10, 200));
    private final DoubleSetting thickness =
            this.register(new DoubleSetting("Thickness", "Size of each step marker.", 0.06D, 0.01D, 0.3D, 2));
    private final BoolSetting landingMarker =
            this.register(new BoolSetting("Landing marker", "Draw a box where it lands.", true));
    private final ColorSetting pathColor =
            this.register(new ColorSetting("Path colour", "Colour of the arc.", 0xFFA855F7));
    private final ColorSetting landingColor =
            this.register(new ColorSetting("Landing colour", "Colour of the landing marker.", 0xFFF472B6))
                    .visibleWhen(() -> this.landingMarker.value());

    public PearlPrediction() {
        super("PearlPrediction", "Shows where your pearl or throwable will land.", Category.RENDER);
        this.listen(Render3DEvent.class, this::onRender);
    }

    private void onRender(Render3DEvent event) {
        if (!this.inGame() || !this.holdingThrowable()) {
            return;
        }

        Vec3d origin = this.player().getEyePos();
        Vec3d velocity = TrajectoryUtil.launchVelocity(
                this.player().getYaw(), this.player().getPitch(), THROW_POWER);

        List<Vec3d> path = TrajectoryUtil.simulate(
                origin, velocity, TrajectoryUtil.THROWN_GRAVITY, this.maxTicks.value());

        double half = this.thickness.value() / 2.0D;

        for (int index = 1; index < path.size(); index++) {
            Vec3d from = path.get(index - 1);
            Vec3d to = path.get(index);

            // Ray-trace each step rather than only the endpoints: a fast projectile covers more
            // than a block per tick, so sampling positions alone would tunnel through walls.
            HitResult hit = this.world().raycast(new RaycastContext(
                    from, to,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    this.player()
            ));

            if (hit.getType() != HitResult.Type.MISS) {
                Vec3d impact = hit.getPos();
                this.drawStep(event, impact, half);

                if (this.landingMarker.value() && hit instanceof BlockHitResult) {
                    RenderUtil.drawBox(event,
                            new Box(impact.subtract(0.25D, 0.25D, 0.25D), impact.add(0.25D, 0.25D, 0.25D)),
                            RenderUtil.withAlpha(this.landingColor.argb(), 60),
                            this.landingColor.argb());
                }
                return;
            }

            this.drawStep(event, to, half);
        }
    }

    private void drawStep(Render3DEvent event, Vec3d at, double half) {
        RenderUtil.drawBoxOutline(event,
                new Box(at.subtract(half, half, half), at.add(half, half, half)),
                this.pathColor.argb());
    }

    private boolean holdingThrowable() {
        return isThrowable(this.player().getMainHandStack())
                || isThrowable(this.player().getOffHandStack());
    }

    private static boolean isThrowable(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.ENDER_PEARL
                || item == Items.SNOWBALL
                || item == Items.EGG
                || item == Items.SPLASH_POTION
                || item == Items.LINGERING_POTION
                || item == Items.EXPERIENCE_BOTTLE;
    }
}
