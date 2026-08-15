package gg.aurora.client.module.modules.movement;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;

/**
 * Holds sprint on without the key.
 *
 * <p>Sprinting is refused by the game while sneaking, while using an item, or below the hunger
 * threshold, so this only asks — it does not force a state the server would reject.
 */
public final class Sprint extends Module {

    private final BoolSetting keepWhileSneaking =
            this.register(new BoolSetting("While sneaking", "Keep sprinting when you start to sneak.", false));
    private final BoolSetting onlyForward =
            this.register(new BoolSetting("Forward only", "Do not sprint when strafing or walking backwards.", true));

    public Sprint() {
        super("Sprint", "Sprints without holding the key.", Category.MOVEMENT);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    private void onTick(TickEvent event) {
        if (!this.inGame()) {
            return;
        }

        if (!this.canSprint()) {
            return;
        }

        this.player().setSprinting(true);
    }

    private boolean canSprint() {
        var input = this.player().input;

        // Nothing to sprint with if the player is not moving.
        if (input.getMovementInput().lengthSquared() < 1.0E-4D) {
            return false;
        }

        if (this.onlyForward.value() && input.getMovementInput().y <= 0.0F) {
            return false;
        }

        if (!this.keepWhileSneaking.value() && this.player().isSneaking()) {
            return false;
        }

        // Below this the game drops sprint on its own; asking again every tick would just fight it.
        if (this.player().isUsingItem() || this.player().horizontalCollision) {
            return false;
        }

        return true;
    }
}
