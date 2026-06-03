package li.cil.oc.common.tileentity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for all OC block entities.
 *
 * <p>Mirrors the server/client NBT split from TileEntity.scala:
 * {@link #loadAdditional} dispatches to {@link #loadServer}/{@link #loadClient}
 * depending on which logical side we're on; the update packet always carries
 * client data written by {@link #fillClientUpdateTag}.</p>
 *
 * <p>Subclasses override {@code loadServer}/{@code saveServer} for persistent
 * data and {@code loadClient}/{@code fillClientUpdateTag} for the lightweight
 * sync packet.</p>
 */
public abstract class OcBlockEntity extends BlockEntity {

    private static final String IS_SERVER_DATA_TAG = "oc:isServerData";

    protected OcBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // -----------------------------------------------------------------------
    // Server/client NBT split
    // -----------------------------------------------------------------------

    protected void loadServer(ValueInput input) {}

    protected void saveServer(ValueOutput output) {}

    protected void loadClient(ValueInput input) {}

    protected void fillClientUpdateTag(CompoundTag nbt) {}

    // -----------------------------------------------------------------------
    // BlockEntity lifecycle
    // -----------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        boolean isServerData = input.getBooleanOr(IS_SERVER_DATA_TAG, true);
        if (level == null || !level.isClientSide() || isServerData) {
            loadServer(input);
        } else {
            loadClient(input);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean(IS_SERVER_DATA_TAG, true);
        saveServer(output);
    }

    // -----------------------------------------------------------------------
    // Client sync packet (getUpdateTag / loadCustomOnly → loadAdditional)
    // -----------------------------------------------------------------------

    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider registries) {
        CompoundTag nbt = super.getUpdateTag(registries);
        nbt.putBoolean(IS_SERVER_DATA_TAG, false);
        fillClientUpdateTag(nbt);
        return nbt;
    }

    @Override
    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
