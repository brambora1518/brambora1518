package gg.aurora.client.module.modules.render;

import gg.aurora.client.event.events.Render3DEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.ColorSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.util.RenderUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;

/**
 * Outlines entity collision boxes, optionally expanded.
 *
 * <p>The expansion is purely visual — it changes what you see, not what the server accepts — so an
 * expanded outline shows where a hit would land if you were close enough, not a larger reach.
 */
public final class Hitboxes extends Module {

    private final DoubleSetting expand =
            this.register(new DoubleSetting("Expand", "Grow each box by this many blocks.", 0.1D, 0.0D, 1.0D, 2));
    private final DoubleSetting range =
            this.register(new DoubleSetting("Range", "Maximum distance to draw, in blocks.", 32.0D, 4.0D, 128.0D, 0));
    private final BoolSetting livingOnly =
            this.register(new BoolSetting("Living only", "Skip items, arrows and other non-living entities.", true));
    private final ColorSetting color =
            this.register(new ColorSetting("Colour", "Outline colour.", 0xFFFBBF24));

    public Hitboxes() {
        super("Hitboxes", "Outlines entity collision boxes.", Category.RENDER);
        this.listen(Render3DEvent.class, this::onRender);
    }

    private void onRender(Render3DEvent event) {
        if (!this.inGame()) {
            return;
        }

        double maxDistance = this.range.value();
        double maxDistanceSquared = maxDistance * maxDistance;
        double grow = this.expand.value();

        for (Entity entity : this.world().getEntities()) {
            if (entity == this.player() || entity.isRemoved()) {
                continue;
            }

            if (this.livingOnly.value() && !(entity instanceof LivingEntity)) {
                continue;
            }

            if (this.player().squaredDistanceTo(entity) > maxDistanceSquared) {
                continue;
            }

            Box box = RenderUtil.interpolatedBox(entity, event.tickDelta()).expand(grow);
            RenderUtil.drawBoxOutline(event, box, this.color.argb());
        }
    }
}
