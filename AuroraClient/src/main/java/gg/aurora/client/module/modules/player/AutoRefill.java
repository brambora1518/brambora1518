package gg.aurora.client.module.modules.player;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.IntSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Tops up a running-low hotbar stack from a matching one in the inventory.
 *
 * <p>Only acts on the selected slot, and only while no screen is open, so it never competes with
 * the player's own inventory clicks.
 */
public final class AutoRefill extends Module {

    private static final int HOTBAR_SIZE = 9;
    private static final int HOTBAR_SCREEN_OFFSET = 36;

    /** Where the main inventory rows start, i.e. the first slot that is not the hotbar. */
    private static final int MAIN_INVENTORY_START = 9;

    private final IntSetting threshold =
            this.register(new IntSetting("Threshold", "Refill when the held stack drops to this count.", 8, 1, 63));
    private final IntSetting delay =
            this.register(new IntSetting("Delay", "Ticks to wait between refills.", 2, 0, 20));
    private final BoolSetting heldOnly =
            this.register(new BoolSetting("Held slot only", "Only refill the slot you are holding.", true));

    private int cooldown;

    public AutoRefill() {
        super("AutoRefill", "Refills a low hotbar stack from your inventory.", Category.PLAYER);
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

        var inventory = this.player().getInventory();
        int start = this.heldOnly.value() ? inventory.getSelectedSlot() : 0;
        int end = this.heldOnly.value() ? inventory.getSelectedSlot() + 1 : HOTBAR_SIZE;

        for (int slot = start; slot < end; slot++) {
            ItemStack held = inventory.getStack(slot);

            // An empty slot has nothing to match against; a full one needs nothing.
            if (held.isEmpty() || held.getCount() > this.threshold.value() || !held.isStackable()) {
                continue;
            }

            int source = this.findMatch(held);
            if (source < 0) {
                continue;
            }

            this.merge(source, slot);
            this.cooldown = this.delay.value();
            return;
        }
    }

    /** Inventory index of a stack that can merge into {@code target}, or {@code -1}. */
    private int findMatch(ItemStack target) {
        var inventory = this.player().getInventory();

        for (int index = MAIN_INVENTORY_START; index < inventory.getMainStacks().size(); index++) {
            ItemStack candidate = inventory.getStack(index);
            if (!candidate.isEmpty() && ItemStack.areItemsAndComponentsEqual(candidate, target)) {
                return index;
            }
        }

        return -1;
    }

    /**
     * Picks up the source stack and drops it onto the hotbar slot, which merges the two. Anything
     * that does not fit stays on the cursor, so it is returned to where it came from.
     */
    private void merge(int sourceIndex, int hotbarIndex) {
        int syncId = this.player().playerScreenHandler.syncId;
        int sourceSlot = toScreenSlot(sourceIndex);
        int targetSlot = toScreenSlot(hotbarIndex);

        mc.interactionManager.clickSlot(syncId, sourceSlot, 0, SlotActionType.PICKUP, this.player());
        mc.interactionManager.clickSlot(syncId, targetSlot, 0, SlotActionType.PICKUP, this.player());
        mc.interactionManager.clickSlot(syncId, sourceSlot, 0, SlotActionType.PICKUP, this.player());
    }

    private static int toScreenSlot(int inventoryIndex) {
        return inventoryIndex < HOTBAR_SIZE
                ? HOTBAR_SCREEN_OFFSET + inventoryIndex
                : inventoryIndex;
    }
}
