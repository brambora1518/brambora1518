package gg.aurora.client.module.modules.combat;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.EnumSetting;
import gg.aurora.client.setting.IntSetting;
import gg.aurora.client.util.InventoryUtil;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Fires a wind charge on demand, either straight down for height or at what you are looking at.
 *
 * <p>In {@code LAUNCH} mode the pitch is forced to straight down for the shot and put back
 * afterwards, since a wind charge only lifts you if it lands under your feet.
 */
public final class AutoWindCharge extends Module {

    public enum Mode {
        /** Straight down, to gain height. */
        LAUNCH,
        /** Wherever you are aiming, to knock a target around. */
        AIM
    }

    private final EnumSetting<Mode> mode =
            this.register(new EnumSetting<>("Mode", "Where to aim the charge.", Mode.LAUNCH));
    private final IntSetting delay =
            this.register(new IntSetting("Delay", "Ticks between charges.", 10, 0, 60));
    private final BoolSetting requireBind =
            this.register(new BoolSetting("Hold to fire", "Only fire while the use key is held.", true));
    private final BoolSetting onlyOnGround =
            this.register(new BoolSetting("On ground only", "Do not fire mid-air.", false))
                    .visibleWhen(() -> this.mode.value() == Mode.LAUNCH);

    private int cooldown;

    public AutoWindCharge() {
        super("WindCharge", "Fires wind charges for height or knockback.", Category.COMBAT);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onEnable() {
        this.cooldown = 0;
    }

    private void onTick(TickEvent event) {
        if (!this.inGame() || mc.currentScreen != null) {
            return;
        }

        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }

        if (this.requireBind.value() && !mc.options.useKey.isPressed()) {
            return;
        }

        if (this.mode.value() == Mode.LAUNCH && this.onlyOnGround.value() && !this.player().isOnGround()) {
            return;
        }

        int slot = InventoryUtil.findHotbar(this.player(), Items.WIND_CHARGE);
        if (slot < 0) {
            return;
        }

        int previousSlot = this.player().getInventory().getSelectedSlot();
        float previousPitch = this.player().getPitch();

        InventoryUtil.select(mc, slot);

        if (this.mode.value() == Mode.LAUNCH) {
            this.player().setPitch(90.0F);
        }

        mc.interactionManager.interactItem(this.player(), Hand.MAIN_HAND);
        this.player().swingHand(Hand.MAIN_HAND);

        this.player().setPitch(previousPitch);
        InventoryUtil.select(mc, previousSlot);

        this.cooldown = this.delay.value();
    }

    @Override
    public String hudSuffix() {
        return this.mode.value().name();
    }
}
