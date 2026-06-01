package li.cil.oc.common.tileentity;

import li.cil.oc.common.init.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Stub for Phase 3. Full text-buffer and network-node wiring in Phase 3b. */
public class ScreenBlockEntity extends OcBlockEntity {

    private final int tier;

    public ScreenBlockEntity(int tier, BlockPos pos, BlockState state) {
        super(Registries.SCREEN_BE.get(), pos, state);
        this.tier = tier;
    }

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        this(0, pos, state);
    }

    public int getTier() { return tier; }

    public void serverTick() {}
}
