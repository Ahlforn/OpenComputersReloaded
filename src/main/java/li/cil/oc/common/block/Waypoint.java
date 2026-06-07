package li.cil.oc.common.block;

import li.cil.oc.common.init.Registries;
import li.cil.oc.common.tileentity.WaypointBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

public class Waypoint extends OcBlock implements EntityBlock {
    public static final EnumProperty<Direction> PITCH = EnumProperty.create("pitch", Direction.class,
            Direction.NORTH, Direction.UP, Direction.DOWN);
    public static final EnumProperty<Direction> YAW = EnumProperty.create("yaw", Direction.class,
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    public Waypoint(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(PITCH, Direction.NORTH).setValue(YAW, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(PITCH, YAW);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaypointBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> beType) {
        if (!level.isClientSide()) return null;
        return (lvl, pos, blockState, be) -> {
            if (be instanceof WaypointBlockEntity wbe) wbe.clientTick(lvl, blockState);
        };
    }
}
