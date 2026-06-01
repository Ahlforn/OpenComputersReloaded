package li.cil.oc.common.tileentity;

import li.cil.oc.common.init.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Stub — floppy mount/unmount, OC network node in Phase 3b. */
public class DiskDriveBlockEntity extends OcBlockEntity {

    public DiskDriveBlockEntity(BlockPos pos, BlockState state) {
        super(Registries.DISK_DRIVE_BE.get(), pos, state);
    }

    public void serverTick() {}
}
