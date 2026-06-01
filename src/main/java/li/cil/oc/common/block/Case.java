package li.cil.oc.common.block;

import li.cil.oc.common.Tier;
import li.cil.oc.common.tileentity.CaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class Case extends OcBlock implements EntityBlock {

    private final int tier;

    public Case(int tier) {
        super();
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    public boolean isCreative() {
        return tier >= Tier.FOUR;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CaseBlockEntity(tier, pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> beType) {
        if (level.isClientSide) return null;
        return (lvl, pos, blockState, be) -> {
            if (be instanceof CaseBlockEntity cbe) {
                cbe.serverTick(lvl, pos, blockState);
            }
        };
    }
}
