package li.cil.oc.common.block;

import li.cil.oc.common.tileentity.ScreenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class Screen extends OcBlock implements EntityBlock {

    private final int tier;

    public Screen(int tier) {
        super();
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScreenBlockEntity(tier, pos, state);
    }
}
