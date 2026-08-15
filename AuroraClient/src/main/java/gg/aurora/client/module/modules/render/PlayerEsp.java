package gg.aurora.client.module.modules.render;

import gg.aurora.client.event.events.Render3DEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.ColorSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.setting.EnumSetting;
import gg.aurora.client.util.RenderUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

/**
 * Draws a box around every other player within range.
 *
 * <p>Friendly and hostile players get separate colours so a crowded scene stays readable; the
 * distinction is by team, which is the only grouping the client can see without a server-side
 * plugin telling it more.
 */
public final class PlayerEsp extends Module {

    public enum Style {
        BOX,
        FILLED,
        BOTH
    }

    private final EnumSetting<Style> style =
            this.register(new EnumSetting<>("Style", "How each player is drawn.", Style.BOTH));
    private final DoubleSetting range =
            this.register(new DoubleSetting("Range", "Maximum distance to draw, in blocks.", 96.0D, 8.0D, 256.0D, 0));
    private final BoolSetting teamColors =
            this.register(new BoolSetting("Team colours", "Colour teammates differently.", true));
    private final ColorSetting color =
            this.register(new ColorSetting("Colour", "Colour for other players.", 0xFFEF4444));
    private final ColorSetting teamColor =
            this.register(new ColorSetting("Team colour", "Colour for players on your team.", 0xFF22C55E))
                    .visibleWhen(() -> this.teamColors.value());
    private final DoubleSetting fillOpacity =
            this.register(new DoubleSetting("Fill opacity", "Opacity of the filled body, as a fraction.",
                    0.25D, 0.0D, 1.0D, 2))
                    .visibleWhen(() -> this.style.value() != Style.BOX);

    private int visible;

    public PlayerEsp() {
        super("PlayerESP", "Highlights other players through the world.", Category.RENDER);
        this.listen(Render3DEvent.class, this::onRender);
    }

    private void onRender(Render3DEvent event) {
        if (!this.inGame()) {
            return;
        }

        double maxDistance = this.range.value();
        double maxDistanceSquared = maxDistance * maxDistance;
        int count = 0;

        for (PlayerEntity target : this.world().getPlayers()) {
            if (target == this.player() || target.isSpectator() || target.isRemoved()) {
                continue;
            }

            if (this.player().squaredDistanceTo(target) > maxDistanceSquared) {
                continue;
            }

            int argb = this.teamColors.value() && this.player().isTeammate(target)
                    ? this.teamColor.argb()
                    : this.color.argb();

            Box box = RenderUtil.interpolatedBox(target, event.tickDelta());

            switch (this.style.value()) {
                case BOX -> RenderUtil.drawBoxOutline(event, box, argb);
                case FILLED -> RenderUtil.drawBoxFilled(event, box, this.fill(argb));
                case BOTH -> RenderUtil.drawBox(event, box, this.fill(argb), argb);
            }

            count++;
        }

        this.visible = count;
    }

    private int fill(int argb) {
        int alpha = (int) Math.round(((argb >>> 24) & 0xFF) * this.fillOpacity.value());
        return RenderUtil.withAlpha(argb, alpha);
    }

    @Override
    public String hudSuffix() {
        return this.visible > 0 ? String.valueOf(this.visible) : null;
    }
}
