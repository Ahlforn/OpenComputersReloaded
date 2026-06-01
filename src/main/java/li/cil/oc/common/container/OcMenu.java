package li.cil.oc.common.container;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Base class for all OC menus (container menus).
 *
 * <p>Replaces the 1.12.2 {@code Player} container base class
 * (confusingly named after the player, not the entity).</p>
 *
 * <p>Provides the standard 3×9 player inventory + hotbar slot layout
 * at configurable offsets via {@link #addPlayerSlots(int, int)}.</p>
 */
public abstract class OcMenu extends AbstractContainerMenu {

    protected static final int SLOT_SIZE = 18;

    protected final Inventory playerInventory;

    protected OcMenu(MenuType<?> type, int windowId, Inventory playerInventory) {
        super(type, windowId);
        this.playerInventory = playerInventory;
    }

    // -----------------------------------------------------------------------
    // Player inventory helper
    // -----------------------------------------------------------------------

    /** Adds the standard 3×9 + hotbar player inventory at (x, y). */
    protected void addPlayerSlots(int x, int y) {
        // 3 rows of 9 inventory slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory,
                    col + row * 9 + 9,              // skip hotbar (indices 0-8)
                    x + col * SLOT_SIZE,
                    y + row * SLOT_SIZE));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, x + col * SLOT_SIZE, y + 58));
        }
    }

    // -----------------------------------------------------------------------
    // AbstractContainerMenu contract
    // -----------------------------------------------------------------------

    @Override
    public boolean stillValid(Player player) {
        return true; // Subclasses override with distance checks.
    }

    /**
     * Shift-click: try moving the clicked stack to the other inventory.
     * Subclasses can override for finer control.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int containerSlots = slots.size() - 36; // everything before player inv
            if (index < containerSlots) {
                if (!moveItemStackTo(stack, containerSlots, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!moveItemStackTo(stack, 0, containerSlots, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }
}
