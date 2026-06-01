package li.cil.oc.common.tileentity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for all OC block entities.
 *
 * <p>Mirrors the server/client NBT split from TileEntity.scala:
 * {@link #loadAdditional} dispatches to {@link #loadServer}/{@link #loadClient}
 * depending on which logical side we're on; the update packet always carries
 * client data written by {@link #saveClient}.</p>
 *
 * <p>Subclasses override {@code loadServer}/{@code saveServer} for persistent
 * data and {@code loadClient}/{@code saveClient} for the lightweight sync packet.</p>
 */
public abstract class OcBlockEntity extends BlockEntity {

    private static final String IS_SERVER_DATA_TAG = "oc:isServerData";

    protected OcBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // -----------------------------------------------------------------------
    // Server/client NBT split
    // -----------------------------------------------------------------------

    protected void loadServer(CompoundTag nbt, HolderLookup.Provider registries) {}

    protected void saveServer(CompoundTag nbt, HolderLookup.Provider registries) {}

    protected void loadClient(CompoundTag nbt, HolderLookup.Provider registries) {}

    protected void saveClient(CompoundTag nbt, HolderLookup.Provider registries) {}

    // -----------------------------------------------------------------------
    // BlockEntity lifecycle
    // -----------------------------------------------------------------------

    @Override
    protected void loadAdditional(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        boolean isServerData = nbt.getBoolean(IS_SERVER_DATA_TAG);
        if (level == null || !level.isClientSide || isServerData) {
            loadServer(nbt, registries);
        } else {
            loadClient(nbt, registries);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        nbt.putBoolean(IS_SERVER_DATA_TAG, true);
        saveServer(nbt, registries);
    }

    // -----------------------------------------------------------------------
    // Client sync packet (getUpdateTag / onDataPacket)
    // -----------------------------------------------------------------------

    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider registries) {
        CompoundTag nbt = super.getUpdateTag(registries);
        nbt.putBoolean(IS_SERVER_DATA_TAG, false);
        saveClient(nbt, registries);
        return nbt;
    }

    @Override
    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(@NotNull Connection connection,
                             @NotNull ClientboundBlockEntityDataPacket packet,
                             @NotNull HolderLookup.Provider registries) {
        CompoundTag nbt = packet.getTag();
        if (nbt != null) {
            loadClient(nbt, registries);
        }
    }
}
