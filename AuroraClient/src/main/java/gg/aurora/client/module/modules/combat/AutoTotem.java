package gg.aurora.client.module.modules.combat;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.setting.IntSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Keeps a totem of undying in the off-hand.
 *
 * <p>Refills only when the off-hand is actually empty or holding something else, and only while
 * no screen is open — moving items underneath an open container would fight the player's own
 * clicks and can desync the inventory.
 */
public final class AutoTotem extends Module {

    /** Slot index of the off-hand within the player's own screen handler. */
    private static final int OFFHAND_SLOT = 45;

    /** Inventory indices 0..8 are the hotbar; in the screen handler they sit after the main rows. */
    private static final int HOTBAR_SIZE = 9;
    private static final int HOTBAR_SCREEN_OFFSET = 36;

    private final DoubleSetting healthThreshold =
            this.register(new DoubleSetting("Health", "Only refill at or below this much health. 20 is always.",
                    20.0D, 1.0D, 20.0D, 1));
    private final IntSetting delay =
            this.register(new IntSetting("Delay", "Ticks to wait between swaps.", 1, 0, 20));
    private final BoolSetting offhandOnly =
            this.register(new BoolSetting("Off-hand only", "Leave the main hand alone.", true));

    private int cooldown;
    private int totemsLeft;

    public AutoTotem() {
        super("AutoTotem", "Keeps a totem in your off-hand.", Category.COMBAT);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onEnable() {
        this.cooldown = 0;
    }

    private void onTick(TickEvent event) {
        if (!this.inGame()) {
            return;
        }

        // Any open screen means the player (or another module) may be mid-interaction.
        if (mc.currentScreen != null) {
            return;
        }

        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }

        if (this.player().getHealth() > this.healthThreshold.value()) {
            return;
        }

        ItemStack offhand = this.player().getOffHandStack();
        if (offhand.isOf(Items.TOTEM_OF_UNDYING)) {
            this.totemsLeft = this.countTotems();
            return;
        }

        int source = this.findTotemSlot();
        if (source < 0) {
            this.totemsLeft = 0;
            return;
        }

        this.swapToOffhand(source, !offhand.isEmpty());
        this.cooldown = this.delay.value();
        this.totemsLeft = this.countTotems();
    }

    /** Inventory index of a totem, or {@code -1}. The off-hand itself is not searched. */
    private int findTotemSlot() {
        var inventory = this.player().getInventory();

        for (int index = 0; index < inventory.getMainStacks().size(); index++) {
            if (this.offhandOnly.value() && index == inventory.getSelectedSlot()) {
                // Swapping the held item away mid-fight would drop the weapon.
                continue;
            }

            if (inventory.getStack(index).isOf(Items.TOTEM_OF_UNDYING)) {
                return index;
            }
        }

        return -1;
    }

    private int countTotems() {
        var inventory = this.player().getInventory();
        int total = 0;

        for (int index = 0; index < inventory.getMainStacks().size(); index++) {
            ItemStack stack = inventory.getStack(index);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                total += stack.getCount();
            }
        }

        return total;
    }

    /**
     * Moves the stack at {@code inventoryIndex} into the off-hand.
     *
     * <p>Three clicks, because the server has no "swap into off-hand" action: take the totem, drop
     * it in the off-hand, then put whatever was displaced back where the totem came from. The last
     * click is skipped when the off-hand was empty, since there is nothing left on the cursor.
     */
    private void swapToOffhand(int inventoryIndex, boolean offhandOccupied) {
        int syncId = this.player().playerScreenHandler.syncId;
        int screenSlot = toScreenSlot(inventoryIndex);

        mc.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, this.player());
        mc.interactionManager.clickSlot(syncId, OFFHAND_SLOT, 0, SlotActionType.PICKUP, this.player());

        if (offhandOccupied) {
            mc.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, this.player());
        }
    }

    private static int toScreenSlot(int inventoryIndex) {
        return inventoryIndex < HOTBAR_SIZE
                ? HOTBAR_SCREEN_OFFSET + inventoryIndex
                : inventoryIndex;
    }

    @Override
    public String hudSuffix() {
        return String.valueOf(this.totemsLeft);
    }
}
