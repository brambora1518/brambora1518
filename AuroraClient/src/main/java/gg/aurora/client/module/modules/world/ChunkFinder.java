package gg.aurora.client.module.modules.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import gg.aurora.client.event.events.PacketEvent;
import gg.aurora.client.event.events.Render3DEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.ColorSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.setting.IntSetting;
import gg.aurora.client.util.RenderUtil;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

/**
 * Marks chunks as the server sends them, so freshly streamed terrain stands out.
 *
 * <p>Each mark fades over its lifetime and is then dropped, which keeps the map bounded on a long
 * session instead of growing with every chunk ever seen.
 *
 * <p>Chunk packets arrive on the netty thread while rendering reads the same map on the render
 * thread, hence the concurrent map — see {@link PacketEvent}.
 */
public final class ChunkFinder extends Module {

    private final IntSetting lifetime =
            this.register(new IntSetting("Lifetime", "Seconds a mark stays visible.", 30, 5, 300));
    private final DoubleSetting height =
            this.register(new DoubleSetting("Height", "Y level to draw the marks at.", 64.0D, -64.0D, 320.0D, 0));
    private final DoubleSetting thickness =
            this.register(new DoubleSetting("Thickness", "Vertical size of each mark.", 1.0D, 0.1D, 16.0D, 1));
    private final ColorSetting color =
            this.register(new ColorSetting("Colour", "Mark colour.", 0xFF38BDF8));

    /** Chunk position to the wall-clock time it arrived. */
    private final Map<ChunkPos, Long> seen = new ConcurrentHashMap<>();

    public ChunkFinder() {
        super("ChunkFinder", "Marks chunks as the server streams them in.", Category.WORLD);
        this.listen(PacketEvent.Receive.class, this::onPacket);
        this.listen(Render3DEvent.class, this::onRender);
    }

    @Override
    protected void onEnable() {
        this.seen.clear();
    }

    @Override
    protected void onDisable() {
        this.seen.clear();
    }

    private void onPacket(PacketEvent.Receive event) {
        if (event.packet() instanceof ChunkDataS2CPacket chunk) {
            this.seen.put(new ChunkPos(chunk.getChunkX(), chunk.getChunkZ()), System.currentTimeMillis());
        }
    }

    private void onRender(Render3DEvent event) {
        if (!this.inGame()) {
            return;
        }

        long now = System.currentTimeMillis();
        long lifetimeMillis = this.lifetime.value() * 1000L;
        double top = this.height.value() + this.thickness.value();

        this.seen.entrySet().removeIf(entry -> now - entry.getValue() > lifetimeMillis);

        for (Map.Entry<ChunkPos, Long> entry : this.seen.entrySet()) {
            ChunkPos pos = entry.getKey();

            // Fade from full opacity when new to nothing as the mark expires.
            double remaining = 1.0D - (double) (now - entry.getValue()) / lifetimeMillis;
            int alpha = (int) Math.round(this.color.alpha() * Math.max(0.0D, remaining));

            Box box = new Box(
                    pos.getStartX(), this.height.value(), pos.getStartZ(),
                    pos.getEndX() + 1.0D, top, pos.getEndZ() + 1.0D
            );

            RenderUtil.drawBoxOutline(event, box, RenderUtil.withAlpha(this.color.argb(), alpha));
        }
    }

    @Override
    public String hudSuffix() {
        return String.valueOf(this.seen.size());
    }
}
