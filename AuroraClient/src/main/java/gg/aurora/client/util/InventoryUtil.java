package gg.aurora.client.util;

import java.util.function.Predicate;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Hotbar lookups and selection, shared by the modules that need a particular item in hand.
 *
 * <p>Everything here works on the hotbar only. Reaching into the main inventory means clicking
 * slots, which the server sees as an inventory interaction; selecting a hotbar slot is an ordinary
 * scroll and is what a player would do anyway.
 */
public final class InventoryUtil {

    public static final int HOTBAR_SIZE = 9;

    private InventoryUtil() {
    }

    /** Hotbar index holding {@code item}, or {@code -1}. */
    public static int findHotbar(ClientPlayerEntity player, Item item) {
        return findHotbar(player, stack -> stack.isOf(item));
    }

    /** Hotbar index of the first stack matching {@code predicate}, or {@code -1}. */
    public static int findHotbar(ClientPlayerEntity player, Predicate<ItemStack> predicate) {
        var inventory = player.getInventory();

        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return slot;
            }
        }

        return -1;
    }

    /** True when either hand already holds {@code item}. */
    public static boolean isHolding(ClientPlayerEntity player, Item item) {
        return player.getMainHandStack().isOf(item) || player.getOffHandStack().isOf(item);
    }

    /**
     * Selects a hotbar slot.
     *
     * <p>The interaction manager compares the selected slot against its own copy every tick and
     * sends the held-item change itself, so setting the index is enough — the server is told on
     * the next tick without this having to build the packet.
     */
    public static void select(MinecraftClient client, int slot) {
        ClientPlayerEntity player = client.player;
        if (player == null || slot < 0 || slot >= HOTBAR_SIZE) {
            return;
        }

        if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
        }
    }

    /** Total count of {@code item} across the hotbar and main inventory. */
    public static int count(ClientPlayerEntity player, Item item) {
        var inventory = player.getInventory();
        int total = 0;

        for (int index = 0; index < inventory.getMainStacks().size(); index++) {
            ItemStack stack = inventory.getStack(index);
            if (stack.isOf(item)) {
                total += stack.getCount();
            }
        }

        return total;
    }
}
