package li.cil.oc.common.container;

import li.cil.oc.common.init.Registries;
import li.cil.oc.common.tileentity.CaseBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * Menu (container) for computer cases.
 *
 * <p>Slot layout mirrors the original Scala {@code Case} container.
 * Component slot wiring (driver registry, slot types) is Phase 3b/10;
 * for now a plain player inventory is sufficient to open the GUI.</p>
 */
public class CaseMenu extends OcMenu {

    public static final int PLAYER_INVENTORY_X = 8;
    public static final int PLAYER_INVENTORY_Y = 84;

    private final CaseBlockEntity blockEntity;

    /** Server-side constructor called by MenuProvider. */
    public CaseMenu(int windowId, Inventory playerInventory, CaseBlockEntity be) {
        super(Registries.CASE_MENU.get(), windowId, playerInventory);
        this.blockEntity = be;
        addPlayerSlots(PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
        // Phase 3b: add component slots from InventorySlots.computer(tier)
    }

    /** Client-side constructor called by the MenuType factory (extra data = BE pos). */
    public CaseMenu(int windowId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(windowId, playerInventory, lookupBE(playerInventory, extraData));
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null) return false;
        return player.distanceToSqr(
            blockEntity.getBlockPos().getX() + 0.5,
            blockEntity.getBlockPos().getY() + 0.5,
            blockEntity.getBlockPos().getZ() + 0.5) < 64.0;
    }

    public CaseBlockEntity getBlockEntity() { return blockEntity; }

    // -----------------------------------------------------------------------

    private static CaseBlockEntity lookupBE(Inventory inv, FriendlyByteBuf buf) {
        if (inv.player.level().isClientSide) return null;
        var pos = buf.readBlockPos();
        if (inv.player.level().getBlockEntity(pos) instanceof CaseBlockEntity be) return be;
        return null;
    }
}
