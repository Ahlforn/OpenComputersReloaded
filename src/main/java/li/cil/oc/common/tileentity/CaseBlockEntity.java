package li.cil.oc.common.tileentity;

import li.cil.oc.api.API;
import li.cil.oc.api.machine.MachineHost;
import li.cil.oc.api.network.Node;
import li.cil.oc.common.container.CaseMenu;
import li.cil.oc.common.init.Registries;
import li.cil.oc.server.PacketSender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
 * Block entity for all computer-case tiers.
 *
 * <p>Implements {@link MachineHost}: creates a {@link li.cil.oc.api.machine.Machine}
 * via the registered {@link li.cil.oc.api.detail.MachineAPI}, and forwards the
 * server tick to {@code machine.update()}. Save/load delegates to the machine.</p>
 *
 * <p>Component inventory management, redstone I/O, power acceptance, and network
 * node wiring are all Phase 3 follow-on work; stubs are present here so the
 * machine can at least boot.</p>
 */
public class CaseBlockEntity extends OcBlockEntity implements MachineHost, MenuProvider {

    private static final String MACHINE_TAG = "oc:computer";
    private static final String TIER_TAG     = "oc:tier";
    private static final String RUNNING_TAG  = "oc:isRunning";
    private static final String ERROR_TAG    = "oc:hasErrored";

    // -1 means "not yet loaded from NBT"
    private int tier;
    private li.cil.oc.api.machine.Machine machine;

    // Lazy Forge Energy bridge — created on first capability query
    private OcEnergyStorage energyStorage;

    // Client-side state for rendering
    public boolean isRunning;
    public boolean hasErrored;

    public CaseBlockEntity(int tier, BlockPos pos, BlockState state) {
        super(Registries.CASE_BE.get(), pos, state);
        this.tier = tier;
    }

    /** Used by the BlockEntityType factory (deserialization); tier is loaded from NBT. */
    public CaseBlockEntity(BlockPos pos, BlockState state) {
        this(0, pos, state);
    }

    // -----------------------------------------------------------------------
    // MachineHost
    // -----------------------------------------------------------------------

    @Override
    public li.cil.oc.api.machine.Machine machine() {
        return machine;
    }

    public OcEnergyStorage energyStorage() {
        if (energyStorage == null) energyStorage = new OcEnergyStorage(this);
        return energyStorage;
    }

    @Override
    public Iterable<ItemStack> internalComponents() {
        // Phase 3b: return contents of component slots.
        return Collections.emptyList();
    }

    @Override
    public int componentSlot(String address) {
        return -1;
    }

    @Override
    public void onMachineConnect(Node node) {}

    @Override
    public void onMachineDisconnect(Node node) {}

    // -----------------------------------------------------------------------
    // EnvironmentHost
    // -----------------------------------------------------------------------

    @Override
    public Level world() {
        return level;
    }

    @Override
    public double xPosition() {
        return getBlockPos().getX() + 0.5;
    }

    @Override
    public double yPosition() {
        return getBlockPos().getY() + 0.5;
    }

    @Override
    public double zPosition() {
        return getBlockPos().getZ() + 0.5;
    }

    @Override
    public void markChanged() {
        setChanged();
    }

    // -----------------------------------------------------------------------
    // Block entity lifecycle
    // -----------------------------------------------------------------------

    /** Called once when the BE is placed into the world. */
    private void initMachine() {
        if (machine == null && level != null && !level.isClientSide() && API.machine != null) {
            machine = API.machine.create(this);
        }
    }

    @Override
    public void setLevel(@NotNull Level level) {
        super.setLevel(level);
        initMachine();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (machine != null) {
            machine.stop();
        }
    }

    // -----------------------------------------------------------------------
    // Server tick (called by BlockEntityTicker registered in CaseBlock)
    // -----------------------------------------------------------------------

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        initMachine();
        if (machine != null) {
            machine.update();

            boolean running = machine.isRunning();
            boolean errored  = machine.lastError() != null;
            if (running != isRunning || errored != hasErrored) {
                isRunning  = running;
                hasErrored = errored;
                setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
                PacketSender.sendComputerState(this);
            }
        }
    }

    // -----------------------------------------------------------------------
    // NBT persistence
    // -----------------------------------------------------------------------

    @Override
    protected void loadServer(ValueInput input) {
        tier = Math.max(0, Math.min(3, input.getByteOr(TIER_TAG, (byte) 0)));
        initMachine();
        if (machine != null) {
            input.read(MACHINE_TAG, CompoundTag.CODEC).ifPresent(machine::load);
            isRunning = machine.isRunning();
        }
    }

    @Override
    protected void saveServer(ValueOutput output) {
        output.putByte(TIER_TAG, (byte) tier);
        if (machine != null) {
            CompoundTag machineTag = new CompoundTag();
            machine.save(machineTag);
            output.store(MACHINE_TAG, CompoundTag.CODEC, machineTag);
        }
    }

    @Override
    protected void loadClient(ValueInput input) {
        isRunning  = input.getBooleanOr(RUNNING_TAG, false);
        hasErrored = input.getBooleanOr(ERROR_TAG, false);
    }

    @Override
    protected void fillClientUpdateTag(CompoundTag nbt) {
        nbt.putBoolean(RUNNING_TAG, machine != null && machine.isRunning());
        nbt.putBoolean(ERROR_TAG,   machine != null && machine.lastError() != null);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    public int getTier() { return tier; }

    // -----------------------------------------------------------------------
    // MenuProvider — allows players to open the case GUI
    // -----------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.opencomputers.case");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int windowId, Inventory playerInventory,
                                                      Player player) {
        return new CaseMenu(windowId, playerInventory, this);
    }

    // -----------------------------------------------------------------------
    // Facing direction (Phase 3b): stub for Direction API compatibility
    // -----------------------------------------------------------------------

    public Direction getFacing() {
        return Direction.NORTH;
    }
}
