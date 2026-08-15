package gg.aurora.client.module.modules.combat;

import java.util.concurrent.ThreadLocalRandom;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.EnumSetting;
import gg.aurora.client.setting.IntSetting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Repeats the held mouse button at a chosen rate.
 *
 * <p>The interval is drawn fresh between the min and max clicks-per-second each time, so the rate
 * varies rather than landing on an exact metronome. Only fires while the real button is held —
 * this speeds up a click the player is already making, it does not click on its own.
 */
public final class AutoClicker extends Module {

    public enum Button {
        LEFT,
        RIGHT
    }

    private final EnumSetting<Button> button =
            this.register(new EnumSetting<>("Button", "Which mouse button to repeat.", Button.LEFT));
    private final IntSetting minCps =
            this.register(new IntSetting("Min CPS", "Slowest clicks per second.", 8, 1, 40));
    private final IntSetting maxCps =
            this.register(new IntSetting("Max CPS", "Fastest clicks per second.", 12, 1, 40));
    private final BoolSetting onlyOnTarget =
            this.register(new BoolSetting("Only on target", "Left click only when aimed at an entity.", false))
                    .visibleWhen(() -> this.button.value() == Button.LEFT);

    private long nextClickAt;

    public AutoClicker() {
        super("AutoClicker", "Repeats the held mouse button.", Category.COMBAT);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onEnable() {
        this.nextClickAt = 0L;
    }

    private void onTick(TickEvent event) {
        if (!this.inGame() || mc.currentScreen != null) {
            return;
        }

        boolean held = this.button.value() == Button.LEFT
                ? mc.options.attackKey.isPressed()
                : mc.options.useKey.isPressed();

        if (!held) {
            // Reset, so releasing and re-pressing starts a fresh interval instead of firing
            // immediately on a timer that ran down while the button was up.
            this.nextClickAt = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (now < this.nextClickAt) {
            return;
        }

        if (this.button.value() == Button.LEFT) {
            if (this.onlyOnTarget.value() && !(mc.crosshairTarget instanceof EntityHitResult)) {
                return;
            }
            this.clickLeft();
        } else {
            mc.interactionManager.interactItem(this.player(), Hand.MAIN_HAND);
            this.player().swingHand(Hand.MAIN_HAND);
        }

        this.nextClickAt = now + this.nextInterval();
    }

    private void clickLeft() {
        HitResult hit = mc.crosshairTarget;

        if (hit instanceof EntityHitResult entityHit) {
            mc.interactionManager.attackEntity(this.player(), entityHit.getEntity());
        }

        this.player().swingHand(Hand.MAIN_HAND);
    }

    /** Milliseconds until the next click, drawn from the configured CPS range. */
    private long nextInterval() {
        // Tolerate the sliders being set the wrong way round rather than throwing.
        int low = Math.min(this.minCps.value(), this.maxCps.value());
        int high = Math.max(this.minCps.value(), this.maxCps.value());

        double cps = low == high ? low : ThreadLocalRandom.current().nextDouble(low, high);
        return Math.round(1000.0D / cps);
    }

    @Override
    public String hudSuffix() {
        return this.minCps.value() + "-" + this.maxCps.value();
    }
}
