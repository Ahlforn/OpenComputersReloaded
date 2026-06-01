package li.cil.oc.common.tileentity;

import li.cil.oc.common.init.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Stub — keyboard component and key event routing in Phase 3b. */
public class KeyboardBlockEntity extends OcBlockEntity {

    public KeyboardBlockEntity(BlockPos pos, BlockState state) {
        super(Registries.KEYBOARD_BE.get(), pos, state);
    }
}
